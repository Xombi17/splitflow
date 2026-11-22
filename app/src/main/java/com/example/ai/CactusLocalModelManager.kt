package com.example.ai

import android.content.Context
import android.os.Build
import android.util.Log
import com.cactus.CactusCompletionParams
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusTranscriptionParams
import com.cactus.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val LOG_TAG = "CactusLocalModels"

enum class LocalModelPhase {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    UNAVAILABLE
}

enum class ActiveEngineKind {
    NONE,
    CACTUS_PRIMARY,
    LOCAL_FALLBACK,
    TEXT_PARSER
}

data class LocalAiEngineState(
    val whistlePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val whistleActiveSlug: String? = null,
    val whistleNote: String = "Tap Install to download Cactus Whistle for offline neural STT",
    val whistleError: String? = null,
    val lastVoiceEngineLabel: String? = null,
    val lastVoiceEngineKind: ActiveEngineKind = ActiveEngineKind.NONE,
    val lastVoiceDetail: String? = null,

    val miragePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val mirageActiveSlug: String? = null,
    val mirageNote: String = "Tap Install to download Cactus Mirage for local neural OCR",
    val mirageError: String? = null,
    val lastOcrEngineLabel: String? = null,
    val lastOcrEngineKind: ActiveEngineKind = ActiveEngineKind.NONE,
    val lastOcrDetail: String? = null
) {
    val isWhistleReady: Boolean get() = whistlePhase == LocalModelPhase.READY
    val isMirageReady: Boolean get() = miragePhase == LocalModelPhase.READY
}

/**
 * Singleton coordinator for on-device Cactus Compute inference engines:
 * - Voice STT via [CactusSTT] (prefers Whistle, then smallest available Cactus voice model such as whisper-tiny)
 * - Vision/OCR via [CactusLM] (prefers Mirage, then smallest vision-capable Cactus model)
 *
 * Surfaces exact download, ABI, and initialization errors in [state] and records which engine
 * actually ran on every voice transcription and receipt OCR pass.
 */
object CactusLocalModelManager {

    private val defaultSttCandidates = listOf("whistle", "whisper-tiny", "whisper-base")
    private val defaultVisionCandidates = listOf("mirage", "lfm2-vl-450m")

    private val sttLock = Mutex()
    private val visionLock = Mutex()

    @Volatile
    private var contextInitialized = false

    @Volatile
    private var contextInitError: String? = null

    private var sttInstance: CactusSTT? = null
    private var visionInstance: CactusLM? = null

    private val _state = MutableStateFlow(LocalAiEngineState())
    val state: StateFlow<LocalAiEngineState> = _state.asStateFlow()

    fun initContext(context: Context) {
        if (contextInitialized) return
        runCatching {
            CactusContextInitializer.initialize(context.applicationContext)
            contextInitialized = true
            contextInitError = null
        }.onFailure { err ->
            val msg = "${err.javaClass.simpleName}: ${err.message ?: "Cactus context init failed"}"
            contextInitError = msg
            Log.w(LOG_TAG, "Cactus context initialization failed: $msg", err)
        }
    }

    fun recordVoiceEngineRun(label: String, kind: ActiveEngineKind, detail: String? = null) {
        _state.update {
            it.copy(
                lastVoiceEngineLabel = label,
                lastVoiceEngineKind = kind,
                lastVoiceDetail = detail
            )
        }
    }

    fun recordOcrEngineRun(label: String, kind: ActiveEngineKind, detail: String? = null) {
        _state.update {
            it.copy(
                lastOcrEngineLabel = label,
                lastOcrEngineKind = kind,
                lastOcrDetail = detail
            )
        }
    }

    suspend fun prepareWhistle(context: Context): Boolean = withContext(Dispatchers.IO) {
        sttLock.withLock {
            val current = sttInstance
            if (_state.value.isWhistleReady && current != null && runCatching { current.isReady() }.getOrDefault(false)) {
                return@withLock true
            }

            initContext(context)
            contextInitError?.let { initErr ->
                _state.update {
                    it.copy(
                        whistlePhase = LocalModelPhase.UNAVAILABLE,
                        whistleNote = "Cactus runtime init failed — falling back to Android SpeechRecognizer",
                        whistleError = initErr
                    )
                }
                return@withLock false
            }

            _state.update {
                it.copy(
                    whistlePhase = LocalModelPhase.DOWNLOADING,
                    whistleNote = "Checking Cactus voice model catalog...",
                    whistleError = null
                )
            }

            val errors = mutableListOf<String>()
            val candidateSlugs = linkedSetOf<String>()

            // Discover live voice slugs supported by this Cactus SDK build first
            val catalogModels = runCatching {
                CactusSTT().getVoiceModels()
            }.onFailure { err ->
                errors.add("Catalog lookup failed (${err.javaClass.simpleName}: ${err.message ?: "offline"})")
            }.getOrDefault(emptyList())

            if (catalogModels.isNotEmpty()) {
                catalogModels
                    .sortedWith(
                        compareBy<com.cactus.VoiceModel>(
                            { !it.slug.lowercase(Locale.US).contains("whistle") },
                            { !it.slug.lowercase(Locale.US).contains("whisper-tiny") },
                            { !it.slug.lowercase(Locale.US).contains("moonshine") },
                            { it.size_mb }
                        )
                    )
                    .forEach { candidateSlugs.add(it.slug) }
            }
            candidateSlugs.addAll(defaultSttCandidates)

            for (slug in candidateSlugs.take(4)) {
                try {
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.DOWNLOADING,
                            whistleNote = "Downloading & initializing Cactus '$slug'..."
                        )
                    }
                    val engine = CactusSTT()
                    val alreadyLocal = runCatching { engine.isModelDownloaded(slug) }.getOrDefault(false)
                    if (!alreadyLocal) {
                        engine.downloadModel(slug)
                    }
                    val downloadedNow = runCatching { engine.isModelDownloaded(slug) }.getOrDefault(true)
                    if (!downloadedNow) {
                        errors.add("'$slug': download did not persist weights")
                        continue
                    }

                    engine.initializeModel(CactusInitParams(model = slug))
                    val ready = runCatching { engine.isReady() }.getOrDefault(false)
                    if (!ready) {
                        errors.add("'$slug': initializeModel finished but engine.isReady() is false")
                        continue
                    }

                    sttInstance = engine
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.READY,
                            whistleActiveSlug = slug,
                            whistleNote = "Cactus '$slug' loaded on-device",
                            whistleError = null
                        )
                    }
                    return@withLock true
                } catch (err: Throwable) {
                    val detail = "'$slug': ${err.javaClass.simpleName}: ${err.message ?: "unknown error"}"
                    errors.add(detail)
                    Log.w(LOG_TAG, "Cactus STT candidate failed: $detail", err)
                    if (err is UnsatisfiedLinkError || err is NoClassDefFoundError) {
                        errors.add("Device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                        break
                    }
                }
            }

            val summaryError = errors.joinToString(" | ").ifBlank {
                "Cactus STT could not download or initialize on this device."
            }
            _state.update {
                it.copy(
                    whistlePhase = LocalModelPhase.UNAVAILABLE,
                    whistleActiveSlug = null,
                    whistleNote = "Cactus STT unavailable — using Android SpeechRecognizer fallback",
                    whistleError = summaryError
                )
            }
            false
        }
    }

    suspend fun transcribeWavWithWhistle(context: Context, wavFile: File): String? =
        withContext(Dispatchers.IO) {
            if (!wavFile.exists() || wavFile.length() <= 44L) {
                _state.update { it.copy(whistleError = "Recorded audio clip was empty.") }
                return@withContext null
            }
            if (!_state.value.isWhistleReady) {
                val ok = prepareWhistle(context)
                if (!ok) return@withContext null
            }
            sttLock.withLock {
                val engine = sttInstance
                val activeSlug = _state.value.whistleActiveSlug ?: "whistle"
                if (engine == null || !runCatching { engine.isReady() }.getOrDefault(false)) {
                    val errMsg = "Cactus STT engine '$activeSlug' is not loaded (isReady=false)."
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.UNAVAILABLE,
                            whistleError = errMsg
                        )
                    }
                    return@withLock null
                }
                val startMs = System.currentTimeMillis()
                try {
                    val output = engine.transcribe(
                        filePath = wavFile.absolutePath,
                        params = CactusTranscriptionParams()
                    )
                    val text = output?.text?.trim().orEmpty()
                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    if (text.isNotEmpty()) {
                        recordVoiceEngineRun(
                            label = "Cactus Whistle ($activeSlug)",
                            kind = ActiveEngineKind.CACTUS_PRIMARY,
                            detail = "Transcribed on-device in ${elapsedMs}ms"
                        )
                        _state.update { it.copy(whistleError = null) }
                        text
                    } else {
                        val errMsg = "Cactus '$activeSlug' returned an empty transcript for this clip."
                        _state.update { it.copy(whistleError = errMsg) }
                        recordVoiceEngineRun(
                            label = "Cactus Whistle ($activeSlug)",
                            kind = ActiveEngineKind.CACTUS_PRIMARY,
                            detail = errMsg
                        )
                        null
                    }
                } catch (err: Throwable) {
                    val errMsg = "Cactus '$activeSlug' transcribe failed: ${err.javaClass.simpleName}: ${err.message ?: "unknown error"}"
                    Log.w(LOG_TAG, errMsg, err)
                    _state.update { it.copy(whistleError = errMsg) }
                    null
                }
            }
        }

    suspend fun prepareMirage(context: Context): Boolean = withContext(Dispatchers.IO) {
        visionLock.withLock {
            val current = visionInstance
            if (_state.value.isMirageReady && current != null && runCatching { current.isLoaded() }.getOrDefault(false)) {
                return@withLock true
            }

            initContext(context)
            contextInitError?.let { initErr ->
                _state.update {
                    it.copy(
                        miragePhase = LocalModelPhase.UNAVAILABLE,
                        mirageNote = "Cactus runtime init failed — falling back to ML Kit OCR",
                        mirageError = initErr
                    )
                }
                return@withLock false
            }

            _state.update {
                it.copy(
                    miragePhase = LocalModelPhase.DOWNLOADING,
                    mirageNote = "Checking Cactus vision model catalog...",
                    mirageError = null
                )
            }

            val errors = mutableListOf<String>()
            val candidateSlugs = linkedSetOf<String>()

            val catalogModels = runCatching {
                CactusLM().getModels()
            }.onFailure { err ->
                errors.add("Catalog lookup failed (${err.javaClass.simpleName}: ${err.message ?: "offline"})")
            }.getOrDefault(emptyList())

            catalogModels
                .filter { it.supports_vision || it.slug.lowercase(Locale.US).contains("mirage") || it.slug.lowercase(Locale.US).contains("vl") }
                .sortedWith(
                    compareBy<com.cactus.CactusModel>(
                        { !it.slug.lowercase(Locale.US).contains("mirage") },
                        { it.size_mb }
                    )
                )
                .forEach { candidateSlugs.add(it.slug) }

            candidateSlugs.addAll(defaultVisionCandidates)

            for (slug in candidateSlugs.take(3)) {
                try {
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.DOWNLOADING,
                            mirageNote = "Downloading & loading Cactus '$slug'..."
                        )
                    }
                    val engine = CactusLM()
                    engine.downloadModel(slug)
                    engine.initializeModel(CactusInitParams(model = slug, contextSize = 2048))
                    val loaded = runCatching { engine.isLoaded() }.getOrDefault(false)
                    if (!loaded) {
                        errors.add("'$slug': initializeModel finished but engine.isLoaded() is false")
                        continue
                    }

                    visionInstance = engine
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.READY,
                            mirageActiveSlug = slug,
                            mirageNote = "Cactus '$slug' vision OCR active on-device",
                            mirageError = null
                        )
                    }
                    return@withLock true
                } catch (err: Throwable) {
                    val detail = "'$slug': ${err.javaClass.simpleName}: ${err.message ?: "unknown error"}"
                    errors.add(detail)
                    Log.w(LOG_TAG, "Cactus Vision candidate failed: $detail", err)
                    if (err is UnsatisfiedLinkError || err is NoClassDefFoundError) {
                        errors.add("Device ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                        break
                    }
                }
            }

            val summaryError = errors.joinToString(" | ").ifBlank {
                "Cactus Mirage vision model could not download or initialize."
            }
            _state.update {
                it.copy(
                    miragePhase = LocalModelPhase.UNAVAILABLE,
                    mirageActiveSlug = null,
                    mirageNote = "Cactus Mirage unavailable — using ML Kit On-Device OCR fallback",
                    mirageError = summaryError
                )
            }
            false
        }
    }

    suspend fun extractReceiptTextWithMirage(context: Context, imageFile: File): String? =
        withContext(Dispatchers.IO) {
            if (!imageFile.exists() || !_state.value.isMirageReady) return@withContext null
            visionLock.withLock {
                val engine = visionInstance
                val activeSlug = _state.value.mirageActiveSlug ?: "mirage"
                if (engine == null || !runCatching { engine.isLoaded() }.getOrDefault(false)) {
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.UNAVAILABLE,
                            mirageError = "Cactus '$activeSlug' is not loaded (isLoaded=false)."
                        )
                    }
                    return@withLock null
                }
                val startMs = System.currentTimeMillis()
                try {
                    val messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = "Transcribe all text on this receipt line by line, keeping item names and prices on the same line.",
                            images = listOf(imageFile.absolutePath)
                        )
                    )
                    val completion = engine.generateCompletion(
                        messages = messages,
                        params = CactusCompletionParams(temperature = 0.1, maxTokens = 768)
                    )
                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    if (completion?.success == true && !completion.response.isNullOrBlank()) {
                        recordOcrEngineRun(
                            label = "Cactus Mirage ($activeSlug)",
                            kind = ActiveEngineKind.CACTUS_PRIMARY,
                            detail = "OCR completed on-device in ${elapsedMs}ms"
                        )
                        _state.update { it.copy(mirageError = null) }
                        completion.response?.trim()
                    } else {
                        val errMsg = "Cactus '$activeSlug' returned no receipt text (success=${completion?.success})."
                        _state.update { it.copy(mirageError = errMsg) }
                        null
                    }
                } catch (err: Throwable) {
                    val errMsg = "Cactus '$activeSlug' OCR failed: ${err.javaClass.simpleName}: ${err.message ?: "unknown error"}"
                    Log.w(LOG_TAG, errMsg, err)
                    _state.update { it.copy(mirageError = errMsg) }
                    null
                }
            }
        }
}
