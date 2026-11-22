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
import kotlin.math.roundToLong

/**
 * Deterministic on-device financial command interpreter for SplitFlow.
 * Converts transcripts from Cactus Whistle (or typed text) into exact ledger actions
 * with zero cloud calls or API rate limits.
 */
object LocalVoiceParser {

    private val stopWords = setOf(
        "i", "me", "my", "we", "us", "you", "your", "he", "she", "they", "them",
        "owes", "owe", "owed", "paid", "pay", "lent", "lend", "borrowed", "borrow",
        "back", "settled", "settle", "received", "got", "returned", "sent", "gave",
        "split", "equally", "equal", "evenly", "between", "among", "with", "and", "for",
        "the", "a", "an", "to", "from", "of", "in", "on", "at", "by", "about", "around",
        "rupees", "rupee", "rs", "inr", "dollars", "dollar", "usd", "euros", "euro", "eur",
        "pounds", "pound", "gbp", "baht", "thb", "bucks", "total", "bill", "amount",
        "dinner", "lunch", "breakfast", "coffee", "tea", "drinks", "food", "groceries",
        "grocery", "cab", "taxi", "uber", "ola", "flight", "hotel", "villa", "rent",
        "wifi", "electricity", "movie", "tickets", "shopping", "snacks", "party", "trip",
        "except", "skipped", "without", "excluding", "just", "only", "please", "add", "log",
        "record", "hey", "ok", "okay", "has", "have", "needs", "need", "will"
    )

    fun parse(
        rawInput: String,
        members: List<LedgerMember>,
        groups: List<ExpenseGroup>,
        defaultCurrency: CurrencyCode,
        engineLabel: String = "Local AI"
    ): ParsedVoiceSplit {
        val transcript = rawInput.trim().replace(Regex("\\s+"), " ")
        require(transcript.isNotBlank()) { "Please speak or type a command first." }

        val lower = transcript.lowercase(Locale.US)
        val selfMember = members.firstOrNull { it.selfProfile } ?: members.firstOrNull()
        val selfId = selfMember?.id.orEmpty()
        val selfName = selfMember?.name ?: "You"

        val currency = detectCurrency(lower, defaultCurrency)
        val amountMinor = extractPrimaryAmountMinor(transcript)
            ?: throw IllegalArgumentException("Could not find an amount in \"$transcript\". Try e.g. \"Tanmay owes me 500 rupees\".")

        val matchedGroup = matchGroup(lower, groups)
        val category = detectCategory(lower)
        val reason = extractReason(transcript)

        val pendingNew = linkedSetOf<String>()

        fun resolvePerson(rawName: String): Pair<String, String> {
            val cleaned = cleanTitleCase(rawName)
            if (cleaned.isBlank() || cleaned.equals("me", true) || cleaned.equals("i", true) || cleaned.equals("you", true)) {
                return selfId to selfName
            }
            val found = members.firstOrNull { m ->
                m.name.equals(cleaned, ignoreCase = true) ||
                    m.name.split(" ").firstOrNull().equals(cleaned, ignoreCase = true)
            }
            if (found != null) return found.id to found.name
            pendingNew.add(cleaned)
            return "NEW:$cleaned" to cleaned
        }

        val amountText = FinanceEngine.formatMinorUnits(amountMinor, currency)

        // 1. Settlement patterns: "<Name> paid me back 500" / "received 500 from <Name>" / "I paid <Name> back 500"
        val receivedFromMatch = Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+(?:paid|gave|sent|returned)\\s+(?:me|us)\\s+(?:back\\s+)?|received\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)|got\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)",
            RegexOption.IGNORE_CASE
        ).find(transcript)
        val isSettlementCue = lower.contains("paid me back") ||
            lower.contains("pay me back") ||
            lower.contains("returned") ||
            lower.contains("received ") ||
            lower.contains("got ") && lower.contains(" from ") ||
            lower.contains("paid back") ||
            lower.contains("settled") ||
            lower.contains("settle ")

        if (isSettlementCue) {
            val userPaidFriend = Regex(
                "(?:i\\s+(?:paid|settled|returned|sent)\\s+(?:back\\s+)?)([A-Za-z][A-Za-z']+)|(?:paid\\s+back|settled)\\s+.*?\\s+(?:to|with)\\s+([A-Za-z][A-Za-z']+)",
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
                aiConfidenceNote = "Parsed on-device via $engineLabel",
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

        // 2. Direct IOU: "<Name> owes me 500" / "I lent <Name> 500" / "<Name> borrowed 500 from me"
        val friendOwesMeMatch = Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+(?:owes|owe|has to pay|needs to pay)\\s+(?:me|us)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "\\b(?:i\\s+)?lent\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+borrowed\\s+.*?\\s+from\\s+me\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)

        if (friendOwesMeMatch != null) {
            val rawName = friendOwesMeMatch.groupValues[1]
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
                aiConfidenceNote = "Direct IOU parsed on-device via $engineLabel",
                matchedGroupId = matchedGroup?.id,
                newMemberNames = pendingNew.toList(),
                assistantReply = "Done! I logged $amountText so $friendFirst owes you $amountText in your Activity.",
                ledgerSummaryLine = summary
            )
        }

        // 3. Direct IOU reverse: "I owe <Name> 500" / "Borrowed 500 from <Name>" / "<Name> lent me 500"
        val iOweFriendMatch = Regex(
            "\\b(?:i\\s+)?owe\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "\\bborrowed\\s+.*?\\s+from\\s+([A-Za-z][A-Za-z']+)\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript) ?: Regex(
            "(?:^|\\b)([A-Za-z][A-Za-z']+)\\s+lent\\s+me\\b",
            RegexOption.IGNORE_CASE
        ).find(transcript)

        if (iOweFriendMatch != null) {
            val rawName = iOweFriendMatch.groupValues[1]
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
                aiConfidenceNote = "Direct IOU parsed on-device via $engineLabel",
                matchedGroupId = matchedGroup?.id,
                newMemberNames = pendingNew.toList(),
                assistantReply = "Got it! Logged $amountText so you owe $friendFirst $amountText in your Activity.",
                ledgerSummaryLine = summary
            )
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

    private fun detectCurrency(lower: String, fallback: CurrencyCode): CurrencyCode = when {
        lower.contains("₹") || lower.contains("rupee") || Regex("\\brs\\.?\\b").containsMatchIn(lower) || lower.contains("inr") -> CurrencyCode.INR
        lower.contains("$") || lower.contains("dollar") || lower.contains("usd") -> CurrencyCode.USD
        lower.contains("€") || lower.contains("euro") || lower.contains("eur") -> CurrencyCode.EUR
        lower.contains("£") || lower.contains("pound") || lower.contains("gbp") -> CurrencyCode.GBP
        lower.contains("฿") || lower.contains("baht") || lower.contains("thb") -> CurrencyCode.THB
        else -> fallback
    }

    private fun extractPrimaryAmountMinor(text: String): Long? {
        // Match shorthand like "1.5k" or "2k"
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
                // Skip small participant counts like "among 5" or "split 3 ways"
                val isCount = before.endsWith("among") || before.endsWith("between") || before.endsWith("split")
                val value = raw.toDoubleOrNull()
                if (value != null && value > 0.0 && !(isCount && value <= 20.0)) value else null
            }
            .toList()
        if (numMatches.isNotEmpty()) {
            return (numMatches.first() * 100.0).roundToLong().coerceAtLeast(100L)
        }

        // Basic spoken word numbers (e.g. "five hundred", "two thousand")
        val spoken = parseSpokenAmount(text.lowercase(Locale.US))
        return spoken?.let { (it * 100.0).roundToLong().coerceAtLeast(100L) }
    }

    private fun parseSpokenAmount(lower: String): Double? {
        val units = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40,
            "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )
        var total = 0
        var current = 0
        var found = false
        for (tok in lower.split(Regex("[^a-z]+"))) {
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
        listOf("dinner", "lunch", "breakfast", "coffee", "tea", "cafe", "pizza", "burger", "food", "drinks", "beer", "bar", "snacks", "meal", "restaurant")
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

        // Match names after "with ..." / "and ..." / capitalized proper nouns
        val afterWith = Regex("\\bwith\\s+([A-Za-z][A-Za-z']+(?:\\s*(?:,|and)\\s*[A-Za-z][A-Za-z']+)*)", RegexOption.IGNORE_CASE)
            .find(transcript)
        afterWith?.groupValues?.getOrNull(1)?.split(Regex("\\s*(?:,|\\band\\b)\\s*", RegexOption.IGNORE_CASE))?.forEach { token ->
            val t = token.trim()
            if (t.length >= 2 && t.lowercase(Locale.US) !in stopWords) {
                found.add(cleanTitleCase(t))
            }
        }

        // Fallback capitalized tokens (excluding sentence start if it's a stopword)
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
