package com.example.ai

import android.content.Context
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

private const val LOG_TAG = "CactusLocalModels"

enum class LocalModelPhase {
    NOT_DOWNLOADED,
    DOWNLOADING,
    READY,
    UNAVAILABLE
}

data class LocalAiEngineState(
    val whistlePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val whistleActiveSlug: String? = null,
    val whistleNote: String = "Tap to install Cactus Whistle (~17 MB) for offline neural STT",
    val miragePhase: LocalModelPhase = LocalModelPhase.NOT_DOWNLOADED,
    val mirageActiveSlug: String? = null,
    val mirageNote: String = "Tap to install Cactus Mirage (~22 MB) for local neural OCR"
) {
    val isWhistleReady: Boolean get() = whistlePhase == LocalModelPhase.READY
    val isMirageReady: Boolean get() = miragePhase == LocalModelPhase.READY
}

/**
 * Singleton coordinator for on-device Cactus Compute inference engines:
 * - Whistle (tiny speech-to-text) via [CactusSTT]
 * - Mirage (tiny vision/OCR) via [CactusLM]
 *
 * Gracefully catches native ABI/link errors (such as x86_64 emulators) so callers
 * can seamlessly fall back to Android on-device SpeechRecognizer and ML Kit OCR.
 */
object CactusLocalModelManager {

    private val sttCandidates = listOf("whistle", "whisper-tiny")
    private val visionCandidates = listOf("mirage", "lfm2-vl-450m")

    private val sttLock = Mutex()
    private val visionLock = Mutex()

    @Volatile
    private var contextInitialized = false

    private var sttInstance: CactusSTT? = null
    private var visionInstance: CactusLM? = null

    private val _state = MutableStateFlow(LocalAiEngineState())
    val state: StateFlow<LocalAiEngineState> = _state.asStateFlow()

    fun initContext(context: Context) {
        if (contextInitialized) return
        runCatching {
            CactusContextInitializer.initialize(context.applicationContext)
            contextInitialized = true
        }.onFailure { err ->
            Log.w(LOG_TAG, "Cactus context initialization unavailable on this runtime", err)
        }
    }

    suspend fun prepareWhistle(context: Context): Boolean = withContext(Dispatchers.IO) {
        sttLock.withLock {
            if (_state.value.isWhistleReady && sttInstance != null) return@withLock true
            initContext(context)
            _state.update {
                it.copy(
                    whistlePhase = LocalModelPhase.DOWNLOADING,
                    whistleNote = "Downloading & loading Cactus Whistle..."
                )
            }

            var lastError: Throwable? = null
            for (slug in sttCandidates) {
                try {
                    val engine = CactusSTT()
                    engine.downloadModel(slug)
                    engine.initializeModel(CactusInitParams(model = slug))
                    sttInstance = engine
                    _state.update {
                        it.copy(
                            whistlePhase = LocalModelPhase.READY,
                            whistleActiveSlug = slug,
                            whistleNote = "Cactus $slug active on-device"
                        )
                    }
                    return@withLock true
                } catch (err: Throwable) {
                    lastError = err
                    Log.w(LOG_TAG, "Cactus STT slug '$slug' failed: ${err.message}")
                    if (err is UnsatisfiedLinkError || err is NoClassDefFoundError) {
                        break
                    }
                }
            }

            val reason = when (lastError) {
                is UnsatisfiedLinkError -> "Native ARM64 runtime unavailable — using local device speech engine"
                else -> "Using local device speech engine (Whistle download offline)"
            }
            _state.update {
                it.copy(
                    whistlePhase = LocalModelPhase.UNAVAILABLE,
                    whistleNote = reason
                )
            }
            false
        }
    }

    suspend fun transcribeWavWithWhistle(context: Context, wavFile: File): String? =
        withContext(Dispatchers.IO) {
            if (!wavFile.exists() || wavFile.length() <= 44L) return@withContext null
            if (!_state.value.isWhistleReady) {
                val ok = prepareWhistle(context)
                if (!ok) return@withContext null
            }
            sttLock.withLock {
                val engine = sttInstance ?: return@withLock null
                try {
                    val output = engine.transcribe(
                        filePath = wavFile.absolutePath,
                        params = CactusTranscriptionParams()
                    )
                    output?.text?.trim()?.takeIf { it.isNotEmpty() }
                } catch (err: Throwable) {
                    Log.w(LOG_TAG, "Whistle transcription error", err)
                    null
                }
            }
        }

    suspend fun prepareMirage(context: Context): Boolean = withContext(Dispatchers.IO) {
        visionLock.withLock {
            if (_state.value.isMirageReady && visionInstance != null) return@withLock true
            initContext(context)
            _state.update {
                it.copy(
                    miragePhase = LocalModelPhase.DOWNLOADING,
                    mirageNote = "Downloading & loading Cactus Mirage..."
                )
            }

            var lastError: Throwable? = null
            for (slug in visionCandidates) {
                try {
                    val engine = CactusLM()
                    engine.downloadModel(slug)
                    engine.initializeModel(CactusInitParams(model = slug, contextSize = 2048))
                    visionInstance = engine
                    _state.update {
                        it.copy(
                            miragePhase = LocalModelPhase.READY,
                            mirageActiveSlug = slug,
                            mirageNote = "Cactus $slug OCR active on-device"
                        )
                    }
                    return@withLock true
                } catch (err: Throwable) {
                    lastError = err
                    Log.w(LOG_TAG, "Cactus Vision slug '$slug' failed: ${err.message}")
                    if (err is UnsatisfiedLinkError || err is NoClassDefFoundError) {
                        break
                    }
                }
            }

            val reason = when (lastError) {
                is UnsatisfiedLinkError -> "Native ARM64 runtime unavailable — using on-device ML Kit OCR"
                else -> "Using instant on-device ML Kit OCR (Mirage offline)"
            }
            _state.update {
                it.copy(
                    miragePhase = LocalModelPhase.UNAVAILABLE,
                    mirageNote = reason
                )
            }
            false
        }
    }

    suspend fun extractReceiptTextWithMirage(context: Context, imageFile: File): String? =
        withContext(Dispatchers.IO) {
            if (!imageFile.exists() || !_state.value.isMirageReady) return@withContext null
            visionLock.withLock {
                val engine = visionInstance ?: return@withContext null
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
                    if (completion?.success == true) {
                        completion.response?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                } catch (err: Throwable) {
                    Log.w(LOG_TAG, "Mirage vision OCR failed", err)
                    null
                }
            }
        }
}
