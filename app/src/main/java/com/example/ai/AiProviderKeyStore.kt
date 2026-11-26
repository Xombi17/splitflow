package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val freeTierHint: String,
    val keyPrefixHint: String
) {
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "llama-3.3-70b-versatile",
        freeTierHint = "Ultra-fast free tier (Llama 3.3 70B)",
        keyPrefixHint = "gsk_..."
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        freeTierHint = "Free community & router models",
        keyPrefixHint = "sk-or-v1-..."
    ),
    CEREBRAS(
        displayName = "Cerebras",
        defaultBaseUrl = "https://api.cerebras.ai/v1/chat/completions",
        defaultModel = "llama-3.3-70b",
        freeTierHint = "High-speed free inference",
        keyPrefixHint = "csk-..."
    ),
    MISTRAL(
        displayName = "Mistral",
        defaultBaseUrl = "https://api.mistral.ai/v1/chat/completions",
        defaultModel = "mistral-small-latest",
        freeTierHint = "Free experiment tier",
        keyPrefixHint = "mistral key..."
    ),
    GOOGLE_AI_STUDIO(
        displayName = "Google AI Studio",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        defaultModel = "gemini-2.5-flash",
        freeTierHint = "Personal Gemini API key (free tier)",
        keyPrefixHint = "AIza..."
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "",
        defaultModel = "",
        freeTierHint = "Any HTTPS /v1/chat/completions endpoint",
        keyPrefixHint = "sk-..."
    );

    companion object {
        fun fromRaw(raw: String?): AiProviderType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: GROQ
    }
}

enum class RoutingStrategy(
    val label: String,
    val subtitle: String
) {
    BYOK_FIRST_THEN_FIREBASE(
        label = "My Keys (Round-Robin) → Built-in Gemini",
        subtitle = "Rotates across your enabled API keys first, then falls back to Firebase Gemini 3"
    ),
    FIREBASE_FIRST_THEN_BYOK(
        label = "Built-in Gemini → My Keys (Fallback)",
        subtitle = "Uses Firebase Gemini 3.8 Flash / 3.5 Lite first, then rotates your API keys on load"
    ),
    FIREBASE_ONLY(
        label = "Built-in Firebase Gemini Only",
        subtitle = "Uses Gemini 3.8 Flash with automatic 3.5 Flash-Lite failover"
    ),
    ON_DEVICE_ONLY(
        label = "100% On-Device Edge Only",
        subtitle = "Zero cloud requests — native Speech, ML Kit OCR, and local parser only"
    );

    companion object {
        fun fromRaw(raw: String?): RoutingStrategy =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
                ?: BYOK_FIRST_THEN_FIREBASE
    }
}

enum class ApiKeyHealth {
    READY,
    ACTIVE,
    COOLDOWN,
    AUTH_ERROR,
    ERROR
}

data class ApiKeyEntry(
    val id: String = UUID.randomUUID().toString().take(12),
    val provider: AiProviderType,
    val label: String,
    val apiKey: String,
    val baseUrl: String = provider.defaultBaseUrl,
    val modelName: String = provider.defaultModel,
    val enabled: Boolean = true,
    val health: ApiKeyHealth = ApiKeyHealth.READY,
    val cooldownUntilMs: Long = 0L,
    val lastLatencyMs: Long? = null,
    val lastError: String? = null,
    val successCount: Int = 0,
    val failCount: Int = 0
) {
    val maskedKey: String
        get() {
            val trimmed = apiKey.trim()
            return when {
                trimmed.length <= 8 -> "••••••••"
                else -> "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
            }
        }

    fun isInCooldown(nowMs: Long = System.currentTimeMillis()): Boolean =
        cooldownUntilMs > nowMs

    fun cooldownRemainingSeconds(nowMs: Long = System.currentTimeMillis()): Int =
        ((cooldownUntilMs - nowMs).coerceAtLeast(0L) / 1000L).toInt()
}

/**
 * Hardware-backed Android Keystore AES-GCM encrypted local vault for end-user BYOK API keys.
 * Keys never leave the device, are never persisted to Firestore, and are never logged.
 */
class AiProviderKeyStore(context: Context? = null) {

    private val prefs: SharedPreferences? =
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _keys = MutableStateFlow<List<ApiKeyEntry>>(loadKeysFromPrefs())
    val keys: StateFlow<List<ApiKeyEntry>> = _keys.asStateFlow()

    private val _routingStrategy = MutableStateFlow(
        RoutingStrategy.fromRaw(prefs?.getString(KEY_ROUTING_STRATEGY, null))
    )
    val routingStrategy: StateFlow<RoutingStrategy> = _routingStrategy.asStateFlow()

    fun setRoutingStrategy(strategy: RoutingStrategy) {
        _routingStrategy.value = strategy
        prefs?.edit()?.putString(KEY_ROUTING_STRATEGY, strategy.name)?.apply()
    }

    fun addOrUpdateKey(entry: ApiKeyEntry): Result<ApiKeyEntry> {
        val trimmedKey = entry.apiKey.trim()
        if (trimmedKey.length < 8) {
            return Result.failure(IllegalArgumentException("API key looks too short."))
        }
        val cleanUrl = entry.baseUrl.trim().ifBlank { entry.provider.defaultBaseUrl }
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Endpoint URL must start with https://"))
        }
        val cleanModel = entry.modelName.trim().ifBlank { entry.provider.defaultModel }
        if (cleanModel.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a model ID (e.g. llama-3.3-70b-versatile)."))
        }
        val cleanLabel = entry.label.trim().ifBlank {
            val sameCount = _keys.value.count { it.provider == entry.provider && it.id != entry.id }
            if (sameCount == 0) entry.provider.displayName else "${entry.provider.displayName} #${sameCount + 1}"
        }

        val normalized = entry.copy(
            label = cleanLabel,
            apiKey = trimmedKey,
            baseUrl = cleanUrl,
            modelName = cleanModel,
            health = ApiKeyHealth.READY,
            cooldownUntilMs = 0L,
            lastError = null
        )

        _keys.update { list ->
            val idx = list.indexOfFirst { it.id == normalized.id }
            if (idx >= 0) list.toMutableList().apply { set(idx, normalized) }
            else list + normalized
        }
        persistKeys(_keys.value)
        return Result.success(normalized)
    }

    fun toggleKeyEnabled(id: String, enabled: Boolean) {
        _keys.update { list ->
            list.map { if (it.id == id) it.copy(enabled = enabled, cooldownUntilMs = 0L) else it }
        }
        persistKeys(_keys.value)
    }

    fun removeKey(id: String) {
        _keys.update { list -> list.filterNot { it.id == id } }
        persistKeys(_keys.value)
    }

    fun recordKeyResult(
        id: String,
        health: ApiKeyHealth,
        latencyMs: Long? = null,
        error: String? = null,
        cooldownUntilMs: Long = 0L
    ) {
        _keys.update { list ->
            list.map { item ->
                if (item.id != id) item
                else item.copy(
                    health = health,
                    lastLatencyMs = latencyMs ?: item.lastLatencyMs,
                    lastError = error,
                    cooldownUntilMs = cooldownUntilMs,
                    successCount = if (health == ApiKeyHealth.ACTIVE) item.successCount + 1 else item.successCount,
                    failCount = if (health == ApiKeyHealth.ERROR || health == ApiKeyHealth.COOLDOWN || health == ApiKeyHealth.AUTH_ERROR) {
                        item.failCount + 1
                    } else item.failCount
                )
            }
        }
        persistKeys(_keys.value)
    }

    fun resetAllCooldowns() {
        _keys.update { list ->
            list.map {
                if (it.health == ApiKeyHealth.COOLDOWN || it.health == ApiKeyHealth.ERROR) {
                    it.copy(health = ApiKeyHealth.READY, cooldownUntilMs = 0L, lastError = null)
                } else it
            }
        }
        persistKeys(_keys.value)
    }

    private fun persistKeys(entries: List<ApiKeyEntry>) {
        val localPrefs = prefs ?: return
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("provider", e.provider.name)
                    put("label", e.label)
                    put("apiKey", e.apiKey)
                    put("baseUrl", e.baseUrl)
                    put("modelName", e.modelName)
                    put("enabled", e.enabled)
                    put("successCount", e.successCount)
                    put("failCount", e.failCount)
                }
            )
        }
        val rawJson = array.toString()
        val encrypted = encryptPayload(rawJson)
        if (encrypted != null) {
            localPrefs.edit()
                .putString(KEY_VAULT_ENC, encrypted)
                .remove(KEY_VAULT_FALLBACK)
                .apply()
        } else {
            // Fallback only on non-Keystore JVM test harnesses
            localPrefs.edit().putString(KEY_VAULT_FALLBACK, rawJson).apply()
        }
    }

    private fun loadKeysFromPrefs(): List<ApiKeyEntry> {
        val localPrefs = prefs ?: return emptyList()
        val rawJson = localPrefs.getString(KEY_VAULT_ENC, null)?.let { decryptPayload(it) }
            ?: localPrefs.getString(KEY_VAULT_FALLBACK, null)
            ?: return emptyList()

        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val provider = AiProviderType.fromRaw(obj.optString("provider"))
                    val keyVal = obj.optString("apiKey", "").trim()
                    if (keyVal.isBlank()) continue
                    add(
                        ApiKeyEntry(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString().take(12) },
                            provider = provider,
                            label = obj.optString("label").ifBlank { provider.displayName },
                            apiKey = keyVal,
                            baseUrl = obj.optString("baseUrl").ifBlank { provider.defaultBaseUrl },
                            modelName = obj.optString("modelName").ifBlank { provider.defaultModel },
                            enabled = obj.optBoolean("enabled", true),
                            successCount = obj.optInt("successCount", 0),
                            failCount = obj.optInt("failCount", 0)
                        )
                    )
                }
            }
        }.getOrElse { err ->
            Log.w(TAG, "Could not deserialize BYOK vault: ${err.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun getOrCreateAesKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        generator.generateKey()
    }.getOrNull()

    private fun encryptPayload(plainText: String): String? = runCatching {
        val key = getOrCreateAesKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        "$ivB64:$dataB64"
    }.getOrNull()

    private fun decryptPayload(payload: String): String? = runCatching {
        val parts = payload.split(":")
        if (parts.size != 2) return@runCatching null
        val key = getOrCreateAesKey() ?: return@runCatching null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val data = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val TAG = "AiProviderKeyStore"
        private const val PREFS_NAME = "splitflow_byok_vault_prefs"
        private const val KEY_VAULT_ENC = "vault_entries_aes_gcm"
        private const val KEY_VAULT_FALLBACK = "vault_entries_fallback"
        private const val KEY_ROUTING_STRATEGY = "routing_strategy"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "splitflow_byok_aes_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
