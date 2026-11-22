package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.ExpenseGroup
import com.example.model.LedgerMember
import com.example.model.LineItemTag
import com.example.model.ParsedReceipt
import com.example.model.ParsedVoiceParticipant
import com.example.model.ParsedVoiceSplit
import com.example.model.ReceiptLineItem
import com.example.model.SpendCoachInsight
import com.example.model.SpendCoachReport
import com.example.model.SplitType
import com.example.model.TripStoryNarrative
import com.example.model.VoiceActionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.roundToLong

private const val TAG = "GeminiSplitService"

class GeminiSplitService {

    private val receiptSchema = Schema.obj(
        properties = mapOf(
            "merchantName" to Schema.string(description = "Name of the restaurant, store, or merchant"),
            "date" to Schema.string(description = "Receipt date in YYYY-MM-DD format"),
            "currency" to Schema.enumeration(
                values = listOf("INR", "USD", "EUR", "GBP", "THB"),
                description = "Three-letter currency code detected on the receipt"
            ),
            "category" to Schema.enumeration(
                values = listOf("FOOD", "TRAVEL", "STAY", "GROCERIES", "UTILITIES", "ENTERTAINMENT", "SHOPPING", "OTHER"),
                description = "Best matching expense category"
            ),
            "lineItems" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Item name"),
                        "quantity" to Schema.integer(description = "Item quantity"),
                        "unitPrice" to Schema.double(description = "Price per single unit in major currency units"),
                        "totalPrice" to Schema.double(description = "Total line price in major currency units"),
                        "itemTag" to Schema.enumeration(
                            values = listOf("VEG", "NON_VEG", "ALCOHOL", "BEER", "MOCKTAIL", "DESSERT", "HOOKAH_EXTRA", "SHARED"),
                            description = "Dietary or drink classification: VEG (vegetarian food/breads/paneer/mushroom), NON_VEG (chicken/mutton/prawn/fish/calamari/seafood/egg), ALCOHOL (cocktails/whiskey/gin/vodka/wine/shots), BEER (beer pints/pitchers/towers), MOCKTAIL (mojito/virgin drinks/soda/coffee/juice), DESSERT (ice cream/tiramisu/cake), HOOKAH_EXTRA (hookah/shisha/cover charge), SHARED (non-food/general)"
                        )
                    ),
                    optionalProperties = listOf("itemTag")
                ),
                description = "All itemized line items on the bill"
            ),
            "subtotal" to Schema.double(description = "Pre-tax subtotal in major currency units"),
            "taxAmount" to Schema.double(description = "Total tax (CGST + SGST / VAT / Sales Tax) in major currency units"),
            "serviceCharge" to Schema.double(description = "Service charge or tip in major currency units"),
            "discountAmount" to Schema.double(description = "Total discount subtracted from bill in major currency units"),
            "grandTotal" to Schema.double(description = "Final grand total payable in major currency units")
        ),
        optionalProperties = listOf("date", "serviceCharge", "discountAmount")
    )

    private val voiceSplitSchema = Schema.obj(
        properties = mapOf(
            "transcript" to Schema.string(description = "Exact verbatim clean transcription of what the user said (fix phonetic Indian/global names using the provided member roster)"),
            "actionType" to Schema.enumeration(
                values = listOf("DIRECT_IOU", "SHARED_EXPENSE", "SETTLEMENT"),
                description = "DIRECT_IOU when one person owes another directly (e.g. 'Tanmay owes me 500', 'I lent Tanmay 500', 'I owe Tanmay 500'); SETTLEMENT when someone paid back/settled an existing debt (e.g. 'Tanmay paid me back 500', 'I settled 300 with Tanmay'); SHARED_EXPENSE for multi-person bills or splits"
            ),
            "title" to Schema.string(description = "Short, clear activity title (e.g. 'Tanmay owes you', 'Dinner with Tanmay', 'Airport Cab', 'Settlement with Tanmay')"),
            "totalAmount" to Schema.double(description = "Total amount in major currency units (e.g. 500.0 for 500 rupees)"),
            "currency" to Schema.enumeration(
                values = listOf("INR", "USD", "EUR", "GBP", "THB"),
                description = "Currency code (rupees/rs/inr -> INR, dollars -> USD, euros -> EUR, pounds -> GBP, baht -> THB)"
            ),
            "paidByMemberId" to Schema.string(description = "Exact memberId from roster who paid (or 'NEW:<Name>' if that person is not yet in the roster). For 'Tanmay owes me 500', THE SPEAKER paid (so use speaker's memberId). For 'I owe Tanmay 500', Tanmay paid (so use Tanmay's memberId or 'NEW:Tanmay')."),
            "paidByMemberName" to Schema.string(description = "Display name of who paid"),
            "category" to Schema.enumeration(
                values = listOf("FOOD", "TRAVEL", "STAY", "GROCERIES", "UTILITIES", "ENTERTAINMENT", "SHOPPING", "OTHER"),
                description = "Best matching expense category"
            ),
            "splitType" to Schema.enumeration(
                values = listOf("EQUAL", "EXACT", "PERCENTAGE", "SHARES", "ITEMIZED"),
                description = "Use EXACT for DIRECT_IOU or unequal amounts, EQUAL for equal splits"
            ),
            "matchedGroupId" to Schema.string(
                description = "Exact groupId from the provided groups list ONLY if the user explicitly named a group (e.g. 'for Goa trip'); otherwise empty string for Personal/1-on-1",
                nullable = true
            ),
            "participants" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "memberId" to Schema.string(description = "Exact memberId from roster OR 'NEW:<TitleCasedName>' if the person is not yet in the roster"),
                        "memberName" to Schema.string(description = "Clean Title-Cased display name (e.g. 'Tanmay')"),
                        "amount" to Schema.double(description = "Exact share owed by this participant in major currency units; sum MUST equal totalAmount. For 'Tanmay owes me 500', ONLY include Tanmay with amount 500.0 (speaker's share is 0). For 'I owe Tanmay 500', ONLY include speaker with amount 500.0."),
                        "shares" to Schema.integer(description = "Integer weight/share count (default 1)"),
                        "percentage" to Schema.double(description = "Percentage share 0-100")
                    )
                ),
                description = "Participants who owe a share of this expense, summing strictly to totalAmount"
            ),
            "newMemberNames" to Schema.array(
                items = Schema.string(description = "Clean Title-Cased name of any mentioned person who is not in the existing roster"),
                description = "Names of any new friends that should be automatically added to the user's ledger"
            ),
            "settlementFromMemberId" to Schema.string(description = "For SETTLEMENT only: memberId (or 'NEW:<Name>') of the person who paid back money", nullable = true),
            "settlementFromMemberName" to Schema.string(description = "For SETTLEMENT only: display name of who paid back", nullable = true),
            "settlementToMemberId" to Schema.string(description = "For SETTLEMENT only: memberId (or 'NEW:<Name>') of the person who received the payment", nullable = true),
            "settlementToMemberName" to Schema.string(description = "For SETTLEMENT only: display name of who received the payment", nullable = true),
            "ledgerSummaryLine" to Schema.string(description = "Ultra-clear human summary of account impact, e.g. 'Tanmay owes you ₹500', 'You owe Tanmay ₹500', or 'Tanmay paid you ₹500'"),
            "assistantReply" to Schema.string(description = "Natural, concise 1-sentence AI voice assistant confirmation ready to be spoken aloud, e.g. 'Done! I logged 500 rupees so Tanmay owes you 500 rupees in your Activity.'"),
            "aiConfidenceNote" to Schema.string(description = "One concise sentence explaining how the split math was computed")
        ),
        optionalProperties = listOf(
            "matchedGroupId",
            "newMemberNames",
            "settlementFromMemberId",
            "settlementFromMemberName",
            "settlementToMemberId",
            "settlementToMemberName"
        )
    )

    private val spendCoachSchema = Schema.obj(
        properties = mapOf(
            "healthScore" to Schema.integer(description = "Overall spending health score from 0 to 100"),
            "headline" to Schema.string(description = "Concise 1-sentence executive summary of spending health and top driver"),
            "budgetAlertNote" to Schema.string(description = "Specific callout on any group nearing/exceeding budget or upcoming recurring bills"),
            "insights" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Short punchy insight title (3-6 words)"),
                        "detail" to Schema.string(description = "Actionable 1-2 sentence advice grounded in actual figures"),
                        "potentialSavingsLabel" to Schema.string(description = "Short savings or impact tag, e.g. 'Save ~₹2,400/mo' or 'Settle 2 transfers'"),
                        "category" to Schema.string(description = "Category or scope label, e.g. 'Dining', 'Goa Trip', 'Recurring'")
                    )
                ),
                description = "Exactly 3 specific, actionable financial coaching cards"
            )
        )
    )

    private val receiptModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = receiptSchema
            }
        )
    }

    private val voiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceSplitSchema
            }
        )
    }

    private val tripStorySchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Punchy editorial 1-line wrap-up title (5-10 words)"),
            "vibeTag" to Schema.string(description = "Short 2-3 word badge e.g. 'Coastal Splurge' or 'Budget Precision'"),
            "highlights" to Schema.array(
                items = Schema.string(description = "Concise 1-sentence highlight grounded in the exact numbers"),
                description = "Exactly 3 sharp highlights covering top spender, category driver, and settlement efficiency"
            ),
            "closingQuip" to Schema.string(description = "Warm, witty 1-sentence sign-off ready to share with the group")
        )
    )

    private val spendCoachModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = spendCoachSchema
            }
        )
    }

    private val tripStoryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tripStorySchema
            }
        )
    }

    private val coachStreamModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun analyzeReceipt(bitmap: Bitmap): ParsedReceipt {
        val scaledBitmap = scaleBitmapForVision(bitmap, maxDim = 1400)
        return try {
            val response = withTimeout(60_000L) {
                receiptModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(
                            "Extract every line item, tax (combine CGST + SGST into taxAmount), service charge/tip, " +
                                "discount, subtotal, and grand total from this receipt accurately. " +
                                "Also tag each line item with itemTag (VEG, NON_VEG, ALCOHOL, BEER, MOCKTAIL, DESSERT, HOOKAH_EXTRA, or SHARED). " +
                                "Use major currency units (e.g. 680.00 for Rs. 680.00). " +
                                "Return strict JSON matching the schema."
                        )
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini returned an empty receipt response.")
            parseReceiptJson(jsonText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Receipt analysis timed out", e)
            throw IllegalStateException("Receipt scan timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt analysis failed", e)
            throw e
        }
    }

    private fun buildVoiceAssistantPrompt(
        transcriptOrAudioHint: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String?
    ): String {
        val roster = members.joinToString("\n") { m ->
            val dietInfo = "diet=${m.dietEnum.name}, drinksAlcohol=${m.isAlcoholDrinker}"
            "- memberId: \"${m.id}\", name: \"${m.name}\" ($dietInfo)${if (m.selfProfile) " (THE SPEAKER / 'I' / 'me')" else ""}"
        }
        val groupRoster = if (groups.isEmpty()) {
            "(No groups yet — use Personal / null matchedGroupId)"
        } else {
            groups.joinToString("\n") { g ->
                "- groupId: \"${g.id}\", name: \"${g.name}\" (${g.categoryEnum.label}, currency=${g.currencyEnum.name})"
            }
        }
        val memorySection = if (!groupMemoryNotes.isNullOrBlank()) {
            "\n            Saved Group Split Memory (apply whenever relevant):\n            \"$groupMemoryNotes\"\n"
        } else ""
        return """
            You are SplitFlow's intelligent AI Voice Ledger Assistant.
            Your job is to accurately transcribe (if audio is attached) and convert the user's natural spoken/written command into an exact account mutation.
            
            Existing Friends & Ledger Members:
            $roster
            
            Existing Groups:
            $groupRoster
            $memorySection
            Default Currency: ${defaultCurrency.name} (${defaultCurrency.symbol})
            Input: $transcriptOrAudioHint
            
            CRITICAL RULES:
            1. Accurate Transcription: If audio is provided, transcribe every word accurately into `transcript` (handle Indian & international names, numbers like "500 rupees", "panch sau", Hinglish, and currencies). Match phonetic names to existing roster members when clearly similar.
            2. New / Unlisted Friends (NEVER DROP ANYONE): If the user mentions a person NOT in the roster (e.g. "Tanmay"), set their memberId to "NEW:Tanmay" (using clean Title Case), memberName to "Tanmay", and include "Tanmay" in `newMemberNames`. NEVER drop them or substitute the speaker!
            3. Direct IOUs (`actionType = "DIRECT_IOU"`, `splitType = "EXACT"`):
               - "Tanmay owes me 500 rupees" / "I lent Tanmay 500" / "Tanmay has to pay me 500":
                 -> `paidByMemberId` = speaker's memberId, `participants` = ONLY `[Tanmay with amount = 500.0, percentage = 100.0]`. (Speaker's share is 0 so Tanmay owes speaker the full 500!)
                 -> `title` = reason if given (e.g. "Coffee — Tanmay owes you") or "Tanmay owes you".
                 -> `ledgerSummaryLine` = "Tanmay owes you ₹500".
               - "I owe Tanmay 500 rupees" / "Borrowed 500 from Tanmay" / "Tanmay lent me 500":
                 -> `paidByMemberId` = Tanmay's memberId (or "NEW:Tanmay"), `participants` = ONLY `[speaker with amount = 500.0, percentage = 100.0]`.
                 -> `title` = reason if given or "You owe Tanmay".
                 -> `ledgerSummaryLine` = "You owe Tanmay ₹500".
            4. Settlements (`actionType = "SETTLEMENT"`):
               - "Tanmay paid me back 500 rupees" / "Received 500 from Tanmay":
                 -> `settlementFromMemberId` = Tanmay's id (or "NEW:Tanmay"), `settlementToMemberId` = speaker's id, `totalAmount` = 500.0.
                 -> `ledgerSummaryLine` = "Tanmay paid you ₹500".
               - "I paid Tanmay back 500" / "I settled 500 with Tanmay":
                 -> `settlementFromMemberId` = speaker's id, `settlementToMemberId` = Tanmay's id (or "NEW:Tanmay"), `totalAmount` = 500.0.
                 -> `ledgerSummaryLine` = "You paid Tanmay ₹500".
            5. Shared Splits (`actionType = "SHARED_EXPENSE"`):
               - Handle equal splits, percentages, and exclusions accurately. Example: "3200 for groceries split among 5, except Kabir skipped 600 snacks" -> base 2600/5=520 each; 600/4=150 for the other 4 -> Kabir=520, others=670, sum=3200.
               - The sum of all participants' `amount` values MUST strictly equal `totalAmount`.
            6. Group Matching: Set `matchedGroupId` ONLY if the user explicitly mentions one of the listed groups; otherwise leave it empty/null so personal IOUs stay in Personal Activity.
            7. Friendly Spoken AI Reply: Write a crisp, natural 1-sentence `assistantReply` confirming the exact change made (e.g. "Done! Logged 500 rupees so Tanmay owes you 500 rupees in your Activity.").
        """.trimIndent()
    }

    /**
     * Multimodal Gemini 3.8 Flash voice transcription + ledger action extraction in one single AI call.
     * Receives raw 16kHz WAV audio bytes alongside the user's friend & group rosters for high-accuracy name recognition.
     */
    suspend fun transcribeAndParseVoiceAudio(
        wavAudioBytes: ByteArray,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup> = emptyList(),
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String? = null
    ): ParsedVoiceSplit {
        val prompt = buildVoiceAssistantPrompt(
            transcriptOrAudioHint = "[Attached voice recording — transcribe verbatim into `transcript` and parse the financial command]",
            members = members,
            groups = groups,
            defaultCurrency = defaultCurrency,
            groupMemoryNotes = groupMemoryNotes
        )

        return try {
            val response = withTimeout(60_000L) {
                voiceModel.generateContent(
                    content {
                        inlineData(wavAudioBytes, "audio/wav")
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("AI could not understand the recording. Please try speaking again.")
            parseVoiceJson(jsonText, fallbackTranscript = "", members = members, groups = groups, defaultCurrency = defaultCurrency)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice audio transcription timed out", e)
            throw IllegalStateException("AI voice transcription timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice audio transcription failed", e)
            throw e
        }
    }

    suspend fun parseVoiceCommand(
        transcript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup> = emptyList(),
        defaultCurrency: CurrencyCode,
        groupMemoryNotes: String? = null
    ): ParsedVoiceSplit {
        val prompt = buildVoiceAssistantPrompt(
            transcriptOrAudioHint = "\"$transcript\"",
            members = members,
            groups = groups,
            defaultCurrency = defaultCurrency,
            groupMemoryNotes = groupMemoryNotes
        )

        return try {
            val response = withTimeout(60_000L) {
                voiceModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Gemini returned an empty split response.")
            parseVoiceJson(jsonText, fallbackTranscript = transcript, members = members, groups = groups, defaultCurrency = defaultCurrency)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice command parsing timed out", e)
            throw IllegalStateException("AI split parsing timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice command parsing failed", e)
            throw e
        }
    }

    suspend fun generateMonthlySpendCoach(ledgerDigest: String): SpendCoachReport {
        val prompt = """
            You are SplitFlow's sharp, concise Personal & Group Finance Coach.
            Analyze the user's real shared ledger summary below (all numbers already formatted with currency) and provide crisp, practical coaching.
            Avoid generic platitudes—reference the real group names, budgets, recurring bills, and top spend categories from the digest.
            
            Ledger Digest:
            $ledgerDigest
            
            Return strict JSON matching the schema with:
            - healthScore: integer 0..100 reflecting budget discipline and open debt balance
            - headline: crisp 1-sentence verdict
            - budgetAlertNote: concrete callout on group budgets or upcoming recurring bills
            - insights: exactly 3 actionable cards with real numbers.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                spendCoachModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("AI Coach returned an empty response.")
            parseSpendCoachJson(jsonText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Spend Coach timed out", e)
            throw IllegalStateException("AI Spend Coach timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Spend Coach failed", e)
            throw e
        }
    }

    fun streamCoachFollowUp(ledgerDigest: String, question: String): Flow<String> {
        val prompt = """
            You are SplitFlow's crisp financial coach. Answer the user's question in 2-4 concise sentences grounded ONLY in this ledger digest. Use exact currency figures.
            
            Ledger Digest:
            $ledgerDigest
            
            User Question: $question
        """.trimIndent()
        return coachStreamModel.generateContentStream(prompt).map { it.text.orEmpty() }
    }

    suspend fun generateTripStory(metricsDigest: String): TripStoryNarrative {
        val prompt = """
            You are SplitFlow's editorial Ledger Story writer. Craft a sleek, shareable trip/group wrap-up story from the real group statistics below.
            Keep the tone crisp, modern, and grounded strictly in the provided figures (no invented numbers).
            
            Group Metrics Digest:
            $metricsDigest
            
            Return strict JSON matching the schema.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                tripStoryModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("AI Trip Story returned an empty response.")
            parseTripStoryJson(jsonText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Trip Story timed out", e)
            throw IllegalStateException("Trip Story generation timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Trip Story failed", e)
            throw e
        }
    }

    private fun parseTripStoryJson(jsonString: String): TripStoryNarrative {
        val obj = JSONObject(jsonString)
        val headline = obj.optString("headline", "Ledger Wrapped: Clean Splits, Zero Drama")
        val vibeTag = obj.optString("vibeTag", "Trip Wrapped")
        val arr = obj.optJSONArray("highlights")
        val highlights = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val line = arr.optString(i, "").trim()
                    if (line.isNotEmpty()) add(line)
                }
            }
        }
        val quip = obj.optString("closingQuip", "Settle up in one tap and start planning the next one.")
        return TripStoryNarrative(
            headline = headline,
            vibeTag = vibeTag,
            highlights = highlights,
            closingQuip = quip
        )
    }

    private fun parseSpendCoachJson(jsonString: String): SpendCoachReport {
        val obj = JSONObject(jsonString)
        val score = obj.optInt("healthScore", 78).coerceIn(0, 100)
        val headline = obj.optString("headline", "Your shared ledger is active with clear room to optimize dining and recurring bills.")
        val alertNote = obj.optString("budgetAlertNote", "Monitor group budgets before your next weekend outing.")
        val arr = obj.optJSONArray("insights")
        val list = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    add(
                        SpendCoachInsight(
                            title = item.optString("title", "Optimize shared spend"),
                            detail = item.optString("detail", ""),
                            potentialSavingsLabel = item.optString("potentialSavingsLabel", "Actionable"),
                            category = item.optString("category", "General")
                        )
                    )
                }
            }
        }
        return SpendCoachReport(
            healthScore = score,
            headline = headline,
            budgetAlertNote = alertNote,
            insights = list
        )
    }

    private fun parseReceiptJson(jsonString: String): ParsedReceipt {
        val obj = JSONObject(jsonString)
        val merchantName = obj.optString("merchantName", "Scanned Receipt").ifBlank { "Scanned Receipt" }
        val date = obj.optString("date", "2026-11-21").ifBlank { "2026-11-21" }
        val currency = CurrencyCode.fromRaw(obj.optString("currency", "INR"))
        val category = ExpenseCategory.fromRaw(obj.optString("category", "FOOD"))

        val itemsArray = obj.optJSONArray("lineItems")
        val lineItems = mutableListOf<ReceiptLineItem>()
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                val qty = itemObj.optLong("quantity", 1L).coerceAtLeast(1L)
                val totalMinor = (itemObj.optDouble("totalPrice", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                val unitMinor = (itemObj.optDouble("unitPrice", 0.0) * 100.0).roundToLong().let {
                    if (it > 0L) it else if (qty > 0L) totalMinor / qty else totalMinor
                }
                val rawTag = itemObj.optString("itemTag", "").takeIf { it.isNotBlank() }
                val resolvedTag = rawTag?.let { LineItemTag.fromRaw(it).name }
                lineItems.add(
                    ReceiptLineItem(
                        itemId = "ai_item_${i + 1}",
                        name = itemObj.optString("name", "Item ${i + 1}").ifBlank { "Item ${i + 1}" },
                        quantity = qty,
                        unitPriceMinor = unitMinor,
                        totalPriceMinor = totalMinor,
                        assignedMemberIds = emptyList(),
                        itemTag = resolvedTag
                    )
                )
            }
        }

        val itemsSum = lineItems.sumOf { it.totalPriceMinor }
        val subtotalMinor = (obj.optDouble("subtotal", 0.0) * 100.0).roundToLong().let {
            if (it > 0L) it else itemsSum
        }
        val taxMinor = (obj.optDouble("taxAmount", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val serviceMinor = (obj.optDouble("serviceCharge", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val discountMinor = (obj.optDouble("discountAmount", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
        val parsedGrandTotal = (obj.optDouble("grandTotal", 0.0) * 100.0).roundToLong()
        val grandTotalMinor = if (parsedGrandTotal > 0L) {
            parsedGrandTotal
        } else {
            (subtotalMinor + taxMinor + serviceMinor - discountMinor).coerceAtLeast(1L)
        }

        return ParsedReceipt(
            merchantName = merchantName,
            date = date,
            currency = currency,
            category = category,
            lineItems = lineItems,
            subtotalMinor = subtotalMinor,
            taxMinor = taxMinor,
            serviceChargeMinor = serviceMinor,
            discountMinor = discountMinor,
            grandTotalMinor = grandTotalMinor
        )
    }

    private fun cleanDisplayName(raw: String): String {
        val cleaned = raw.removePrefix("NEW:").removePrefix("new:").trim()
        if (cleaned.isBlank()) return ""
        return cleaned.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    private fun parseVoiceJson(
        jsonString: String,
        fallbackTranscript: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode
    ): ParsedVoiceSplit {
        val obj = JSONObject(jsonString)
        val memberById = members.associateBy { it.id }
        val groupById = groups.associateBy { it.id }
        val selfMember = members.firstOrNull { it.selfProfile } ?: members.firstOrNull()
        val pendingNewMembers = linkedSetOf<String>()

        fun resolveMember(rawId: String, rawName: String): Pair<String, String> {
            val idTrim = rawId.trim()
            val nameTrim = rawName.trim()
            if (idTrim.isNotEmpty() && memberById.containsKey(idTrim)) {
                val m = memberById.getValue(idTrim)
                return m.id to m.name
            }
            if (idTrim.equals("self", ignoreCase = true) ||
                idTrim.equals("me", ignoreCase = true) ||
                nameTrim.equals("you", ignoreCase = true) ||
                nameTrim.equals("me", ignoreCase = true)
            ) {
                if (selfMember != null) return selfMember.id to selfMember.name
            }
            val lookupName = cleanDisplayName(nameTrim.ifBlank { idTrim })
            if (lookupName.isNotEmpty()) {
                val matched = members.firstOrNull { m ->
                    m.name.equals(lookupName, ignoreCase = true) ||
                        m.name.split(" ").first().equals(lookupName, ignoreCase = true) ||
                        m.name.contains(lookupName, ignoreCase = true)
                }
                if (matched != null) return matched.id to matched.name
                pendingNewMembers.add(lookupName)
                return "NEW:$lookupName" to lookupName
            }
            val fallbackId = selfMember?.id.orEmpty()
            return fallbackId to (selfMember?.name ?: "You")
        }

        val resolvedTranscript = obj.optString("transcript", "").trim().ifBlank { fallbackTranscript }
        val actionType = VoiceActionType.fromRaw(obj.optString("actionType", "SHARED_EXPENSE"))
        val title = obj.optString("title", "Shared Expense").ifBlank { "Shared Expense" }
        val totalMinor = (obj.optDouble("totalAmount", 0.0) * 100.0).roundToLong().coerceAtLeast(100L)
        val currency = CurrencyCode.fromRaw(obj.optString("currency", defaultCurrency.name))
        val (paidByMemberId, paidByMemberName) = resolveMember(
            obj.optString("paidByMemberId", ""),
            obj.optString("paidByMemberName", "")
        )
        val category = ExpenseCategory.fromRaw(obj.optString("category", "OTHER"))
        val splitType = SplitType.fromRaw(obj.optString("splitType", if (actionType == VoiceActionType.DIRECT_IOU) "EXACT" else "EQUAL"))
        val note = obj.optString("aiConfidenceNote", "Parsed natural language command via Gemini.")
        val rawGroupId = obj.optString("matchedGroupId", "").trim()
        val matchedGroupId = when {
            rawGroupId.isBlank() || rawGroupId.equals("null", ignoreCase = true) -> null
            groupById.containsKey(rawGroupId) -> rawGroupId
            else -> groups.firstOrNull { it.name.equals(rawGroupId, ignoreCase = true) }?.id
        }

        val newMembersArr = obj.optJSONArray("newMemberNames")
        if (newMembersArr != null) {
            for (i in 0 until newMembersArr.length()) {
                val nm = cleanDisplayName(newMembersArr.optString(i, ""))
                if (nm.isNotEmpty() && members.none { it.name.equals(nm, ignoreCase = true) || it.name.split(" ").first().equals(nm, ignoreCase = true) }) {
                    pendingNewMembers.add(nm)
                }
            }
        }

        val participantsArray = obj.optJSONArray("participants")
        val participants = mutableListOf<ParsedVoiceParticipant>()
        if (participantsArray != null) {
            for (i in 0 until participantsArray.length()) {
                val pObj = participantsArray.getJSONObject(i)
                val pIdRaw = pObj.optString("memberId", "")
                val pNameRaw = pObj.optString("memberName", "")
                val (resolvedId, resolvedName) = resolveMember(pIdRaw, pNameRaw)
                if (resolvedId.isNotBlank()) {
                    val amtMinor = (pObj.optDouble("amount", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    val shares = pObj.optLong("shares", 1L).coerceAtLeast(1L)
                    val pctBps = (pObj.optDouble("percentage", 0.0) * 100.0).roundToLong().coerceAtLeast(0L)
                    participants.add(
                        ParsedVoiceParticipant(
                            memberId = resolvedId,
                            memberName = resolvedName,
                            amountMinorUnits = amtMinor,
                            shares = shares,
                            percentageBps = pctBps
                        )
                    )
                }
            }
        }

        // Deduplicate & ensure reconciliation with totalMinor
        val deduped = participants.groupBy { it.memberId }.map { (_, list) ->
            val first = list.first()
            first.copy(amountMinorUnits = list.sumOf { it.amountMinorUnits })
        }.filter { it.amountMinorUnits > 0L || participants.size == 1 }.toMutableList()

        if (deduped.isEmpty() && selfMember != null) {
            deduped.add(ParsedVoiceParticipant(selfMember.id, selfMember.name, totalMinor, 1L, 10_000L))
        } else if (deduped.isNotEmpty()) {
            val sum = deduped.sumOf { it.amountMinorUnits }
            if (sum != totalMinor) {
                val diff = totalMinor - sum
                val idx = deduped.indices.maxByOrNull { deduped[it].amountMinorUnits } ?: 0
                deduped[idx] = deduped[idx].copy(
                    amountMinorUnits = (deduped[idx].amountMinorUnits + diff).coerceAtLeast(0L)
                )
            }
        }

        var settlementFromId: String? = null
        var settlementFromName: String? = null
        var settlementToId: String? = null
        var settlementToName: String? = null
        if (actionType == VoiceActionType.SETTLEMENT) {
            val rawFromId = obj.optString("settlementFromMemberId", "").trim()
            val rawFromName = obj.optString("settlementFromMemberName", "").trim()
            val rawToId = obj.optString("settlementToMemberId", "").trim()
            val rawToName = obj.optString("settlementToMemberName", "").trim()
            if (rawFromId.isNotEmpty() || rawFromName.isNotEmpty()) {
                val (fid, fname) = resolveMember(rawFromId, rawFromName)
                settlementFromId = fid
                settlementFromName = fname
            }
            if (rawToId.isNotEmpty() || rawToName.isNotEmpty()) {
                val (tid, tname) = resolveMember(rawToId, rawToName)
                settlementToId = tid
                settlementToName = tname
            }
        }

        val amountFormatted = com.example.engine.FinanceEngine.formatMinorUnits(totalMinor, currency)
        val summaryLine = obj.optString("ledgerSummaryLine", "").trim().ifBlank {
            when (actionType) {
                VoiceActionType.SETTLEMENT -> "${settlementFromName ?: "Friend"} paid ${settlementToName ?: "You"} $amountFormatted"
                VoiceActionType.DIRECT_IOU -> {
                    val selfId = selfMember?.id
                    val counterparty = deduped.firstOrNull { it.memberId != selfId }
                    if (paidByMemberId == selfId && counterparty != null) {
                        "${counterparty.memberName.split(" ").first()} owes you $amountFormatted"
                    } else if (paidByMemberId != selfId) {
                        "You owe ${paidByMemberName.split(" ").first().ifBlank { "Friend" }} $amountFormatted"
                    } else {
                        "$title · $amountFormatted"
                    }
                }
                VoiceActionType.SHARED_EXPENSE -> "$title · $amountFormatted"
            }
        }
        val assistantReply = obj.optString("assistantReply", "").trim().ifBlank {
            "Done! Recorded $summaryLine in your ledger."
        }

        return ParsedVoiceSplit(
            transcript = resolvedTranscript,
            actionType = actionType,
            title = title,
            totalMinorUnits = totalMinor,
            currency = currency,
            paidByMemberId = paidByMemberId,
            paidByMemberName = paidByMemberName,
            category = category,
            splitType = splitType,
            participants = deduped,
            aiConfidenceNote = note,
            matchedGroupId = matchedGroupId,
            newMemberNames = pendingNewMembers.toList(),
            assistantReply = assistantReply,
            ledgerSummaryLine = summaryLine,
            settlementFromMemberId = settlementFromId,
            settlementFromMemberName = settlementFromName,
            settlementToMemberId = settlementToId,
            settlementToMemberName = settlementToName
        )
    }

    private fun scaleBitmapForVision(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source
        val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }
}
