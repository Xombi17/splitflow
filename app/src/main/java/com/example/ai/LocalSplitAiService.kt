package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.ExpenseGroup
import com.example.model.LedgerMember
import com.example.model.ParsedReceipt
import com.example.model.ParsedVoiceParticipant
import com.example.model.ParsedVoiceSplit
import com.example.model.SpendCoachInsight
import com.example.model.SpendCoachReport
import com.example.model.SplitType
import com.example.model.TripStoryNarrative
import com.example.model.VoiceActionType
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * Hybrid Edge-First + Multi-Provider Cloud AI service for SplitFlow:
 * - Tier 1 (Instant On-Device): Native SpeechRecognizer + LocalVoiceParser + ML Kit Spatial OCR.
 * - Tier 2 (Resilient Cloud Failover): User BYOK Round-Robin Pool (Groq, OpenRouter, Cerebras,
 *   Mistral, Google AI Studio, Custom HTTPS) <-> Firebase AI Logic (gemini-3.8-flash -> gemini-3.5-flash-lite).
 * - Tier 3 (Offline Resilience): Deterministic local financial analytics & parsers.
 */
class LocalSplitAiService(
    private val appContext: Context
) {
    val keyStore = AiProviderKeyStore(appContext)
    val router = MultiProviderAiRouter(keyStore)
    val routerStatus: StateFlow<AiRouterStatus> = router.status

    private val voiceCommandSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "actionType" to Schema.enumeration(listOf("DIRECT_IOU", "SHARED_EXPENSE", "SETTLEMENT")),
                "title" to Schema.string("Short clear title of the expense or IOU"),
                "totalAmount" to Schema.double("Total amount in major currency units"),
                "currency" to Schema.enumeration(listOf("INR", "USD", "EUR", "GBP", "THB")),
                "category" to Schema.enumeration(
                    listOf("FOOD", "TRAVEL", "STAY", "GROCERIES", "UTILITIES", "ENTERTAINMENT", "SHOPPING", "OTHER")
                ),
                "splitType" to Schema.enumeration(listOf("EQUAL", "EXACT", "PERCENTAGE", "SHARES", "ITEMIZED")),
                "paidByName" to Schema.string("Name of the person who paid, or 'SELF' if the user paid"),
                "participants" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "name" to Schema.string("Participant name, or 'SELF'"),
                            "amount" to Schema.double("Participant owed amount in major units"),
                            "percentage" to Schema.double("Percentage share 0-100")
                        )
                    ),
                    description = "For DIRECT_IOU where Friend owes user, include ONLY Friend with full amount. Never split 2-way for DIRECT_IOU."
                ),
                "assistantReply" to Schema.string("Friendly 1-sentence spoken confirmation"),
                "ledgerSummaryLine" to Schema.string("Concise ledger summary e.g. 'Direct IOU · Tanmay owes you ₹500'"),
                "settlementFromName" to Schema.string("Payer name for SETTLEMENT, or 'SELF'", nullable = true),
                "settlementToName" to Schema.string("Receiver name for SETTLEMENT, or 'SELF'", nullable = true)
            ),
            optionalProperties = listOf("settlementFromName", "settlementToName")
        )
    }

    suspend fun analyzeReceipt(bitmap: Bitmap, preferCloudAi: Boolean = false): ParsedReceipt {
        return LocalReceiptOcrEngine.analyzeReceiptBitmap(
            context = appContext,
            bitmap = bitmap,
            router = router,
            preferCloudAi = preferCloudAi
        )
    }

    suspend fun parseVoiceCommand(
        transcript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup> = emptyList(),
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String? = null,
        fromDeviceSpeech: Boolean = true,
        locale: SplitFlowLocale = SplitFlowLocale.EN_IN
    ): ParsedVoiceSplit = withContext(Dispatchers.Default) {
        val cleaned = transcript.trim()
        require(cleaned.isNotBlank()) { "Please speak or type a command first." }

        val shouldEscalate = LocalVoiceParser.shouldEscalateToCloudAi(cleaned)
        val cloudAllowed = keyStore.routingStrategy.value != RoutingStrategy.ON_DEVICE_ONLY

        // Tier 1: Fast deterministic on-device parsing for crisp English/Hinglish commands
        if (!shouldEscalate) {
            val localAttempt = runCatching {
                LocalVoiceParser.parse(
                    rawInput = cleaned,
                    members = members,
                    groups = groups,
                    defaultCurrency = defaultCurrency,
                    engineLabel = if (fromDeviceSpeech) "On-Device Edge Speech" else "On-Device Edge Parser"
                )
            }.getOrNull()
            if (localAttempt != null) {
                router.recordVoiceRun(
                    label = if (fromDeviceSpeech) "On-Device Edge Speech" else "On-Device Edge Parser",
                    tier = EngineTier.ON_DEVICE_EDGE,
                    detail = "Instant local parse (${locale.displayName})"
                )
                return@withContext localAttempt
            }
        }

        // Tier 2: Resilient Multi-Provider Cloud AI (BYOK Round-Robin <-> Gemini 3.8 Flash -> 3.5 Flash-Lite)
        if (cloudAllowed) {
            try {
                val cloudParsed = parseVoiceWithCloudAi(
                    transcript = cleaned,
                    members = members,
                    groups = groups,
                    defaultCurrency = defaultCurrency,
                    groupMemoryNotes = groupMemoryNotes,
                    locale = locale
                )
                return@withContext cloudParsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI voice parse failed; attempting local fallback: ${e.message}")
                val fallback = runCatching {
                    LocalVoiceParser.parse(
                        rawInput = cleaned,
                        members = members,
                        groups = groups,
                        defaultCurrency = defaultCurrency,
                        engineLabel = "On-Device Edge (Offline Fallback)"
                    )
                }.getOrNull()
                if (fallback != null) {
                    router.recordVoiceRun(
                        label = "On-Device Edge",
                        tier = EngineTier.ON_DEVICE_EDGE,
                        detail = "Handled locally while cloud providers unavailable",
                        notice = e.localizedMessage?.take(90)
                    )
                    return@withContext fallback
                }
                throw e
            }
        }

        // Pure on-device mode final attempt
        val localFinal = LocalVoiceParser.parse(
            rawInput = cleaned,
            members = members,
            groups = groups,
            defaultCurrency = defaultCurrency,
            engineLabel = "On-Device Edge"
        )
        router.recordVoiceRun(
            label = "On-Device Edge",
            tier = EngineTier.ON_DEVICE_EDGE,
            detail = "100% On-Device Edge mode"
        )
        localFinal
    }

    private suspend fun parseVoiceWithCloudAi(
        transcript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String?,
        locale: SplitFlowLocale
    ): ParsedVoiceSplit {
        val selfMember = members.firstOrNull { it.selfProfile } ?: members.firstOrNull()
        val selfId = selfMember?.id.orEmpty()
        val selfName = selfMember?.name ?: "You"
        val friendNames = members.filter { !it.selfProfile }.map { it.name }.joinToString(", ").ifBlank { "None yet" }
        val groupNames = groups.map { it.name }.joinToString(", ").ifBlank { "None" }

        val systemPrompt = """
            You are SplitFlow's multilingual Indian expense-splitting parser (supports English, Hinglish, Hindi, Marathi, Tamil).
            Return STRICT JSON matching the schema with keys:
            actionType ("DIRECT_IOU" | "SHARED_EXPENSE" | "SETTLEMENT"), title, totalAmount, currency, category, splitType ("EQUAL" | "EXACT" | "PERCENTAGE" | "SHARES" | "ITEMIZED"), paidByName ("SELF" or friend name), participants ([{name, amount, percentage}]), assistantReply, ledgerSummaryLine, settlementFromName, settlementToName.
            CRITICAL RULES:
            1. If someone owes the user money ("Tanmay owes me 500", "Tanmay se 500 lena hai", "I lent Tanmay 500"), actionType MUST be "DIRECT_IOU", splitType MUST be "EXACT", paidByName="SELF", and participants MUST contain ONLY [{"name":"Tanmay","amount":500,"percentage":100}]. NEVER split 2-way!
            2. If the user owes someone ("I owe Tanmay 500", "Tanmay ko 500 dena hai"), actionType="DIRECT_IOU", splitType="EXACT", paidByName="Tanmay", participants=[{"name":"SELF","amount":500,"percentage":100}].
            3. If someone paid back / settled ("Tanmay paid me back 500"), actionType="SETTLEMENT", settlementFromName="Tanmay", settlementToName="SELF".
            4. For percentage splits ("70 30", "60% me 40% Riya"), use splitType="PERCENTAGE" and exact participant amounts summing to totalAmount.
            5. assistantReply should be short and natural in ${locale.displayName}.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Command: \"$transcript\"")
            appendLine("Default currency: ${defaultCurrency.name}")
            appendLine("Known friends: $friendNames")
            appendLine("Groups: $groupNames")
            if (!groupMemoryNotes.isNullOrBlank()) appendLine("Group notes: $groupMemoryNotes")
        }

        val routed = router.generateStructuredOrText(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            expectJson = true,
            firebaseSchema = voiceCommandSchema
        )
        val parsed = parseCloudVoiceJson(
            jsonString = routed.text,
            transcript = transcript,
            members = members,
            groups = groups,
            defaultCurrency = defaultCurrency,
            selfId = selfId,
            selfName = selfName,
            engineLabel = routed.engineLabel
        )
        router.recordVoiceRun(
            label = routed.engineLabel,
            tier = routed.tier,
            detail = "Parsed in ${routed.latencyMs}ms (${locale.shortBadge})",
            notice = routed.fallbackNote
        )
        return parsed
    }

    internal fun parseCloudVoiceJson(
        jsonString: String,
        transcript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode,
        selfId: String,
        selfName: String,
        engineLabel: String
    ): ParsedVoiceSplit {
        val obj = JSONObject(jsonString)
        val actionType = VoiceActionType.fromRaw(obj.optString("actionType", "SHARED_EXPENSE"))
        val currency = CurrencyCode.fromRaw(obj.optString("currency", defaultCurrency.name))
        val category = ExpenseCategory.fromRaw(obj.optString("category", "OTHER"))
        val totalMajor = obj.optDouble("totalAmount", 0.0)
        require(totalMajor > 0.0) { "Could not detect an amount from \"$transcript\"." }
        val totalMinor = (totalMajor * 100.0).roundToLong().coerceAtLeast(100L)
        val splitType = SplitType.fromRaw(obj.optString("splitType", if (actionType == VoiceActionType.DIRECT_IOU) "EXACT" else "EQUAL"))
        val title = obj.optString("title").trim().ifBlank { "Voice Expense" }

        val pendingNew = linkedSetOf<String>()
        fun resolveName(raw: String?): Pair<String, String> {
            val cleaned = raw?.trim().orEmpty()
            if (cleaned.isBlank() ||
                cleaned.equals("SELF", ignoreCase = true) ||
                cleaned.equals("ME", ignoreCase = true) ||
                cleaned.equals("YOU", ignoreCase = true) ||
                cleaned.equals(selfName, ignoreCase = true)
            ) {
                return selfId to selfName
            }
            val found = members.firstOrNull { m ->
                m.name.equals(cleaned, ignoreCase = true) ||
                    m.name.split(" ").firstOrNull().equals(cleaned, ignoreCase = true)
            }
            if (found != null) return found.id to found.name
            val pretty = cleaned.split(Regex("\\s+")).joinToString(" ") { p ->
                p.lowercase().replaceFirstChar { it.uppercase() }
            }
            pendingNew.add(pretty)
            return "NEW:$pretty" to pretty
        }

        val (paidById, paidByName) = resolveName(obj.optString("paidByName", "SELF"))
        val pArr = obj.optJSONArray("participants")
        val participants = mutableListOf<ParsedVoiceParticipant>()
        if (pArr != null) {
            for (i in 0 until pArr.length()) {
                val pObj = pArr.optJSONObject(i) ?: continue
                val (pid, pname) = resolveName(pObj.optString("name"))
                val amtMinor = (pObj.optDouble("amount", 0.0) * 100.0).roundToLong()
                val pctBps = (pObj.optDouble("percentage", 0.0) * 100.0).roundToLong()
                if (pid.isNotBlank() && amtMinor > 0L) {
                    participants.add(
                        ParsedVoiceParticipant(
                            memberId = pid,
                            memberName = pname,
                            amountMinorUnits = amtMinor,
                            shares = 1L,
                            percentageBps = if (pctBps > 0L) pctBps else ((amtMinor * 10_000L) / totalMinor)
                        )
                    )
                }
            }
        }

        // Enforce invariant: DIRECT_IOU must NEVER accidentally include the payer as a 2-way split
        val sanitizedParticipants = if (actionType == VoiceActionType.DIRECT_IOU && participants.size > 1) {
            val nonPayer = participants.firstOrNull { it.memberId != paidById }
            if (nonPayer != null) listOf(nonPayer.copy(amountMinorUnits = totalMinor, percentageBps = 10_000L))
            else participants
        } else if (participants.isEmpty()) {
            listOf(ParsedVoiceParticipant(selfId, selfName, totalMinor, 1L, 10_000L))
        } else {
            participants
        }

        val (fromId, fromName) = obj.optString("settlementFromName").takeIf { it.isNotBlank() && it != "null" }
            ?.let { resolveName(it) } ?: (null to null)
        val (toId, toName) = obj.optString("settlementToName").takeIf { it.isNotBlank() && it != "null" }
            ?.let { resolveName(it) } ?: (null to null)

        val matchedGroup = groups.firstOrNull { g ->
            g.name.length >= 3 && transcript.contains(g.name, ignoreCase = true)
        }
        val amtFormatted = FinanceEngine.formatMinorUnits(totalMinor, currency)
        val summary = obj.optString("ledgerSummaryLine").trim().ifBlank { "$title · $amtFormatted" }
        val reply = obj.optString("assistantReply").trim().ifBlank { "Done! Logged $summary." }

        return ParsedVoiceSplit(
            transcript = transcript,
            actionType = actionType,
            title = title,
            totalMinorUnits = totalMinor,
            currency = currency,
            paidByMemberId = paidById,
            paidByMemberName = paidByName,
            category = category,
            splitType = if (actionType == VoiceActionType.DIRECT_IOU) SplitType.EXACT else splitType,
            participants = sanitizedParticipants,
            aiConfidenceNote = "Parsed via $engineLabel",
            matchedGroupId = matchedGroup?.id,
            newMemberNames = pendingNew.toList(),
            assistantReply = reply,
            ledgerSummaryLine = summary,
            settlementFromMemberId = fromId,
            settlementFromMemberName = fromName,
            settlementToMemberId = toId,
            settlementToMemberName = toName
        )
    }

    suspend fun generateMonthlySpendCoach(ledgerDigest: String): SpendCoachReport =
        withContext(Dispatchers.Default) {
            if (keyStore.routingStrategy.value == RoutingStrategy.ON_DEVICE_ONLY) {
                return@withContext LocalFinanceInsightsEngine.generateSpendCoach(ledgerDigest)
            }
            runCatching {
                val schema = Schema.obj(
                    properties = mapOf(
                        "healthScore" to Schema.integer("Score from 40 to 99"),
                        "headline" to Schema.string("Short crisp insight headline"),
                        "budgetAlertNote" to Schema.string("1-sentence actionable budget guidance"),
                        "insights" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "title" to Schema.string(),
                                    "detail" to Schema.string(),
                                    "potentialSavingsLabel" to Schema.string(),
                                    "category" to Schema.string()
                                )
                            )
                        )
                    )
                )
                val res = router.generateStructuredOrText(
                    systemPrompt = "You are SplitFlow's personal spend coach. Return strict JSON with healthScore (int 40-99), headline, budgetAlertNote, and 3 concise insights[{title,detail,potentialSavingsLabel,category}].",
                    userPrompt = ledgerDigest,
                    expectJson = true,
                    firebaseSchema = schema
                )
                val obj = JSONObject(res.text)
                val arr = obj.optJSONArray("insights")
                val insights = buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            add(
                                SpendCoachInsight(
                                    title = item.optString("title", "Spend Insight"),
                                    detail = item.optString("detail", ""),
                                    potentialSavingsLabel = item.optString("potentialSavingsLabel", "Smart habit"),
                                    category = item.optString("category", "General")
                                )
                            )
                        }
                    }
                }
                SpendCoachReport(
                    healthScore = obj.optInt("healthScore", 78).coerceIn(40, 99),
                    headline = obj.optString("headline").ifBlank { "Your Ledger Snapshot (${res.engineLabel})" },
                    budgetAlertNote = obj.optString("budgetAlertNote").ifBlank { "Analyzed via ${res.engineLabel}" },
                    insights = insights.ifEmpty { LocalFinanceInsightsEngine.generateSpendCoach(ledgerDigest).insights }
                )
            }.getOrElse {
                LocalFinanceInsightsEngine.generateSpendCoach(ledgerDigest)
            }
        }

    fun streamCoachFollowUp(ledgerDigest: String, question: String): Flow<String> {
        if (keyStore.routingStrategy.value == RoutingStrategy.ON_DEVICE_ONLY) {
            return LocalFinanceInsightsEngine.streamCoachFollowUp(ledgerDigest, question)
        }
        return router.streamCoachingAnswer(
            systemPrompt = "You are SplitFlow's concise financial assistant. Answer using only the ledger digest below in 2-4 crisp bullet points.",
            userPrompt = "LEDGER:\n$ledgerDigest\n\nQUESTION: $question"
        ).catch {
            emitAll(LocalFinanceInsightsEngine.streamCoachFollowUp(ledgerDigest, question))
        }
    }

    suspend fun generateTripStory(metricsDigest: String): TripStoryNarrative =
        withContext(Dispatchers.Default) {
            if (keyStore.routingStrategy.value == RoutingStrategy.ON_DEVICE_ONLY) {
                return@withContext LocalFinanceInsightsEngine.generateTripStory(metricsDigest)
            }
            runCatching {
                val schema = Schema.obj(
                    properties = mapOf(
                        "headline" to Schema.string("Fun trip headline"),
                        "vibeTag" to Schema.string("2-3 word vibe label"),
                        "summaryParagraph" to Schema.string("2-sentence story wrap-up"),
                        "highlights" to Schema.array(Schema.string("3 playful group highlights or awards")),
                        "closingQuip" to Schema.string("Short warm closing quip or WhatsApp caption")
                    )
                )
                val res = router.generateStructuredOrText(
                    systemPrompt = "Create a fun, warm JSON trip wrap-up story with keys: headline, vibeTag, highlights (array of 3 strings), closingQuip.",
                    userPrompt = metricsDigest,
                    expectJson = true,
                    firebaseSchema = schema
                )
                val obj = JSONObject(res.text)
                val arr = obj.optJSONArray("highlights")
                val highlights = buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                TripStoryNarrative(
                    headline = obj.optString("headline").ifBlank { "Trip Wrap-Up" },
                    vibeTag = obj.optString("vibeTag").ifBlank { "Crew Highlights" },
                    highlights = highlights.ifEmpty { listOf("Top Host", "Fair Splitter", "Zero Drama") },
                    closingQuip = obj.optString("closingQuip").ifBlank { "Split cleanly with SplitFlow!" }
                )
            }.getOrElse {
                LocalFinanceInsightsEngine.generateTripStory(metricsDigest)
            }
        }

    companion object {
        private const val TAG = "LocalSplitAiService"
    }
}
