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
import com.cactus.TranscriptionMode
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
import kotlin.math.abs

private const val LOG_TAG = "CactusLocalModels"
private const val WHISPER_EN_PROMPT = "<|startoftranscript|><|en|><|transcribe|><|notimestamps|>"

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

data class CatalogModelOption(
    val slug: String,
    val displayName: String,
    val sizeMb: Int,
    val isDownloaded: Boolean
)

data class LocalAiEngineState(
    val catalogLoaded: Boolean = false,
    val availableVoiceModels: List<CatalogModelOption> = emptyList(),
    val availableVisionModels: List<CatalogModelOption> = emptyList(),

    val whistlePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val whistleActiveSlug: String? = null,
    val whistleNote: String = "Download Cactus Whistle for offline voice recognition",
    val whistleError: String? = null,
    val lastVoiceEngineLabel: String? = null,
    val lastVoiceEngineKind: ActiveEngineKind = ActiveEngineKind.NONE,
    val lastVoiceDetail: String? = null,

    val miragePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val mirageActiveSlug: String? = null,
    val mirageNote: String = "Download Cactus Mirage for offline receipt scanning",
    val mirageError: String? = null,
    val lastOcrEngineLabel: String? = null,
    val lastOcrEngineKind: ActiveEngineKind = ActiveEngineKind.NONE,
    val lastOcrDetail: String? = null
) {
    val isWhistleReady: Boolean get() = whistlePhase == LocalModelPhase.READY
    val isMirageReady: Boolean get() = miragePhase == LocalModelPhase.READY
    val isAnyDownloading: Boolean
        get() = whistlePhase == LocalModelPhase.DOWNLOADING || miragePhase == LocalModelPhase.DOWNLOADING
    val areBothModelsReady: Boolean get() = isWhistleReady && isMirageReady
}

/**
 * Singleton coordinator for on-device Cactus Compute inference engines:
 * - Voice STT via [CactusSTT] (Cactus Whistle)
 * - Vision/OCR via [CactusLM] (Cactus Mirage)
 *
 * Supports onboarding pre-download, automatic startup hydration of already-downloaded weights,
 * multi-pass WAV + PCM transcription, and transparent runtime diagnostics.
 */
object CactusLocalModelManager {

    // Verified STT candidates pinned to fast, lightweight models (whisper-tiny primary for offline voice)
    private val defaultSttCandidates = listOf("whisper-tiny", "whisper-base", "whisper-small")
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

    /**
     * Fetches the live Cactus STT & Vision model catalogs and automatically loads any
     * model weights that were already downloaded on this device (e.g. during Onboarding).
     */
    suspend fun refreshCatalogAndHydrate(context: Context) = withContext(Dispatchers.IO) {
        initContext(context)
        if (contextInitError != null) return@withContext

        val voiceCatalog = runCatching {
            val stt = CactusSTT()
            stt.getVoiceModels().map { m ->
                val downloaded = runCatching { stt.isModelDownloaded(m.slug) }.getOrDefault(m.isDownloaded)
                CatalogModelOption(
                    slug = m.slug,
                    displayName = formatModelLabel(m.slug),
                    sizeMb = m.size_mb,
                    isDownloaded = downloaded
                )
            }.sortedWith(voiceModelComparator())
        }.getOrElse {
            // Offline catalog fallback: check verified candidates directly on-device
            defaultSttCandidates.map { slug ->
                val downloaded = runCatching { CactusSTT().isModelDownloaded(slug) }.getOrDefault(false)
                CatalogModelOption(
                    slug = slug,
                    displayName = formatModelLabel(slug),
                    sizeMb = if (slug == "whisper-tiny") 39 else 142,
                    isDownloaded = downloaded
                )
            }
        }

        val visionCatalog = runCatching {
            val lm = CactusLM()
            lm.getModels()
                .filter {
                    it.supports_vision ||
                        it.slug.lowercase(Locale.US).contains("mirage") ||
                        it.slug.lowercase(Locale.US).contains("vl")
                }
                .map { m ->
                    CatalogModelOption(
                        slug = m.slug,
                        displayName = m.name.ifBlank { formatModelLabel(m.slug) },
                        sizeMb = m.size_mb,
                        isDownloaded = m.isDownloaded
                    )
                }
                .sortedWith(visionModelComparator())
        }.getOrDefault(emptyList())

        _state.update { current ->
            current.copy(
                catalogLoaded = true,
                availableVoiceModels = voiceCatalog.take(1),
                availableVisionModels = visionCatalog.take(1),
                whistleNote = if (current.isWhistleReady) current.whistleNote else {
                    "Ready to download Cactus Whistle offline voice pack"
                },
                mirageNote = if (current.isMirageReady) current.mirageNote else {
                    "Ready to download Cactus Mirage offline receipt OCR"
                }
            )
        }

        // Auto-initialize any already-downloaded voice model into memory without re-downloading
        if (!_state.value.isWhistleReady) {
            val alreadyLocalVoice = voiceCatalog.firstOrNull { it.isDownloaded }?.slug
                ?: defaultSttCandidates.firstOrNull { slug ->
                    runCatching { CactusSTT().isModelDownloaded(slug) }.getOrDefault(false)
                }
            if (alreadyLocalVoice != null) {
                prepareWhistle(context, preferredSlug = alreadyLocalVoice)
            }
        }

        // Auto-initialize any already-downloaded vision model into memory
        if (!_state.value.isMirageReady) {
            val alreadyLocalVision = visionCatalog.firstOrNull { it.isDownloaded }?.slug
            if (alreadyLocalVision != null) {
                prepareMirage(context, preferredSlug = alreadyLocalVision)
            }
        }
    }

    /**
     * Downloads and initializes BOTH the primary Voice model (Whistle / Whisper) and
     * the Vision OCR model (Mirage / Cactus VLM) — used by the Onboarding setup step.
     */
    suspend fun prepareAllModels(
        context: Context,
        preferredVoiceSlug: String? = null,
        preferredVisionSlug: String? = null
    ): Pair<Boolean, Boolean> {
        val voiceOk = prepareWhistle(context, preferredSlug = preferredVoiceSlug)
        val visionOk = prepareMirage(context, preferredSlug = preferredVisionSlug)
        return voiceOk to visionOk
    }

    suspend fun prepareWhistle(
        context: Context,
        preferredSlug: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        sttLock.withLock {
            val current = sttInstance
            val currentSlug = _state.value.whistleActiveSlug
            if (_state.value.isWhistleReady &&
                current != null &&
                (preferredSlug == null || preferredSlug == currentSlug) &&
                runCatching { current.isReady() }.getOrDefault(false)
            ) {
                return@withLock true
            }

            initContext(context)
            contextInitError?.let { initErr ->
                _state.update {
                    it.copy(
                        whistlePhase = LocalModelPhase.UNAVAILABLE,
                        whistleNote = "Cactus runtime init failed — using Android SpeechRecognizer",
                        whistleError = initErr
                    )
                }
                return@withLock false
            }

            _state.update {
                it.copy(
                    whistlePhase = LocalModelPhase.DOWNLOADING,
                    whistleNote = "Preparing Cactus Whistle offline voice...",
                    whistleError = null
                )
            }

            val errors = mutableListOf<String>()
            val candidateSlugs = linkedSetOf<String>()
            preferredSlug?.takeIf { it.isNotBlank() }?.let { candidateSlugs.add(it) }

            val catalogModels = runCatching {
                CactusSTT().getVoiceModels()
            }.onFailure { err ->
                errors.add("Catalog lookup failed (${err.javaClass.simpleName}: ${err.message ?: "offline"})")
            }.getOrDefault(emptyList())

            if (catalogModels.isNotEmpty()) {
                val options = catalogModels.map { m ->
                    CatalogModelOption(
                        slug = m.slug,
                        displayName = formatModelLabel(m.slug),
                        sizeMb = m.size_mb,
                        isDownloaded = m.isDownloaded
                    )
                }.sortedWith(voiceModelComparator())
                _state.update { it.copy(catalogLoaded = true, availableVoiceModels = options.take(1)) }
                options.forEach { candidateSlugs.add(it.slug) }
            }
            candidateSlugs.addAll(defaultSttCandidates)

            for (slug in candidateSlugs.take(4)) {
                try {
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.DOWNLOADING,
                            whistleNote = "Downloading Cactus Whistle offline pack..."
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
                        errors.add("'$slug': initialized but engine.isReady() is false")
                        continue
                    }

                    sttInstance = engine
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.READY,
                            whistleActiveSlug = slug,
                            whistleNote = "Cactus Whistle loaded on-device for offline voice",
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

            val wavBytes = runCatching { wavFile.readBytes() }.getOrNull() ?: return@withContext null
            val rawPcm = if (wavBytes.size > 44) wavBytes.copyOfRange(44, wavBytes.size) else ByteArray(0)
            val audioStats = inspectPcmStats(rawPcm)

            sttLock.withLock {
                val engine = sttInstance
                val activeSlug = _state.value.whistleActiveSlug ?: "whisper-base"
                if (engine == null || !runCatching { engine.isReady() }.getOrDefault(false)) {
                    val errMsg = "Cactus STT '$activeSlug' is not loaded (isReady=false)."
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.UNAVAILABLE,
                            whistleError = errMsg
                        )
                    }
                    return@withLock null
                }

                val startMs = System.currentTimeMillis()
                val isWhisperModel = activeSlug.lowercase(Locale.US).contains("whisper")
                val primaryPrompt = if (isWhisperModel) WHISPER_EN_PROMPT else ""

                try {
                    val streamed = StringBuilder()
                    val result = engine.transcribe(
                        filePath = wavFile.absolutePath,
                        prompt = primaryPrompt,
                        params = CactusTranscriptionParams(maxTokens = 128),
                        onToken = { token, _ -> streamed.append(token) },
                        mode = TranscriptionMode.LOCAL
                    )
                    val candidate = result?.text?.takeIf { it.isNotBlank() }
                        ?: streamed.toString().takeIf { it.isNotBlank() }
                    val text = cleanAsrTranscript(candidate)

                    val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    if (!text.isNullOrBlank()) {
                        recordVoiceEngineRun(
                            label = "Cactus On-Device ($activeSlug)",
                            kind = ActiveEngineKind.CACTUS_PRIMARY,
                            detail = "Transcribed offline in ${elapsedMs}ms"
                        )
                        _state.update { it.copy(whistleError = null) }
                        text
                    } else {
                        val errMsg = "Couldn't catch clear words (${audioStats.durationMs}ms clip, level ${audioStats.peakPercent}%). Try speaking closer to the mic or type below."
                        _state.update { it.copy(whistleError = errMsg) }
                        recordVoiceEngineRun(
                            label = "Cactus On-Device",
                            kind = ActiveEngineKind.CACTUS_PRIMARY,
                            detail = "No speech decoded (${audioStats.durationMs}ms)"
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

    suspend fun prepareMirage(
        context: Context,
        preferredSlug: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        visionLock.withLock {
            val current = visionInstance
            val currentSlug = _state.value.mirageActiveSlug
            if (_state.value.isMirageReady &&
                current != null &&
                (preferredSlug == null || preferredSlug == currentSlug) &&
                runCatching { current.isLoaded() }.getOrDefault(false)
            ) {
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
                    mirageNote = "Checking Cactus Mirage / Vision catalog...",
                    mirageError = null
                )
            }

            val errors = mutableListOf<String>()
            val candidateSlugs = linkedSetOf<String>()
            preferredSlug?.takeIf { it.isNotBlank() }?.let { candidateSlugs.add(it) }

            val catalogModels = runCatching {
                CactusLM().getModels()
            }.onFailure { err ->
                errors.add("Catalog lookup failed (${err.javaClass.simpleName}: ${err.message ?: "offline"})")
            }.getOrDefault(emptyList())

            val visionOptions = catalogModels
                .filter {
                    it.supports_vision ||
                        it.slug.lowercase(Locale.US).contains("mirage") ||
                        it.slug.lowercase(Locale.US).contains("vl")
                }
                .map { m ->
                    CatalogModelOption(
                        slug = m.slug,
                        displayName = m.name.ifBlank { formatModelLabel(m.slug) },
                        sizeMb = m.size_mb,
                        isDownloaded = m.isDownloaded
                    )
                }
                .sortedWith(visionModelComparator())

            if (visionOptions.isNotEmpty()) {
                _state.update { it.copy(catalogLoaded = true, availableVisionModels = visionOptions.take(1)) }
                visionOptions.forEach { candidateSlugs.add(it.slug) }
            }
            candidateSlugs.addAll(defaultVisionCandidates)

            for (slug in candidateSlugs.take(3)) {
                try {
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.DOWNLOADING,
                            mirageNote = "Downloading Cactus Mirage receipt OCR..."
                        )
                    }
                    val engine = CactusLM()
                    engine.downloadModel(slug)
                    engine.initializeModel(CactusInitParams(model = slug, contextSize = 2048))
                    val loaded = runCatching { engine.isLoaded() }.getOrDefault(false)
                    if (!loaded) {
                        errors.add("'$slug': initialized but engine.isLoaded() is false")
                        continue
                    }

                    visionInstance = engine
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.READY,
                            mirageActiveSlug = slug,
                            mirageNote = "Cactus Mirage vision OCR active on-device",
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
                "Cactus Mirage / Vision model could not download or initialize."
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
                            label = "Cactus Vision ($activeSlug)",
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

    private data class PcmStats(val durationMs: Long, val peakPercent: Int)

    private fun inspectPcmStats(pcmBytes: ByteArray): PcmStats {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount <= 0) return PcmStats(0L, 0)
        var peakAbs = 0
        for (i in 0 until sampleCount) {
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt() and 0xFF
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val v = abs(sample)
            if (v > peakAbs) peakAbs = v
        }
        val durationMs = (sampleCount * 1000L) / 16_000L
        val peakPct = ((peakAbs / 32_767.0) * 100.0).toInt().coerceIn(0, 100)
        return PcmStats(durationMs, peakPct)
    }

    private val whisperHallucinations = setOf(
        "thank you",
        "thank you.",
        "thank you!",
        "thanks",
        "thanks.",
        "thanks for watching",
        "thanks for watching.",
        "thanks for listening",
        "you",
        "you.",
        "bye",
        "bye.",
        "mbc",
        "subtitles by",
        "transcribed by"
    )

    private fun cleanAsrTranscript(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(Regex("<\\|[^|>]+\\|>"), " ")
            .replace(Regex("\\[(blank_audio|silence|inaudible|music|applause|noise|laughter|coughing)]", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\((blank audio|silence|inaudible|music|applause)\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (!cleaned.any { ch -> ch.isLetterOrDigit() }) return null
        val lower = cleaned.lowercase(Locale.US).trimEnd('.', '!', '?')
        if (lower in whisperHallucinations || lower.startsWith("subtitles by") || lower.startsWith("thanks for watching")) {
            return null
        }
        return cleaned
    }

    private fun voiceModelComparator(): Comparator<CatalogModelOption> = compareBy(
        { !it.slug.lowercase(Locale.US).contains("whistle") },
        { !it.slug.lowercase(Locale.US).contains("whisper-base") },
        { !it.slug.lowercase(Locale.US).contains("whisper-small") },
        { !it.slug.lowercase(Locale.US).contains("whisper-tiny") },
        { it.sizeMb }
    )

    private fun visionModelComparator(): Comparator<CatalogModelOption> = compareBy(
        { !it.slug.lowercase(Locale.US).contains("mirage") },
        { !it.slug.lowercase(Locale.US).contains("lfm2-vl") },
        { it.sizeMb }
    )

    private fun formatModelLabel(slug: String): String =
        slug.split("-", "_").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
}
