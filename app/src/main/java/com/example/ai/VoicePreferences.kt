package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

enum class SplitFlowLocale(
    val shortBadge: String,
    val displayName: String,
    val bcp47Tag: String,
    val locale: Locale,
    val promptHint: String
) {
    EN_IN(
        shortBadge = "EN",
        displayName = "English (India)",
        bcp47Tag = "en-IN",
        locale = Locale("en", "IN"),
        promptHint = "Say e.g. \"Tanmay owes me 500 rupees\""
    ),
    HI_IN(
        shortBadge = "हिन्दी",
        displayName = "Hindi",
        bcp47Tag = "hi-IN",
        locale = Locale("hi", "IN"),
        promptHint = "बोलें: \"Tanmay ko 500 dene hain\" या \"500 split karo\""
    ),
    MR_IN(
        shortBadge = "मराठी",
        displayName = "Marathi",
        bcp47Tag = "mr-IN",
        locale = Locale("mr", "IN"),
        promptHint = "बोला: \"Tanmay kade 500 rupaye baki aahet\""
    ),
    TA_IN(
        shortBadge = "தமிழ்",
        displayName = "Tamil",
        bcp47Tag = "ta-IN",
        locale = Locale("ta", "IN"),
        promptHint = "சொல்லுங்கள்: \"Tanmay enakku 500 rubai tharanum\""
    );

    companion object {
        fun fromRaw(raw: String?): SplitFlowLocale =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EN_IN
    }
}

enum class SplitFlowVoicePersona(
    val displayName: String,
    val styleLabel: String,
    val prefersFemale: Boolean,
    val pitch: Float,
    val speechRate: Float
) {
    DIYA(
        displayName = "Diya",
        styleLabel = "Warm Female",
        prefersFemale = true,
        pitch = 1.04f,
        speechRate = 0.98f
    ),
    AARAV(
        displayName = "Aarav",
        styleLabel = "Steady Male",
        prefersFemale = false,
        pitch = 0.86f,
        speechRate = 0.96f
    ),
    RIYA(
        displayName = "Riya",
        styleLabel = "Bright Female",
        prefersFemale = true,
        pitch = 1.12f,
        speechRate = 1.02f
    ),
    KABIR(
        displayName = "Kabir",
        styleLabel = "Calm Male",
        prefersFemale = false,
        pitch = 0.80f,
        speechRate = 0.93f
    );

    companion object {
        fun fromRaw(raw: String?): SplitFlowVoicePersona =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DIYA
    }
}

data class VoiceSettingsState(
    val locale: SplitFlowLocale = SplitFlowLocale.EN_IN,
    val persona: SplitFlowVoicePersona = SplitFlowVoicePersona.DIYA,
    val soundboxEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false
)

class VoicePreferenceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        VoiceSettingsState(
            locale = SplitFlowLocale.fromRaw(prefs.getString(KEY_LOCALE, null)),
            persona = SplitFlowVoicePersona.fromRaw(prefs.getString(KEY_PERSONA, null)),
            soundboxEnabled = prefs.getBoolean(KEY_SOUNDBOX, true),
            wakeWordEnabled = prefs.getBoolean(KEY_WAKE_WORD, false)
        )
    )
    val settings: StateFlow<VoiceSettingsState> = _settings.asStateFlow()

    fun setLocale(locale: SplitFlowLocale) {
        _settings.update { it.copy(locale = locale) }
        prefs.edit().putString(KEY_LOCALE, locale.name).apply()
    }

    fun setPersona(persona: SplitFlowVoicePersona) {
        _settings.update { it.copy(persona = persona) }
        prefs.edit().putString(KEY_PERSONA, persona.name).apply()
    }

    fun setSoundboxEnabled(enabled: Boolean) {
        _settings.update { it.copy(soundboxEnabled = enabled) }
        prefs.edit().putBoolean(KEY_SOUNDBOX, enabled).apply()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _settings.update { it.copy(wakeWordEnabled = enabled) }
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "splitflow_voice_prefs"
        private const val KEY_LOCALE = "voice_locale"
        private const val KEY_PERSONA = "voice_persona"
        private const val KEY_SOUNDBOX = "voice_soundbox_enabled"
        private const val KEY_WAKE_WORD = "voice_wake_word_enabled"
    }
}
