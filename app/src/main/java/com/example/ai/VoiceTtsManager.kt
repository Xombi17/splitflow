package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

private const val TTS_LOG_TAG = "VoiceTtsManager"

data class VoiceTtsState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVolumeMuted: Boolean = false,
    val activeLocaleTag: String? = null,
    val errorMessage: String? = null
)

/**
 * Reliable Android Text-to-Speech controller for SplitFlow's Voice Assistant.
 * - Requires `<action android:name="android.intent.action.TTS_SERVICE" />` in `<queries>`.
 * - Configures assistant speech AudioAttributes and negotiates English/system locales gracefully.
 * - Buffers any utterance requested before initialization finishes and speaks it immediately once ready.
 * - Normalizes currency symbols (e.g. "₹500" -> "500 rupees") for natural spoken phrasing.
 */
class VoiceTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(VoiceTtsState())
    val state: StateFlow<VoiceTtsState> = _state.asStateFlow()

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var pendingUtterance: String? = null

    @Volatile
    private var released = false

    init {
        val instance = TextToSpeech(appContext) { status ->
            if (released) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                configureEngine()
            } else {
                Log.w(TTS_LOG_TAG, "TextToSpeech init failed with status=$status")
                _state.update {
                    it.copy(
                        isReady = false,
                        errorMessage = "Device speech engine failed to initialize (code $status). Check Android Text-to-Speech settings."
                    )
                }
            }
        }
        engine = instance
    }

    private fun configureEngine() {
        val tts = engine ?: return
        try {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setSpeechRate(1.02f)
            tts.setPitch(1.0f)

            val preferredLocales = listOf(
                Locale.getDefault(),
                Locale("en", "IN"),
                Locale.US,
                Locale.UK,
                Locale.ENGLISH
            )
            var chosenLocale: Locale? = null
            for (loc in preferredLocales) {
                val res = tts.setLanguage(loc)
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    chosenLocale = loc
                    break
                }
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.update { it.copy(isSpeaking = true, errorMessage = null) }
                }

                override fun onDone(utteranceId: String?) {
                    _state.update { it.copy(isSpeaking = false) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _state.update {
                        it.copy(
                            isSpeaking = false,
                            errorMessage = "Could not play voice reply on this device."
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _state.update {
                        it.copy(
                            isSpeaking = false,
                            errorMessage = "Voice reply error (code $errorCode)."
                        )
                    }
                }
            })

            _state.update {
                it.copy(
                    isReady = true,
                    activeLocaleTag = chosenLocale?.toLanguageTag() ?: Locale.getDefault().toLanguageTag(),
                    isVolumeMuted = isMediaMuted(),
                    errorMessage = if (chosenLocale == null) {
                        "No English TTS voice pack installed in Android Settings."
                    } else null
                )
            }

            pendingUtterance?.let { queued ->
                pendingUtterance = null
                speak(queued)
            }
        } catch (err: Throwable) {
            Log.w(TTS_LOG_TAG, "TTS configuration failed", err)
            _state.update {
                it.copy(
                    isReady = false,
                    errorMessage = err.message ?: "Text-to-Speech setup failed."
                )
            }
        }
    }

    fun speak(rawText: String) {
        if (released) return
        val spoken = normalizeForSpeech(rawText)
        if (spoken.isBlank()) return

        val muted = isMediaMuted()
        _state.update { it.copy(isVolumeMuted = muted) }

        val tts = engine
        if (tts == null || !_state.value.isReady) {
            pendingUtterance = spoken
            return
        }

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "splitflow_reply_${UUID.randomUUID()}"
        val status = runCatching {
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (status == TextToSpeech.ERROR) {
            _state.update {
                it.copy(
                    isSpeaking = false,
                    errorMessage = "Voice playback failed. Ensure Google Speech Services is enabled."
                )
            }
        }
    }

    fun stop() {
        pendingUtterance = null
        runCatching { engine?.stop() }
        _state.update { it.copy(isSpeaking = false) }
    }

    fun release() {
        released = true
        pendingUtterance = null
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        _state.update { it.copy(isReady = false, isSpeaking = false) }
    }

    private fun isMediaMuted(): Boolean {
        val am = audioManager ?: return false
        return runCatching {
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }.getOrDefault(false)
    }

    private fun normalizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("₹\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"), "$1 rupees")
            .replace(Regex("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"), "$1 dollars")
            .replace(Regex("€\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"), "$1 euros")
            .replace(Regex("£\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"), "$1 pounds")
            .replace("·", ", ")
            .replace("—", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
