package com.example.ai

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.LedgerMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

data class VoiceControllerState(
    val isListening: Boolean = false,
    val isWakeStandbyActive: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialTranscript: String = "",
    val audioLevel: Float = 0f,
    val ttsReady: Boolean = false,
    val ttsOnDeviceVoice: Boolean = false,
    val isMediaVolumeMuted: Boolean = false,
    val statusNotice: String? = null,
    val errorMessage: String? = null
)

/**
 * VyaparFlow-inspired multilingual Android voice & TTS controller for SplitFlow:
 * - Native Android SpeechRecognizer with live partial transcripts & N-best ranking
 * - 4 Indian locales: en-IN, hi-IN, mr-IN, ta-IN
 * - 4 TTS personas: Diya, Aarav, Riya, Kabir (preferring non-network on-device voices)
 * - Hands-free "Hey SplitFlow" wake-word standby loop with pause-while-speaking & re-arm
 */
class SplitFlowVoiceController(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(VoiceControllerState())
    val state: StateFlow<VoiceControllerState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isReleased = false

    private var currentLocale: SplitFlowLocale = SplitFlowLocale.EN_IN
    private var currentPersona: SplitFlowVoicePersona = SplitFlowVoicePersona.DIYA
    private var soundboxEnabled: Boolean = true
    private var wakeStandbyDesired: Boolean = false
    private var inWakeMode: Boolean = false

    private var activeMembers: List<LedgerMember> = emptyList()
    private var onCommandCaptured: ((String) -> Unit)? = null
    private var onWakeDetected: ((inlineCommand: String?) -> Unit)? = null
    private var onFallbackDialogRequested: ((Intent) -> Unit)? = null

    private var pendingUtterance: String? = null
    private var onSpeechFinishedCallback: (() -> Unit)? = null

    private val wakeRearmRunnable = Runnable {
        if (!isReleased && wakeStandbyDesired && !_state.value.isListening && !_state.value.isSpeaking) {
            startWakeListeningInternal()
        }
    }

    init {
        initTts()
    }

    fun updateSettings(settings: VoiceSettingsState) {
        val localeChanged = currentLocale != settings.locale
        val personaChanged = currentPersona != settings.persona
        currentLocale = settings.locale
        currentPersona = settings.persona
        soundboxEnabled = settings.soundboxEnabled
        if (localeChanged || personaChanged) {
            applyTtsVoiceConfiguration()
        }
    }

    fun updateKnownMembers(members: List<LedgerMember>) {
        activeMembers = members
    }

    fun setCallbacks(
        onCommand: ((String) -> Unit)? = null,
        onWake: ((String?) -> Unit)? = null,
        onFallbackDialog: ((Intent) -> Unit)? = null
    ) {
        if (onCommand != null) onCommandCaptured = onCommand
        if (onWake != null) onWakeDetected = onWake
        if (onFallbackDialog != null) onFallbackDialogRequested = onFallbackDialog
    }

    fun buildSystemSpeechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.bcp47Tag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocale.bcp47Tag)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        putExtra(RecognizerIntent.EXTRA_PROMPT, currentLocale.promptHint)
    }

    /**
     * Starts active command dictation (stops any wake standby or TTS playback first).
     */
    fun startCommandListening() {
        mainHandler.post {
            if (isReleased) return@post
            mainHandler.removeCallbacks(wakeRearmRunnable)
            stopSpeakingInternal()
            inWakeMode = false
            _state.update {
                it.copy(
                    isWakeStandbyActive = false,
                    partialTranscript = "",
                    errorMessage = null,
                    statusNotice = "Listening (${currentLocale.displayName})..."
                )
            }

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.update { it.copy(isListening = false, statusNotice = "Opening device voice input...") }
                onFallbackDialogRequested?.invoke(buildSystemSpeechIntent())
                return@post
            }

            startRecognizerInternal(isWakePass = false)
        }
    }

    fun stopCommandListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            _state.update { it.copy(isListening = false, audioLevel = 0f) }
            if (wakeStandbyDesired) {
                scheduleWakeRearm(700L)
            }
        }
    }

    /**
     * Enables or disables foreground "Hey SplitFlow" hands-free wake-word standby.
     */
    fun setWakeStandbyEnabled(enabled: Boolean) {
        mainHandler.post {
            if (isReleased) return@post
            wakeStandbyDesired = enabled
            mainHandler.removeCallbacks(wakeRearmRunnable)
            if (!enabled) {
                if (inWakeMode) {
                    runCatching { speechRecognizer?.cancel() }
                    inWakeMode = false
                }
                _state.update { it.copy(isWakeStandbyActive = false, audioLevel = 0f) }
            } else if (!_state.value.isListening && !_state.value.isSpeaking) {
                startWakeListeningInternal()
            }
        }
    }

    /**
     * Call on lifecycle ON_PAUSE to release mic cleanly when app moves to background.
     */
    fun pauseForBackground() {
        mainHandler.post {
            mainHandler.removeCallbacks(wakeRearmRunnable)
            runCatching { speechRecognizer?.cancel() }
            inWakeMode = false
            _state.update { it.copy(isListening = false, isWakeStandbyActive = false, audioLevel = 0f) }
        }
    }

    /**
     * Call on lifecycle ON_RESUME to re-arm wake-word standby if enabled.
     */
    fun resumeInForeground() {
        mainHandler.post {
            if (!isReleased && wakeStandbyDesired && !_state.value.isListening && !_state.value.isSpeaking) {
                scheduleWakeRearm(500L)
            }
        }
    }

    fun speak(
        text: String,
        ignoreSoundboxToggle: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        val normalized = normalizeForSpeech(text)
        if (normalized.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!soundboxEnabled && !ignoreSoundboxToggle) {
            onDone?.invoke()
            return
        }
        mainHandler.post {
            if (isReleased) return@post
            // Pause wake listening while TTS is speaking so we never trigger on our own voice
            mainHandler.removeCallbacks(wakeRearmRunnable)
            if (inWakeMode) {
                runCatching { speechRecognizer?.cancel() }
                inWakeMode = false
                _state.update { it.copy(isWakeStandbyActive = false, audioLevel = 0f) }
            }

            val muted = isMediaMuted()
            _state.update { it.copy(isMediaVolumeMuted = muted) }
            onSpeechFinishedCallback = onDone

            if (!_state.value.ttsReady || tts == null) {
                pendingUtterance = normalized
                return@post
            }
            speakInternal(normalized)
        }
    }

    fun previewCurrentPersona() {
        val sample = when (currentLocale) {
            SplitFlowLocale.HI_IN -> "नमस्ते, मैं ${currentPersona.displayName} हूँ। तन्मय ने आपको 500 रुपये देने हैं, रिकॉर्ड हो गया।"
            SplitFlowLocale.MR_IN -> "नमस्कार, मी ${currentPersona.displayName} आहे. तन्मयकडे 500 रुपये बाकी नोंदवले आहेत."
            SplitFlowLocale.TA_IN -> "வணக்கம், நான் ${currentPersona.displayName}. தன்மய் 500 ரூபாய் தர வேண்டும் என பதிவு செய்தேன்."
            SplitFlowLocale.EN_IN -> "Hi, I'm ${currentPersona.displayName}. Logged 500 rupees so Tanmay owes you."
        }
        speak(sample, ignoreSoundboxToggle = true)
    }

    fun stopSpeaking() {
        mainHandler.post {
            stopSpeakingInternal()
            if (wakeStandbyDesired) scheduleWakeRearm(500L)
        }
    }

    fun release() {
        isReleased = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
        }
    }

    private fun startWakeListeningInternal() {
        if (isReleased || !wakeStandbyDesired || _state.value.isSpeaking || _state.value.isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update { it.copy(isWakeStandbyActive = false) }
            return
        }
        inWakeMode = true
        _state.update { it.copy(isWakeStandbyActive = true, errorMessage = null) }
        startRecognizerInternal(isWakePass = true)
    }

    private fun startRecognizerInternal(isWakePass: Boolean) {
        try {
            runCatching { speechRecognizer?.destroy() }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.update {
                        if (isWakePass) it.copy(isWakeStandbyActive = true)
                        else it.copy(isListening = true, errorMessage = null)
                    }
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _state.update { it.copy(audioLevel = normalized) }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isWakePass) {
                        _state.update { it.copy(audioLevel = 0f) }
                    }
                }

                override fun onError(error: Int) {
                    if (isWakePass) {
                        inWakeMode = false
                        _state.update { it.copy(audioLevel = 0f) }
                        if (wakeStandbyDesired && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 600L
                            scheduleWakeRearm(delay)
                        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            wakeStandbyDesired = false
                            _state.update { it.copy(isWakeStandbyActive = false) }
                        }
                        return
                    }

                    _state.update { it.copy(isListening = false, audioLevel = 0f) }
                    val shouldFallbackToDialog = error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_SERVER
                    if (shouldFallbackToDialog && onFallbackDialogRequested != null) {
                        onFallbackDialogRequested?.invoke(buildSystemSpeechIntent())
                    } else {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap mic and speak clearly."
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check if another app is using the mic."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service needs network or downloaded offline voice pack."
                            else -> "Speech recognizer paused (code $error). Tap mic to retry."
                        }
                        _state.update { it.copy(errorMessage = msg, statusNotice = null) }
                        if (wakeStandbyDesired) scheduleWakeRearm(1000L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (isWakePass) {
                        inWakeMode = false
                        _state.update { it.copy(audioLevel = 0f) }
                        val wakeHit = matches.firstNotNullOfOrNull { extractWakeCommand(it) }
                        if (wakeHit != null) {
                            _state.update { it.copy(isWakeStandbyActive = false) }
                            val inlineCmd = wakeHit.takeIf { it.isNotBlank() }
                            onWakeDetected?.invoke(inlineCmd)
                            if (inlineCmd != null) {
                                onCommandCaptured?.invoke(inlineCmd)
                            } else {
                                speak("Listening", ignoreSoundboxToggle = false) {
                                    startCommandListening()
                                }
                            }
                        } else if (wakeStandbyDesired) {
                            scheduleWakeRearm(450L)
                        }
                        return
                    }

                    _state.update { it.copy(isListening = false, audioLevel = 0f) }
                    val best = LocalVoiceParser.pickBestTranscript(matches, activeMembers)
                        .ifBlank { _state.value.partialTranscript.trim() }
                    if (best.isNotBlank()) {
                        // Strip accidental wake phrase prefix if user said "Hey SplitFlow ..."
                        val cleaned = extractWakeCommand(best)?.takeIf { it.isNotBlank() } ?: best
                        _state.update { it.copy(partialTranscript = cleaned, statusNotice = null, errorMessage = null) }
                        onCommandCaptured?.invoke(cleaned)
                    } else {
                        _state.update { it.copy(errorMessage = "Didn't catch speech. Tap mic and try again.") }
                    }
                    if (wakeStandbyDesired) scheduleWakeRearm(1200L)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isEmpty()) return

                    if (isWakePass) {
                        // Fast wake trigger on partial match if user only spoke the wake phrase
                        if (matchesWakePhraseOnly(partial)) {
                            inWakeMode = false
                            runCatching { recognizer.cancel() }
                            _state.update { it.copy(isWakeStandbyActive = false, audioLevel = 0f) }
                            onWakeDetected?.invoke(null)
                            speak("Listening", ignoreSoundboxToggle = false) {
                                startCommandListening()
                            }
                        }
                    } else {
                        _state.update { it.copy(partialTranscript = partial) }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(buildSystemSpeechIntent())
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer start failed: ${e.message}")
            if (!isWakePass) {
                _state.update { it.copy(isListening = false, audioLevel = 0f) }
                onFallbackDialogRequested?.invoke(buildSystemSpeechIntent())
            } else {
                inWakeMode = false
                if (wakeStandbyDesired) scheduleWakeRearm(1500L)
            }
        }
    }

    private fun scheduleWakeRearm(delayMs: Long) {
        mainHandler.removeCallbacks(wakeRearmRunnable)
        mainHandler.postDelayed(wakeRearmRunnable, delayMs)
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (isReleased) return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed status=$status")
                    _state.update { it.copy(ttsReady = false) }
                    return@TextToSpeech
                }
                val engine = tts ?: return@TextToSpeech
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            _state.update { it.copy(isSpeaking = false) }
                            val cb = onSpeechFinishedCallback
                            onSpeechFinishedCallback = null
                            cb?.invoke()
                            if (wakeStandbyDesired && !_state.value.isListening) {
                                scheduleWakeRearm(550L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            _state.update { it.copy(isSpeaking = false) }
                            val cb = onSpeechFinishedCallback
                            onSpeechFinishedCallback = null
                            cb?.invoke()
                            if (wakeStandbyDesired && !_state.value.isListening) {
                                scheduleWakeRearm(700L)
                            }
                        }
                    }
                })

                applyTtsVoiceConfiguration()
                _state.update { it.copy(ttsReady = true, isMediaVolumeMuted = isMediaMuted()) }
                pendingUtterance?.let { queued ->
                    pendingUtterance = null
                    speakInternal(queued)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }
    }

    private fun applyTtsVoiceConfiguration() {
        val engine = tts ?: return
        runCatching {
            val desiredLocale = currentLocale.locale
            val langRes = engine.setLanguage(desiredLocale)
            val effectiveLocale = if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(SplitFlowLocale.EN_IN.locale)
                SplitFlowLocale.EN_IN.locale
            } else {
                desiredLocale
            }

            // Pick best matching voice for persona, strongly preferring non-network (on-device) voices
            val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            val bestVoice = selectBestVoice(voices, effectiveLocale, currentPersona)
            if (bestVoice != null) {
                engine.voice = bestVoice
            }
            engine.setPitch(currentPersona.pitch)
            engine.setSpeechRate(currentPersona.speechRate)
            _state.update {
                it.copy(ttsOnDeviceVoice = bestVoice?.isNetworkConnectionRequired == false)
            }
        }
    }

    private fun selectBestVoice(
        voices: Set<Voice>,
        targetLocale: Locale,
        persona: SplitFlowVoicePersona
    ): Voice? {
        val matching = voices.filter { v ->
            v.locale.language.equals(targetLocale.language, ignoreCase = true)
        }
        if (matching.isEmpty()) return null

        val genderHints = if (persona.prefersFemale) {
            listOf("female", "f00", "ind-f", "en-in-x-end", "en-in-x-ene", "hi-in-x-hia", "hi-in-x-hic", "sfg")
        } else {
            listOf("male", "m00", "ind-m", "en-in-x-enc", "en-in-x-ahp", "hi-in-x-hid", "hi-in-x-hie")
        }

        return matching.maxByOrNull { v ->
            var score = 0
            if (!v.isNetworkConnectionRequired) score += 40
            if (v.locale.country.equals(targetLocale.country, ignoreCase = true)) score += 25
            val nameLower = v.name.lowercase(Locale.US)
            if (genderHints.any { nameLower.contains(it) }) score += 20
            if (v.quality >= Voice.QUALITY_HIGH) score += 10
            score
        }
    }

    private fun speakInternal(text: String) {
        val engine = tts ?: return
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "sf_${UUID.randomUUID().toString().take(8)}")
    }

    private fun stopSpeakingInternal() {
        pendingUtterance = null
        onSpeechFinishedCallback = null
        runCatching { tts?.stop() }
        _state.update { it.copy(isSpeaking = false) }
    }

    private fun isMediaMuted(): Boolean {
        val am = audioManager ?: return false
        return runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "SplitFlowVoiceCtrl"

        private val WAKE_PREFIX_REGEX = Regex(
            "^(?:hey|hi|ok|okay|hello|हे|हाय|ஹே)\\s*(?:split\\s*flow|splitflow|split\\s*pro|स्प्लिट\\s*फ्लो|स्प्लिटफ्लो|ஸ்பிளிட்\\s*ஃப்ளோ)[,.:!?\\s]*(.*)$",
            RegexOption.IGNORE_CASE
        )

        /**
         * Returns non-null remainder if the utterance begins with the "Hey SplitFlow" wake phrase
         * (empty string if user spoke only the wake phrase, or trailing command if spoken in one breath).
         */
        fun extractWakeCommand(transcript: String): String? {
            val trimmed = transcript.trim()
            val match = WAKE_PREFIX_REGEX.find(trimmed) ?: return null
            return match.groupValues.getOrNull(1)?.trim().orEmpty()
        }

        fun matchesWakePhraseOnly(transcript: String): Boolean {
            val remainder = extractWakeCommand(transcript)
            return remainder != null && remainder.isEmpty()
        }

        fun normalizeForSpeech(raw: String): String {
            return raw
                .replace(Regex("₹\\s*([0-9,]+(?:\\.\\d+)?)"), "$1 rupees")
                .replace(Regex("\\$\\s*([0-9,]+(?:\\.\\d+)?)"), "$1 dollars")
                .replace(Regex("€\\s*([0-9,]+(?:\\.\\d+)?)"), "$1 euros")
                .replace(Regex("£\\s*([0-9,]+(?:\\.\\d+)?)"), "$1 pounds")
                .replace("·", ", ")
                .replace("—", ", ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
