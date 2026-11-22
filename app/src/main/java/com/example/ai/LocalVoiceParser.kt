package com.example.ai

import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.ExpenseCategory
import com.example.model.ExpenseGroup
import com.example.model.LedgerMember
import com.example.model.ParsedVoiceParticipant
import com.example.model.ParsedVoiceSplit
import com.example.model.SplitType
import com.example.model.VoiceActionType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Deterministic on-device financial command interpreter for SplitFlow.
 * Converts transcripts from Speech Recognition / Cactus Whistle (or typed text) into exact
 * ledger actions with fuzzy friend-name matching, N-best hypothesis scoring, and natural phrasing.
 */
object LocalVoiceParser {

    private val stopWords = setOf(
        "i", "me", "my", "we", "us", "you", "your", "he", "she", "they", "them", "myself",
        "owes", "owe", "owed", "paid", "pay", "paying", "lent", "lend", "borrowed", "borrow",
        "back", "settled", "settle", "received", "receive", "got", "returned", "return",
        "sent", "send", "gave", "give", "took", "take", "split", "splitting", "equally",
        "equal", "evenly", "between", "among", "with", "and", "for", "the", "a", "an",
        "to", "from", "of", "in", "on", "at", "by", "about", "around", "into", "onto",
        "rupees", "rupee", "rs", "inr", "dollars", "dollar", "usd", "euros", "euro", "eur",
        "pounds", "pound", "gbp", "baht", "thb", "bucks", "total", "bill", "amount", "money",
        "cash", "upi", "gpay", "paytm", "phonepe", "dinner", "lunch", "breakfast", "coffee",
        "tea", "chai", "drinks", "food", "groceries", "grocery", "cab", "taxi", "uber", "ola",
        "auto", "flight", "hotel", "villa", "rent", "wifi", "electricity", "movie", "tickets",
        "shopping", "snacks", "party", "trip", "pizza", "burger", "beer", "cafe",
        "except", "skipped", "without", "excluding", "just", "only", "please", "add", "log",
        "record", "hey", "hi", "hello", "ok", "okay", "has", "have", "needs", "need", "will",
        "was", "were", "is", "are", "been", "today", "yesterday", "tonight", "now", "here",
        "there", "this", "that", "bro", "dude", "guys", "everyone", "all", "friend", "friends",
        "actually", "basically", "like", "also", "then", "than", "more", "less", "worth"
    )

    /**
     * Scores multiple N-best speech recognition hypotheses from Android SpeechRecognizer
     * and picks the candidate most likely to represent an accurate expense command
     * (rewarding known friend names, amounts, currency cues, and financial verbs).
     */
    fun pickBestTranscript(
        hypotheses: List<String>,
        members: List<LedgerMember>
    ): String {
        val cleaned = hypotheses.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return ""
        if (cleaned.size == 1) return cleaned.first()

        val financeVerbRegex = Regex(
            "\\b(owes?|owed|paid|pay|lent|borrowed|settled?|received|returned|sent|gave|took|split|bill)\\b",
            RegexOption.IGNORE_CASE
        )
        val currencyRegex = Regex(
            "(₹|\\$|€|£|\\b(rupees?|rs\\.?|inr|dollars?|usd|euros?|pounds?|bucks)\\b)",
            RegexOption.IGNORE_CASE
        )

        var bestText = cleaned.first()
        var bestScore = Int.MIN_VALUE

        cleaned.forEachIndexed { index, candidate ->
            var score = (cleaned.size - index) * 2 // slight prior toward recognizer's top rank
            if (extractPrimaryAmountMinor(candidate) != null) score += 25
            if (financeVerbRegex.containsMatchIn(candidate)) score += 16
            if (currencyRegex.containsMatchIn(candidate)) score += 10

            // Strong bonus if hypothesis matches an actual friend in the user's ledger
            val lower = candidate.lowercase(Locale.US)
            for (m in members) {
                if (m.selfProfile) continue
                val first = m.name.split(" ").firstOrNull()?.lowercase(Locale.US).orEmpty()
                if (first.length >= 2 && Regex("\\b${Regex.escape(first)}\\b").containsMatchIn(lower)) {
                    score += 30
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestText = candidate
            }
        }
        return bestText
    }

    fun parse(
        rawInput: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode,
        engineLabel: String = "Local AI"
    ): ParsedVoiceSplit {
        val rawCleaned = rawInput.trim().replace(Regex("\\s+"), " ")
        require(rawCleaned.isNotBlank()) { "Please speak or type a command first." }

        // Repair split/phonetically misheard known friend names (e.g. "Tan may" -> "Tanmay")
        val transcript = normalizeKnownNamesInTranscript(rawCleaned, members)
        val lower = transcript.lowercase(Locale.US)
        val selfMember = members.firstOrNull { it.selfProfile } ?: members.firstOrNull()
        val selfId = selfMember?.id.orEmpty()
        val selfName = selfMember?.name ?: "You"

        val currency = detectCurrency(lower, defaultCurrency)
        val amountMinor = extractPrimaryAmountMinor(transcript)
            ?: throw IllegalArgumentException(
                "Heard \"$transcript\", but couldn't find an amount. Try saying e.g. \"Tanmay owes me 500 rupees\"."
            )

        val matchedGroup = matchGroup(lower, groups)
        val category = detectCategory(lower)
        val reason = extractReason(transcript)

        val pendingNew = linkedSetOf<String>()

        fun resolvePerson(rawName: String): Pair<String, String> {
            val cleaned = cleanTitleCase(rawName)
            if (cleaned.isBlank() || cleaned.equals("me", true) || cleaned.equals("i", true) || cleaned.equals("you", true)) {
                return selfId to selfName
            }
            val found = findBestMemberMatch(cleaned, members)
            if (found != null) return found.id to found.name
            pendingNew.add(cleaned)
            return "NEW:$cleaned" to cleaned
        }

        val amountText = FinanceEngine.formatMinorUnits(amountMinor, currency)

        // 1. Settlement patterns: "<Name> paid me back 500" / "received 500 from <Name>" / "<Name> sent me 500" / "I paid <Name> back 500"
        val receivedFromMatch = Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+(?:paid|gave|sent|returned|transferred)\\s+(?:me|us)\\b|received\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)|got\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)",
            RegexOption.IGNORE_CASE
        ).find(transcript)
        val isSettlementCue = lower.contains("paid me back") ||
            lower.contains("pay me back") ||
            lower.contains("paid me ") ||
            lower.contains("sent me ") ||
            lower.contains("gave me ") ||
            lower.contains("transferred me ") ||
            lower.contains("returned") ||
            lower.contains("received ") ||
            (lower.contains("got ") && lower.contains(" from ")) ||
            lower.contains("paid back") ||
            lower.contains("settled") ||
            lower.contains("settle ")

        if (isSettlementCue) {
            val userPaidFriend = Regex(
                "(?:i\\s+(?:paid|settled|returned|sent|gave)\\s+(?:back\\s+)?)([A-Za-z][A-Za-z']+)|(?:paid\\s+back|settled)\\s+.*?\\s+(?:to|with)\\s+([A-Za-z][A-Za-z']+)",
                RegexOption.IGNORE_CASE
            ).find(transcript)

            val friendRaw = when {
                receivedFromMatch != null -> receivedFromMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                userPaidFriend != null -> userPaidFriend.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                else -> extractCandidateNames(transcript, members).firstOrNull()
            }?.takeIf { it.lowercase(Locale.US) !in stopWords }

            val (friendId, friendName) = resolvePerson(friendRaw ?: "Friend")
            val friendFirst = friendName.split(" ").first()

            val fromUser = userPaidFriend != null && receivedFromMatch == null
            val fromId = if (fromUser) selfId else friendId
            val fromName = if (fromUser) selfName else friendName
            val toId = if (fromUser) friendId else selfId
            val toName = if (fromUser) friendName else selfName

            val summary = if (fromUser) "You paid $friendFirst $amountText" else "$friendFirst paid you $amountText"
            return ParsedVoiceSplit(
                transcript = transcript,
                actionType = VoiceActionType.SETTLEMENT,
                title = "Settlement with $friendFirst",
                totalMinorUnits = amountMinor,
                currency = currency,
                paidByMemberId = fromId,
                paidByMemberName = fromName,
                category = ExpenseCategory.OTHER,
                splitType = SplitType.EXACT,
                participants = listOf(ParsedVoiceParticipant(toId, toName, amountMinor, 1L, 10_000L)),
                aiConfidenceNote = "Parsed via $engineLabel",
                matchedGroupId = matchedGroup?.id,
                newMemberNames = pendingNew.toList(),
                assistantReply = "Done! Recorded that $summary in your Activity.",
                ledgerSummaryLine = summary,
                settlementFromMemberId = fromId,
                settlementFromMemberName = fromName,
                settlementToMemberId = toId,
                settlementToMemberName = toName
            )
        }

        // 2. Direct IOU: "<Name> owes me 500" / "<Name> owes 500" / "<Name> has to give 500" / "I lent <Name> 500" / "<Name> took 500 from me" / "add 500 on <Name>"
        val friendOwesMeMatch = Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+(?:owes|owe|owed|has to pay|needs to pay|has to give|needs to give)(?:\\s+(?:me|us))?\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)?.takeIf {
            val subject = it.groupValues[1].lowercase(Locale.US)
            subject != "i" && subject != "we" && subject !in stopWords
        } ?: Regex(
            "\\b(?:i\\s+)?(?:lent|lend|gave)\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+(?:borrowed|took)\\s+.*?\\s+from\\s+me\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "\\b(?:add|put|log)\\s+.*?\\s+(?:on|for|against)\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)?.takeIf {
            !lower.contains("split") && !lower.contains("with ")
        }

        if (friendOwesMeMatch != null) {
            val rawName = friendOwesMeMatch.groupValues[1]
            if (rawName.lowercase(Locale.US) !in stopWords) {
                val (friendId, friendName) = resolvePerson(rawName)
                val friendFirst = friendName.split(" ").first()
                val title = if (reason != null) "$reason — $friendFirst owes you" else "$friendFirst owes you"
                val summary = "$friendFirst owes you $amountText"
                return ParsedVoiceSplit(
                    transcript = transcript,
                    actionType = VoiceActionType.DIRECT_IOU,
                    title = title,
                    totalMinorUnits = amountMinor,
                    currency = currency,
                    paidByMemberId = selfId,
                    paidByMemberName = selfName,
                    category = category,
                    splitType = SplitType.EXACT,
                    participants = listOf(ParsedVoiceParticipant(friendId, friendName, amountMinor, 1L, 10_000L)),
                    aiConfidenceNote = "Direct IOU parsed via $engineLabel",
                    matchedGroupId = matchedGroup?.id,
                    newMemberNames = pendingNew.toList(),
                    assistantReply = "Done! Logged $amountText so $friendFirst owes you $amountText.",
                    ledgerSummaryLine = summary
                )
            }
        }

        // 3. Direct IOU reverse: "I owe <Name> 500" / "Borrowed 500 from <Name>" / "<Name> lent me 500" / "I have to pay <Name> 500"
        val iOweFriendMatch = Regex(
            "\\b(?:i\\s+)?(?:owe|have to pay|need to pay|have to give|need to give)\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "\\b(?:borrowed|took)\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+lent\\s+me\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)

        if (iOweFriendMatch != null) {
            val rawName = iOweFriendMatch.groupValues[1]
            if (rawName.lowercase(Locale.US) !in stopWords) {
                val (friendId, friendName) = resolvePerson(rawName)
                val friendFirst = friendName.split(" ").first()
                val title = if (reason != null) "$reason — You owe $friendFirst" else "You owe $friendFirst"
                val summary = "You owe $friendFirst $amountText"
                return ParsedVoiceSplit(
                    transcript = transcript,
                    actionType = VoiceActionType.DIRECT_IOU,
                    title = title,
                    totalMinorUnits = amountMinor,
                    currency = currency,
                    paidByMemberId = friendId,
                    paidByMemberName = friendName,
                    category = category,
                    splitType = SplitType.EXACT,
                    participants = listOf(ParsedVoiceParticipant(selfId, selfName, amountMinor, 1L, 10_000L)),
                    aiConfidenceNote = "Direct IOU parsed via $engineLabel",
                    matchedGroupId = matchedGroup?.id,
                    newMemberNames = pendingNew.toList(),
                    assistantReply = "Got it! Logged $amountText so you owe $friendFirst $amountText.",
                    ledgerSummaryLine = summary
                )
            }
        }

        // 4. Shared Expense Split (including optional "except <Name> skipped <amt>" math)
        val paidByMatch = Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+paid\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)
        val payerRaw = paidByMatch?.groupValues?.getOrNull(1)?.takeIf {
            val l = it.lowercase(Locale.US)
            l != "i" && l != "we" && l !in stopWords
        }
        val (payerId, payerName) = if (payerRaw != null) resolvePerson(payerRaw) else (selfId to selfName)

        val candidateNames = extractCandidateNames(transcript, members)
        val participantPairs = linkedMapOf<String, String>()
        if (selfId.isNotBlank()) participantPairs[selfId] = selfName
        participantPairs[payerId] = payerName
        candidateNames.forEach { n ->
            val (id, resolvedName) = resolvePerson(n)
            participantPairs[id] = resolvedName
        }

        // If user said "split among N" and named fewer people, include group or existing members
        val amongCount = Regex("(?:among|between)\\s+(\\d+)", RegexOption.IGNORE_CASE)
            .find(transcript)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (amongCount != null && participantPairs.size < amongCount) {
            val pool = matchedGroup?.memberIds?.mapNotNull { id -> members.firstOrNull { it.id == id } }
                ?: members
            for (m in pool) {
                if (participantPairs.size >= amongCount) break
                participantPairs.putIfAbsent(m.id, m.name)
            }
        }

        val orderedIds = participantPairs.keys.toList().ifEmpty { listOf(selfId) }
        val exclusionMatch = Regex(
            "(?:except|excluding|without)\\s+([A-Za-z][A-Za-z']+)\\s+(?:skipped|didn't have|no)?\\s*(?:₹|rs\\.?|inr|\\$)?\\s*(\\d+(?:[.,]\\d+)?)",
            RegexOption.IGNORE_CASE
        ).find(transcript)

        val participants = if (exclusionMatch != null && orderedIds.size >= 2) {
            val excludedName = exclusionMatch.groupValues[1]
            val excludedMinor = ((exclusionMatch.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0) * 100.0)
                .roundToLong().coerceIn(0L, amountMinor)
            val (excludedId, _) = resolvePerson(excludedName)
            val basePool = (amountMinor - excludedMinor).coerceAtLeast(0L)
            val baseAlloc = FinanceEngine.distributeEqual(basePool, orderedIds).associate { it.memberId to it.amountMinorUnits }
            val nonExcluded = orderedIds.filter { it != excludedId }.ifEmpty { orderedIds }
            val extraAlloc = FinanceEngine.distributeEqual(excludedMinor, nonExcluded).associate { it.memberId to it.amountMinorUnits }
            orderedIds.map { id ->
                val amt = (baseAlloc[id] ?: 0L) + (extraAlloc[id] ?: 0L)
                val pctBps = if (amountMinor > 0L) (amt * 10_000L) / amountMinor else 0L
                ParsedVoiceParticipant(id, participantPairs[id] ?: "Member", amt, 1L, pctBps)
            }
        } else {
            val equalAllocs = FinanceEngine.distributeEqual(amountMinor, orderedIds)
            equalAllocs.map { alloc ->
                ParsedVoiceParticipant(
                    memberId = alloc.memberId,
                    memberName = participantPairs[alloc.memberId] ?: "Member",
                    amountMinorUnits = alloc.amountMinorUnits,
                    shares = 1L,
                    percentageBps = alloc.percentageBps
                )
            }
        }

        val splitTitle = reason ?: when (category) {
            ExpenseCategory.FOOD -> "Shared Meal"
            ExpenseCategory.TRAVEL -> "Shared Ride"
            ExpenseCategory.STAY -> "Shared Stay"
            ExpenseCategory.GROCERIES -> "Groceries Split"
            ExpenseCategory.UTILITIES -> "Utility Bill"
            ExpenseCategory.ENTERTAINMENT -> "Entertainment Split"
            ExpenseCategory.SHOPPING -> "Shopping Split"
            ExpenseCategory.OTHER -> "Shared Expense"
        }
        val summaryLine = "$splitTitle · $amountText (${participants.size} way)"
        val splitType = if (exclusionMatch != null) SplitType.EXACT else SplitType.EQUAL

        return ParsedVoiceSplit(
            transcript = transcript,
            actionType = VoiceActionType.SHARED_EXPENSE,
            title = splitTitle,
            totalMinorUnits = amountMinor,
            currency = currency,
            paidByMemberId = payerId,
            paidByMemberName = payerName,
            category = category,
            splitType = splitType,
            participants = participants,
            aiConfidenceNote = "Split across ${participants.size} people via $engineLabel",
            matchedGroupId = matchedGroup?.id,
            newMemberNames = pendingNew.toList(),
            assistantReply = "Done! Split $amountText for $splitTitle across ${participants.size} people.",
            ledgerSummaryLine = summaryLine
        )
    }

    /**
     * Repairs common ASR splits/misspellings of known friend names before parsing
     * (e.g. "Tan may owes me 500" -> "Tanmay owes me 500", "Danmay" -> "Tanmay").
     */
    private fun normalizeKnownNamesInTranscript(
        transcript: String,
        members: List<LedgerMember>
    ): String {
        val friendFirstNames = members
            .filter { !it.selfProfile }
            .mapNotNull { it.name.split(" ").firstOrNull()?.trim() }
            .filter { it.length >= 3 }
            .distinctBy { it.lowercase(Locale.US) }
        if (friendFirstNames.isEmpty()) return transcript

        val words = transcript.split(" ").toMutableList()
        var i = 0
        while (i < words.size) {
            val w1Raw = words[i]
            val w1Clean = w1Raw.trim(',', '.', '!', '?').lowercase(Locale.US)

            // 1. Check merged pair ("tan may" -> "Tanmay")
            if (i + 1 < words.size) {
                val w2Raw = words[i + 1]
                val w2Clean = w2Raw.trim(',', '.', '!', '?').lowercase(Locale.US)
                if (w1Clean.isNotEmpty() && w2Clean.isNotEmpty() &&
                    w1Clean !in stopWords && w2Clean !in stopWords
                ) {
                    val merged = w1Clean + w2Clean
                    val mergedMatch = friendFirstNames.firstOrNull { name ->
                        val target = name.lowercase(Locale.US)
                        merged == target || (target.length >= 5 && levenshtein(merged, target) <= 1)
                    }
                    if (mergedMatch != null) {
                        words[i] = mergedMatch
                        words.removeAt(i + 1)
                        i++
                        continue
                    }
                }
            }

            // 2. Check single-word phonetic/typo match (only for non-stopwords >= 4 chars)
            if (w1Clean.length >= 4 && w1Clean !in stopWords && !w1Clean.all { it.isDigit() }) {
                val best = friendFirstNames.minByOrNull { levenshtein(w1Clean, it.lowercase(Locale.US)) }
                if (best != null) {
                    val dist = levenshtein(w1Clean, best.lowercase(Locale.US))
                    val maxAllowed = if (best.length >= 6) 2 else 1
                    if (dist in 1..maxAllowed && abs(w1Clean.length - best.length) <= 1) {
                        words[i] = best
                    }
                }
            }
            i++
        }
        return words.joinToString(" ")
    }

    private fun findBestMemberMatch(cleanedName: String, members: List<LedgerMember>): LedgerMember? {
        val exact = members.firstOrNull { m ->
            m.name.equals(cleanedName, ignoreCase = true) ||
                m.name.split(" ").firstOrNull().equals(cleanedName, ignoreCase = true)
        }
        if (exact != null) return exact

        val lower = cleanedName.lowercase(Locale.US)
        if (lower.length < 3) return null
        return members
            .filter { !it.selfProfile }
            .mapNotNull { m ->
                val first = m.name.split(" ").firstOrNull()?.lowercase(Locale.US).orEmpty()
                if (first.length < 3) return@mapNotNull null
                val dist = levenshtein(lower, first)
                val threshold = if (first.length >= 6) 2 else 1
                if (dist <= threshold) m to dist else null
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private fun detectCurrency(lower: String, fallback: CurrencyCode): CurrencyCode = when {
        lower.contains("₹") || lower.contains("rupee") || Regex("\\brs\\.?\\b").containsMatchIn(lower) || lower.contains("inr") -> CurrencyCode.INR
        lower.contains("$") || lower.contains("dollar") || lower.contains("usd") -> CurrencyCode.USD
        lower.contains("€") || lower.contains("euro") || lower.contains("eur") -> CurrencyCode.EUR
        lower.contains("£") || lower.contains("pound") || lower.contains("gbp") -> CurrencyCode.GBP
        lower.contains("฿") || lower.contains("baht") || lower.contains("thb") -> CurrencyCode.THB
        else -> fallback
    }

    private fun extractPrimaryAmountMinor(text: String): Long? {
        // Shorthand like "1.5k" or "2k"
        val kMatch = Regex("\\b(\\d+(?:\\.\\d+)?)\\s*[kK]\\b").find(text)
        if (kMatch != null) {
            val v = kMatch.groupValues[1].toDoubleOrNull()
            if (v != null && v > 0.0) return (v * 1000.0 * 100.0).roundToLong()
        }

        // Standard numeric amounts like 500, 1,800, 350.50
        val numMatches = Regex("(?:₹|\\$|€|£|฿|rs\\.?\\s*)?(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)")
            .findAll(text)
            .mapNotNull { m ->
                val raw = m.groupValues[1].replace(",", "")
                val before = text.substring(0, m.range.first).lowercase(Locale.US).trimEnd()
                val isCount = before.endsWith("among") || before.endsWith("between") || before.endsWith("split")
                val value = raw.toDoubleOrNull()
                if (value != null && value > 0.0 && !(isCount && value <= 20.0)) value else null
            }
            .toList()
        if (numMatches.isNotEmpty()) {
            return (numMatches.first() * 100.0).roundToLong().coerceAtLeast(100L)
        }

        // Spoken word numbers (e.g. "five hundred", "fifteen hundred", "two fifty", "two thousand")
        val spoken = parseSpokenAmount(text.lowercase(Locale.US))
        return spoken?.let { (it * 100.0).roundToLong().coerceAtLeast(100L) }
    }

    private fun parseSpokenAmount(lower: String): Double? {
        val units = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
            "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
            "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
            "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )
        val tokens = lower.split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        // Handle colloquial Indian spoken amounts like "two fifty" (250), "three fifty" (350), "five fifty" (550)
        for (i in 0 until tokens.size - 1) {
            val first = units[tokens[i]]
            val second = units[tokens[i + 1]]
            if (first != null && first in 1..9 && second != null && second >= 20 && second % 10 == 0) {
                return (first * 100 + second).toDouble()
            }
        }

        var total = 0
        var current = 0
        var found = false
        for (tok in tokens) {
            val u = units[tok]
            when {
                u != null -> {
                    current += u
                    found = true
                }
                tok == "hundred" -> {
                    current = (if (current == 0) 1 else current) * 100
                    found = true
                }
                tok == "thousand" -> {
                    total += (if (current == 0) 1 else current) * 1000
                    current = 0
                    found = true
                }
                tok == "lakh" || tok == "lac" -> {
                    total += (if (current == 0) 1 else current) * 100_000
                    current = 0
                    found = true
                }
            }
        }
        val result = total + current
        return if (found && result > 0) result.toDouble() else null
    }

    private fun matchGroup(lower: String, groups: List<ExpenseGroup>): ExpenseGroup? {
        return groups.firstOrNull { grp ->
            val gLower = grp.name.lowercase(Locale.US)
            gLower.length >= 3 && (lower.contains(gLower) || gLower.split(" ").any { part ->
                part.length >= 3 && lower.contains(part)
            })
        }
    }

    private fun detectCategory(lower: String): ExpenseCategory = when {
        listOf("dinner", "lunch", "breakfast", "coffee", "tea", "chai", "cafe", "pizza", "burger", "food", "drinks", "beer", "bar", "snacks", "meal", "restaurant")
            .any { lower.contains(it) } -> ExpenseCategory.FOOD
        listOf("cab", "taxi", "uber", "ola", "auto", "flight", "train", "bus", "petrol", "fuel", "metro", "toll")
            .any { lower.contains(it) } -> ExpenseCategory.TRAVEL
        listOf("hotel", "villa", "airbnb", "resort", "hostel", "stay", "room", "rent")
            .any { lower.contains(it) } -> ExpenseCategory.STAY
        listOf("grocery", "groceries", "milk", "vegetables", "supermarket", "blinkit", "zepto", "instamart")
            .any { lower.contains(it) } -> ExpenseCategory.GROCERIES
        listOf("wifi", "internet", "electricity", "water", "bill", "gas", "broadband", "recharge")
            .any { lower.contains(it) } -> ExpenseCategory.UTILITIES
        listOf("movie", "cinema", "concert", "tickets", "bowling", "party", "club", "game")
            .any { lower.contains(it) } -> ExpenseCategory.ENTERTAINMENT
        listOf("shop", "shopping", "clothes", "shoes", "gift", "amazon", "flipkart")
            .any { lower.contains(it) } -> ExpenseCategory.SHOPPING
        else -> ExpenseCategory.OTHER
    }

    private fun extractReason(transcript: String): String? {
        val m = Regex(
            "\\bfor\\s+([A-Za-z0-9 &'-]{2,36}?)(?=\\s+(?:with|split|among|between|except|in|at|on|,|\\d)|$)",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: return null
        val raw = m.groupValues[1].trim()
        if (raw.isBlank() || raw.lowercase(Locale.US) in stopWords) return null
        return cleanTitleCase(raw)
    }

    private fun extractCandidateNames(transcript: String, members: List<LedgerMember>): List<String> {
        val found = linkedSetOf<String>()
        val lower = transcript.lowercase(Locale.US)

        // Match existing friends first (case-insensitive)
        for (m in members) {
            if (m.selfProfile) continue
            val first = m.name.split(" ").firstOrNull()?.trim().orEmpty()
            if (first.length >= 2 && Regex("\\b${Regex.escape(first.lowercase(Locale.US))}\\b").containsMatchIn(lower)) {
                found.add(m.name)
            }
        }

        // Match names after "with ..." / "and ..."
        val afterWith = Regex("\\b(?:with|and)\\s+([A-Za-z][A-Za-z']+(?:\\s*(?:,|and)\\s*[A-Za-z][A-Za-z']+)*)", RegexOption.IGNORE_CASE)
            .findAll(transcript)
        afterWith.forEach { m ->
            m.groupValues.getOrNull(1)?.split(Regex("\\s*(?:,|\\band\\b)\\s*", RegexOption.IGNORE_CASE))?.forEach { token ->
                val t = token.trim()
                if (t.length >= 2 && t.lowercase(Locale.US) !in stopWords) {
                    found.add(cleanTitleCase(t))
                }
            }
        }

        // Fallback capitalized tokens (excluding stopwords)
        Regex("\\b([A-Z][a-z']{2,})\\b").findAll(transcript).forEach { match ->
            val word = match.groupValues[1]
            if (word.lowercase(Locale.US) !in stopWords) {
                found.add(word)
            }
        }
        return found.toList()
    }

    private fun cleanTitleCase(raw: String): String {
        return raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.lowercase(Locale.US).replaceFirstChar { it.uppercase(Locale.US) }
        }
    }
}
