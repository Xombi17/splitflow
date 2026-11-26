package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class EngineTier {
    ON_DEVICE_EDGE,
    BYOK_ROUND_ROBIN,
    FIREBASE_GEMINI_PRIMARY,
    FIREBASE_GEMINI_LITE
}

data class AiRouterStatus(
    val lastVoiceEngineLabel: String = "On-Device Edge Ready",
    val lastVoiceTier: EngineTier = EngineTier.ON_DEVICE_EDGE,
    val lastVoiceDetail: String = "Native Speech + Local Parser with Gemini/BYOK fallback",
    val lastOcrEngineLabel: String = "ML Kit Spatial OCR Ready",
    val lastOcrTier: EngineTier = EngineTier.ON_DEVICE_EDGE,
    val lastOcrDetail: String = "Instant on-device OCR with Gemini Vision failover",
    val lastCloudNotice: String? = null
)

data class AiRouteResult(
    val text: String,
    val engineLabel: String,
    val tier: EngineTier,
    val latencyMs: Long,
    val fallbackNote: String? = null
)

data class ApiKeyTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String
)

/**
 * Pure round-robin + failover coordinator across:
 * 1. User BYOK pool (Groq, OpenRouter, Cerebras, Mistral, Google AI Studio, Custom HTTPS)
 *    with per-key cooldown on 429/503/timeout and automatic rotation.
 * 2. Built-in Firebase AI Logic (gemini-3.8-flash -> automatic gemini-3.5-flash-lite failover).
 */
class MultiProviderAiRouter(
    val keyStore: AiProviderKeyStore,
    private val openAiTransport: (suspend (ApiKeyEntry, String, String, Boolean) -> HttpResponsePayload)? = null,
    private val firebaseTextTransport: (suspend (String, String, Schema?) -> String)? = null
) {
    data class HttpResponsePayload(
        val statusCode: Int,
        val body: String,
        val errorMessage: String? = null
    )

    private val roundRobinCursor = AtomicInteger(0)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Lazy Firebase AI Logic models (no API key required; App Check secured)
    private val primaryGeminiModel by lazy {
        Firebase.ai.generativeModel(modelName = PRIMARY_GEMINI_MODEL)
    }
    private val liteGeminiModel by lazy {
        Firebase.ai.generativeModel(modelName = LITE_GEMINI_MODEL)
    }

    private val _status = MutableStateFlow(AiRouterStatus())
    val status: StateFlow<AiRouterStatus> = _status.asStateFlow()

    fun recordVoiceRun(label: String, tier: EngineTier, detail: String, notice: String? = null) {
        _status.update {
            it.copy(
                lastVoiceEngineLabel = label,
                lastVoiceTier = tier,
                lastVoiceDetail = detail,
                lastCloudNotice = notice
            )
        }
    }

    fun recordOcrRun(label: String, tier: EngineTier, detail: String, notice: String? = null) {
        _status.update {
            it.copy(
                lastOcrEngineLabel = label,
                lastOcrTier = tier,
                lastOcrDetail = detail,
                lastCloudNotice = notice
            )
        }
    }

    /**
     * Executes a structured JSON or text prompt following the user's configured routing strategy:
     * - BYOK Round-Robin Pool (rotating across enabled non-cooled-down keys)
     * - Built-in Firebase AI Logic (gemini-3.8-flash -> gemini-3.5-flash-lite failover)
     */
    suspend fun generateStructuredOrText(
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean,
        firebaseSchema: Schema? = null
    ): AiRouteResult {
        val strategy = keyStore.routingStrategy.value
        if (strategy == RoutingStrategy.ON_DEVICE_ONLY) {
            throw IllegalStateException("Cloud AI is disabled (100% On-Device Edge mode).")
        }

        val errors = mutableListOf<String>()
        val enabledByok = keyStore.keys.value.filter { it.enabled }

        val order: List<String> = when (strategy) {
            RoutingStrategy.BYOK_FIRST_THEN_FIREBASE -> {
                if (enabledByok.isNotEmpty()) listOf("BYOK", "FIREBASE") else listOf("FIREBASE")
            }
            RoutingStrategy.FIREBASE_FIRST_THEN_BYOK -> {
                if (enabledByok.isNotEmpty()) listOf("FIREBASE", "BYOK") else listOf("FIREBASE")
            }
            RoutingStrategy.FIREBASE_ONLY -> listOf("FIREBASE")
            RoutingStrategy.ON_DEVICE_ONLY -> emptyList()
        }

        for (stage in order) {
            when (stage) {
                "BYOK" -> {
                    val byokRes = tryByokRoundRobin(systemPrompt, userPrompt, expectJson, errors)
                    if (byokRes != null) return byokRes
                }
                "FIREBASE" -> {
                    val fbRes = tryFirebaseGeminiChain(
                        fullPrompt = "$systemPrompt\n\n$userPrompt",
                        expectJson = expectJson,
                        schema = firebaseSchema,
                        priorErrors = errors
                    )
                    if (fbRes != null) return fbRes
                }
            }
        }

        val summary = errors.takeLast(3).joinToString(" | ").ifBlank { "No cloud AI provider responded." }
        throw IllegalStateException("All AI providers busy or unreachable: $summary")
    }

    /**
     * Multimodal Receipt OCR fallback via Firebase AI Logic (gemini-3.8-flash -> gemini-3.5-flash-lite).
     */
    suspend fun generateMultimodalJson(
        bitmap: Bitmap,
        prompt: String,
        schema: Schema
    ): AiRouteResult {
        if (keyStore.routingStrategy.value == RoutingStrategy.ON_DEVICE_ONLY) {
            throw IllegalStateException("Cloud AI is disabled (100% On-Device Edge mode).")
        }
        val models = listOf(
            PRIMARY_GEMINI_MODEL to EngineTier.FIREBASE_GEMINI_PRIMARY,
            LITE_GEMINI_MODEL to EngineTier.FIREBASE_GEMINI_LITE
        )
        val errors = mutableListOf<String>()

        for ((idx, pair) in models.withIndex()) {
            val (modelName, tier) = pair
            val startMs = System.currentTimeMillis()
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = schema
                        temperature = 0.1f
                    }
                )
                val response = withTimeout(45_000L) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) {
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val fallbackNote = if (idx > 0) "Auto-switched to $modelName due to high demand" else null
                    return AiRouteResult(
                        text = text,
                        engineLabel = if (idx == 0) "Gemini 3.8 Flash Vision" else "Gemini 3.5 Flash-Lite Vision",
                        tier = tier,
                        latencyMs = elapsed,
                        fallbackNote = fallbackNote
                    )
                }
                errors.add("$modelName returned empty receipt JSON")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Multimodal $modelName timed out; trying fallback")
                    errors.add("$modelName timed out")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Multimodal $modelName failed: ${e.message}")
                errors.add("$modelName: ${sanitizeError(e.message)}")
            }
        }
        throw IllegalStateException(errors.joinToString(" | ").ifBlank { "Multimodal receipt AI failed." })
    }

    /**
     * Streams Spend Coach follow-up answers with automatic BYOK / Gemini 3.8 -> 3.5 Lite failover.
     */
    fun streamCoachingAnswer(systemPrompt: String, userPrompt: String): Flow<String> = flow {
        val strategy = keyStore.routingStrategy.value
        if (strategy == RoutingStrategy.ON_DEVICE_ONLY) {
            throw IllegalStateException("On-device mode")
        }

        // If BYOK is preferred and available, serve fast non-streaming response from round-robin pool
        if (strategy == RoutingStrategy.BYOK_FIRST_THEN_FIREBASE && keyStore.keys.value.any { it.enabled }) {
            val errors = mutableListOf<String>()
            val byok = tryByokRoundRobin(systemPrompt, userPrompt, expectJson = false, errors = errors)
            if (byok != null) {
                emit(byok.text)
                return@flow
            }
        }

        // Firebase AI Logic streaming: gemini-3.8-flash -> gemini-3.5-flash-lite
        val models = listOf(primaryGeminiModel to PRIMARY_GEMINI_MODEL, liteGeminiModel to LITE_GEMINI_MODEL)
        var lastError: Throwable? = null
        for ((model, name) in models) {
            var emittedAny = false
            try {
                withTimeout(45_000L) {
                    model.generateContentStream("$systemPrompt\n\n$userPrompt").collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            emittedAny = true
                            emit(part)
                        }
                    }
                }
                if (emittedAny) return@flow
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && !emittedAny) {
                    lastError = e
                    Log.w(TAG, "Stream $name timed out; failing over")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Stream $name failed: ${e.message}")
                if (emittedAny) throw e
            }
        }
        throw lastError ?: IllegalStateException("All streaming AI models unavailable")
    }

    /**
     * Tests an individual BYOK API key entry directly so users can verify latency and acceptance.
     */
    suspend fun testApiKey(entry: ApiKeyEntry): ApiKeyTestResult {
        val startMs = System.currentTimeMillis()
        return try {
            val payload = executeOpenAiCompatibleCall(
                entry = entry,
                systemPrompt = "Reply with strictly OK.",
                userPrompt = "Ping",
                expectJson = false
            )
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (payload.statusCode in 200..299 && payload.body.isNotBlank()) {
                keyStore.recordKeyResult(entry.id, ApiKeyHealth.ACTIVE, latencyMs = elapsed)
                ApiKeyTestResult(
                    success = true,
                    latencyMs = elapsed,
                    message = "Connected (${elapsed}ms) · ${entry.modelName}"
                )
            } else {
                val health = when (payload.statusCode) {
                    401, 403 -> ApiKeyHealth.AUTH_ERROR
                    429, 503 -> ApiKeyHealth.COOLDOWN
                    else -> ApiKeyHealth.ERROR
                }
                val msg = payload.errorMessage ?: "HTTP ${payload.statusCode}"
                keyStore.recordKeyResult(
                    id = entry.id,
                    health = health,
                    latencyMs = elapsed,
                    error = msg,
                    cooldownUntilMs = if (health == ApiKeyHealth.COOLDOWN) System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS else 0L
                )
                ApiKeyTestResult(false, elapsed, "HTTP ${payload.statusCode}: $msg")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val msg = sanitizeError(e.message)
            keyStore.recordKeyResult(entry.id, ApiKeyHealth.ERROR, latencyMs = elapsed, error = msg)
            ApiKeyTestResult(false, elapsed, msg)
        }
    }

    private suspend fun tryByokRoundRobin(
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean,
        errors: MutableList<String>
    ): AiRouteResult? {
        val enabled = keyStore.keys.value.filter { it.enabled }
        if (enabled.isEmpty()) return null

        val nowMs = System.currentTimeMillis()
        val ordered = buildRoundRobinOrder(enabled, nowMs)
        val totalAttempts = ordered.size

        for (i in 0 until totalAttempts) {
            val entry = ordered[i]
            val startMs = System.currentTimeMillis()
            try {
                val payload = executeOpenAiCompatibleCall(entry, systemPrompt, userPrompt, expectJson)
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

                if (payload.statusCode in 200..299 && payload.body.isNotBlank()) {
                    keyStore.recordKeyResult(
                        id = entry.id,
                        health = ApiKeyHealth.ACTIVE,
                        latencyMs = elapsed,
                        error = null,
                        cooldownUntilMs = 0L
                    )
                    // Advance cursor past the winning key so the next request rotates fairly
                    val idxInEnabled = enabled.indexOfFirst { it.id == entry.id }
                    if (idxInEnabled >= 0) {
                        roundRobinCursor.set((idxInEnabled + 1) % enabled.size)
                    }
                    val note = if (i > 0) "Round-robin switched to ${entry.label} after ${i} retry(s)" else null
                    return AiRouteResult(
                        text = payload.body,
                        engineLabel = "${entry.label} · ${entry.modelName.substringAfterLast('/')}",
                        tier = EngineTier.BYOK_ROUND_ROBIN,
                        latencyMs = elapsed,
                        fallbackNote = note
                    )
                }

                // Handle rate limit / high demand / auth error by cooling down and rotating
                val cooldownMs = when (payload.statusCode) {
                    401, 403 -> AUTH_ERROR_COOLDOWN_MS
                    429, 502, 503, 504 -> RATE_LIMIT_COOLDOWN_MS
                    else -> TRANSIENT_ERROR_COOLDOWN_MS
                }
                val health = when (payload.statusCode) {
                    401, 403 -> ApiKeyHealth.AUTH_ERROR
                    429, 502, 503, 504 -> ApiKeyHealth.COOLDOWN
                    else -> ApiKeyHealth.ERROR
                }
                val errText = payload.errorMessage ?: "HTTP ${payload.statusCode}"
                keyStore.recordKeyResult(
                    id = entry.id,
                    health = health,
                    latencyMs = elapsed,
                    error = errText,
                    cooldownUntilMs = System.currentTimeMillis() + cooldownMs
                )
                errors.add("${entry.label} (${payload.statusCode})")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    keyStore.recordKeyResult(
                        id = entry.id,
                        health = ApiKeyHealth.COOLDOWN,
                        error = "Timed out",
                        cooldownUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
                    )
                    errors.add("${entry.label} (timeout)")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                val msg = sanitizeError(e.message)
                keyStore.recordKeyResult(
                    id = entry.id,
                    health = ApiKeyHealth.ERROR,
                    error = msg,
                    cooldownUntilMs = System.currentTimeMillis() + TRANSIENT_ERROR_COOLDOWN_MS
                )
                errors.add("${entry.label} ($msg)")
            }
        }
        return null
    }

    internal fun buildRoundRobinOrder(enabled: List<ApiKeyEntry>, nowMs: Long): List<ApiKeyEntry> {
        if (enabled.isEmpty()) return emptyList()
        val start = Math.floorMod(roundRobinCursor.getAndIncrement(), enabled.size)
        val rotated = (0 until enabled.size).map { offset -> enabled[(start + offset) % enabled.size] }
        val healthy = rotated.filter { !it.isInCooldown(nowMs) }
        val cooling = rotated.filter { it.isInCooldown(nowMs) }.sortedBy { it.cooldownUntilMs }
        // Try all healthy keys in round-robin order; only fall back to cooling non-auth keys if zero healthy remain
        return if (healthy.isNotEmpty()) healthy else cooling.filter { it.health != ApiKeyHealth.AUTH_ERROR }
    }

    private suspend fun tryFirebaseGeminiChain(
        fullPrompt: String,
        expectJson: Boolean,
        schema: Schema?,
        priorErrors: MutableList<String>
    ): AiRouteResult? {
        val models = listOf(
            PRIMARY_GEMINI_MODEL to EngineTier.FIREBASE_GEMINI_PRIMARY,
            LITE_GEMINI_MODEL to EngineTier.FIREBASE_GEMINI_LITE
        )
        for ((idx, pair) in models.withIndex()) {
            val (modelName, tier) = pair
            val startMs = System.currentTimeMillis()
            try {
                val text = withTimeout(45_000L) {
                    if (firebaseTextTransport != null) {
                        firebaseTextTransport.invoke(modelName, fullPrompt, schema)
                    } else {
                        val model = if (expectJson && schema != null) {
                            Firebase.ai.generativeModel(
                                modelName = modelName,
                                generationConfig = generationConfig {
                                    responseMimeType = "application/json"
                                    responseSchema = schema
                                    temperature = 0.1f
                                }
                            )
                        } else {
                            if (idx == 0) primaryGeminiModel else liteGeminiModel
                        }
                        model.generateContent(fullPrompt).text.orEmpty()
                    }
                }.trim()

                if (text.isNotBlank()) {
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val note = when {
                        idx > 0 -> "Auto-switched to $modelName (primary busy/rate-limited)"
                        priorErrors.isNotEmpty() -> "Fallback to built-in $modelName after BYOK retry"
                        else -> null
                    }
                    return AiRouteResult(
                        text = text,
                        engineLabel = if (idx == 0) "Gemini 3.8 Flash" else "Gemini 3.5 Flash-Lite",
                        tier = tier,
                        latencyMs = elapsed,
                        fallbackNote = note
                    )
                }
                priorErrors.add("$modelName empty response")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "$modelName timed out; trying lower Gemini tier")
                    priorErrors.add("$modelName timeout")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                val msg = sanitizeError(e.message)
                Log.w(TAG, "$modelName failed: $msg")
                priorErrors.add("$modelName ($msg)")
                // Continue automatically to LITE_GEMINI_MODEL on 429/503/quota/demand/network error
            }
        }
        return null
    }

    private suspend fun executeOpenAiCompatibleCall(
        entry: ApiKeyEntry,
        systemPrompt: String,
        userPrompt: String,
        expectJson: Boolean
    ): HttpResponsePayload {
        if (openAiTransport != null) {
            return openAiTransport.invoke(entry, systemPrompt, userPrompt, expectJson)
        }
        return withContext(Dispatchers.IO) {
            val url = entry.baseUrl.trim()
            require(url.startsWith("https://", ignoreCase = true)) {
                "Only HTTPS endpoints are permitted."
            }
            val payload = JSONObject().apply {
                put("model", entry.modelName.trim())
                put("temperature", 0.1)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    }
                )
                if (expectJson && entry.provider != AiProviderType.CUSTOM) {
                    put("response_format", JSONObject().apply { put("type", "json_object") })
                }
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${entry.apiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

            if (entry.provider == AiProviderType.OPENROUTER) {
                reqBuilder.header("HTTP-Referer", "https://splitflow.app")
                reqBuilder.header("X-Title", "SplitFlow Android")
            }

            withTimeout(30_000L) {
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val rawBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = extractOpenAiErrorMessage(rawBody) ?: resp.message
                        HttpResponsePayload(resp.code, "", errMsg)
                    } else {
                        val contentText = extractOpenAiContent(rawBody)
                        HttpResponsePayload(resp.code, contentText, null)
                    }
                }
            }
        }
    }

    private fun extractOpenAiContent(rawBody: String): String {
        val root = JSONObject(rawBody)
        val choices = root.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val first = choices.getJSONObject(0)
        val msg = first.optJSONObject("message")
        val content = msg?.optString("content")?.trim().orEmpty()
        return stripMarkdownFences(content)
    }

    private fun extractOpenAiErrorMessage(rawBody: String): String? = runCatching {
        val root = JSONObject(rawBody)
        root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()?.take(120)

    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun sanitizeError(raw: String?): String {
        val clean = raw.orEmpty().replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE), "Bearer ***")
        return clean.take(100).ifBlank { "Request failed" }
    }

    companion object {
        private const val TAG = "MultiProviderAiRouter"
        const val PRIMARY_GEMINI_MODEL = "gemini-3.8-flash"
        const val LITE_GEMINI_MODEL = "gemini-3.5-flash-lite"
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        private const val TRANSIENT_ERROR_COOLDOWN_MS = 20_000L
        private const val AUTH_ERROR_COOLDOWN_MS = 300_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
