package com.example.ai

import com.example.model.SpendCoachInsight
import com.example.model.SpendCoachReport
import com.example.model.TripStoryNarrative
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 100% local on-device ledger analytics engine for Spend Coach and Trip Wrap-Up narratives.
 * Parses the structured ledger digest produced by SplitFlowViewModel and produces actionable
 * coaching insights, instant follow-up Q&A, and shareable trip stories with zero API calls.
 */
object LocalFinanceInsightsEngine {

    fun generateSpendCoach(ledgerDigest: String): SpendCoachReport {
        val lines = ledgerDigest.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val totalLine = lines.firstOrNull { it.startsWith("Total Tracked Spend:") }.orEmpty()
        val netLine = lines.firstOrNull { it.startsWith("Self Net Position:") }.orEmpty()
        val budgetLines = lines.filter { it.startsWith("Budget [") || it.startsWith("Group Budget:") }
        val categoryLines = extractSection(lines, "Top Categories:", setOf("Recurring Bills:", "Recent Expenses:"))
        val recurringLines = extractSection(lines, "Recurring Bills:", setOf("Recent Expenses:"))

        val overBudget = budgetLines.firstOrNull { it.contains("OVER", ignoreCase = true) }
        val warningBudget = budgetLines.firstOrNull { it.contains("WARNING", ignoreCase = true) }

        val topCat = categoryLines.firstOrNull()?.removePrefix("- ")
        val topCatPct = Regex("\\((\\d+)%\\)").find(topCat.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

        var score = 84
        if (overBudget != null) score -= 22
        else if (warningBudget != null) score -= 10
        if (topCatPct >= 60) score -= 8
        if (netLine.contains("-")) score -= 6
        if (totalLine.contains("across 0 expenses")) score = 95
        score = score.coerceIn(35, 98)

        val headline = when {
            totalLine.contains("across 0 expenses") ->
                "Clean slate — log your first split or scan a bill to unlock live spending analytics."
            overBudget != null ->
                "One of your active groups has crossed its budget cap — review top categories now."
            topCat != null ->
                "Your ledger is healthy overall, led by ${topCat.substringBefore(":")} at $topCatPct% of tracked spend."
            else ->
                "Your shared expenses are well-balanced with clear opportunities to simplify balances."
        }

        val budgetAlert = when {
            overBudget != null -> overBudget.replace("Budget ", "").replace("Group Budget: ", "Over cap: ")
            warningBudget != null -> warningBudget.replace("Budget ", "").replace("Group Budget: ", "Nearing cap: ")
            recurringLines.isNotEmpty() -> "${recurringLines.size} recurring ${if (recurringLines.size == 1) "bill" else "bills"} tracked — review upcoming due dates."
            else -> "All active group budgets are tracking comfortably within limits."
        }

        val insights = buildList {
            if (topCat != null) {
                val catName = topCat.substringBefore(":").trim()
                val catDetail = topCat.substringAfter(":").trim()
                add(
                    SpendCoachInsight(
                        title = "Top Driver: $catName",
                        detail = "$catName accounts for $catDetail. Setting a weekly cap or using itemized splits here has the biggest impact.",
                        potentialSavingsLabel = "Trim ~15%",
                        category = catName
                    )
                )
            } else {
                add(
                    SpendCoachInsight(
                        title = "Start With Voice or Receipt OCR",
                        detail = "Use local Whistle voice commands or Mirage receipt scanning to build category trends automatically.",
                        potentialSavingsLabel = "On-Device",
                        category = "Setup"
                    )
                )
            }

            if (recurringLines.isNotEmpty()) {
                val firstRec = recurringLines.first().removePrefix("- ")
                add(
                    SpendCoachInsight(
                        title = "Recurring Bills Audit",
                        detail = "You have ${recurringLines.size} active recurring charges including $firstRec. Confirm equal splits before renewal.",
                        potentialSavingsLabel = "${recurringLines.size} Recurring",
                        category = "Recurring"
                    )
                )
            } else {
                add(
                    SpendCoachInsight(
                        title = "Automate Shared Subscriptions",
                        detail = "Mark rent, Wi-Fi, or streaming splits as recurring so monthly balances update without manual re-entry.",
                        potentialSavingsLabel = "Save Time",
                        category = "Recurring"
                    )
                )
            }

            val netClean = netLine.removePrefix("Self Net Position:").trim().ifBlank { "Balanced" }
            add(
                SpendCoachInsight(
                    title = "Net Balance Position",
                    detail = "Current net standing is $netClean. Use Debt Simplification to settle multiple friends in minimal transfers.",
                    potentialSavingsLabel = if (netClean.startsWith("+")) "Owed to You" else "Settle Fast",
                    category = "Balances"
                )
            )
        }

        return SpendCoachReport(
            healthScore = score,
            headline = headline,
            budgetAlertNote = budgetAlert,
            insights = insights
        )
    }

    fun streamCoachFollowUp(ledgerDigest: String, question: String): Flow<String> = flow {
        val answer = answerCoachQuestion(ledgerDigest, question)
        val tokens = answer.split(" ")
        for (i in tokens.indices) {
            emit(if (i == 0) tokens[i] else " ${tokens[i]}")
            delay(18L)
        }
    }

    private fun answerCoachQuestion(ledgerDigest: String, question: String): String {
        val q = question.lowercase(Locale.US)
        val lines = ledgerDigest.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val totalLine = lines.firstOrNull { it.startsWith("Total Tracked Spend:") }?.removePrefix("Total Tracked Spend:")?.trim()
        val netLine = lines.firstOrNull { it.startsWith("Self Net Position:") }?.removePrefix("Self Net Position:")?.trim()
        val budgets = lines.filter { it.startsWith("Budget [") || it.startsWith("Group Budget:") }
        val categories = extractSection(lines, "Top Categories:", setOf("Recurring Bills:", "Recent Expenses:"))
        val recurring = extractSection(lines, "Recurring Bills:", setOf("Recent Expenses:"))
        val recent = extractSection(lines, "Recent Expenses:", emptySet())

        return when {
            q.contains("recurr") || q.contains("bill") || q.contains("subscri") -> {
                if (recurring.isEmpty()) {
                    "You do not have any recurring bills logged in this scope yet. Mark shared rent, Wi-Fi, or utilities as recurring to track monthly fixed costs automatically."
                } else {
                    "You currently track ${recurring.size} recurring bills: ${recurring.take(3).joinToString("; ") { it.removePrefix("- ") }}. Auditing unused shared plans is the fastest way to lower monthly fixed spend."
                }
            }
            q.contains("budget") || q.contains("pac") || q.contains("cap") || q.contains("trip") -> {
                if (budgets.isEmpty()) {
                    "No group budget caps are set yet. Total tracked spend is ${totalLine ?: "0"} — add a budget cap to any group to get real-time pacing alerts."
                } else {
                    "Budget pacing summary: ${budgets.take(3).joinToString(" | ")}. Keep an eye on high-frequency dining and travel entries to stay under target."
                }
            }
            q.contains("trim") || q.contains("save") || q.contains("cut") || q.contains("food") || q.contains("categor") -> {
                val top = categories.firstOrNull()?.removePrefix("- ")
                if (top != null) {
                    "Your largest spend driver is $top out of ${totalLine ?: "your ledger"}. Trimming 15% from this category and itemizing group bills via local Receipt OCR will produce the fastest monthly savings."
                } else {
                    "Log a few expenses first so your local Spend Coach can pinpoint which category offers the easiest 15% savings."
                }
            }
            q.contains("owe") || q.contains("balance") || q.contains("settle") -> {
                "Your current net position is ${netLine ?: "settled up"} across ${totalLine ?: "tracked activity"}. Open Simplify Debts on the Home or Groups tab to clear balances in the fewest transfers."
            }
            else -> {
                val topSnippet = categories.firstOrNull()?.removePrefix("- ") ?: "No category spikes yet"
                val recentSnippet = recent.firstOrNull()?.removePrefix("- ")
                buildString {
                    append("Tracked spend is ${totalLine ?: "₹0"} with net position ${netLine ?: "₹0"}. ")
                    append("Top category: $topSnippet.")
                    if (recentSnippet != null) {
                        append(" Most recent entry: $recentSnippet.")
                    }
                }
            }
        }
    }

    fun generateTripStory(metricsDigest: String): TripStoryNarrative {
        val lines = metricsDigest.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun valueOf(prefix: String): String =
            lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim().orEmpty()

        val groupName = valueOf("Group:").substringBefore("(").trim().ifBlank { "Your Group" }
        val totalSpend = valueOf("Total Spend:").ifBlank { "tracked bills" }
        val topSpender = valueOf("Top Spender:").ifBlank { "Everyone pitched in" }
        val biggestCat = valueOf("Biggest Category:").ifBlank { "Balanced categories" }
        val burnRate = valueOf("Daily Burn Rate:")
        val debtSaved = valueOf("Debt Simplification:")
        val budget = valueOf("Budget Cap:")

        val vibeTag = when {
            budget.contains("OVER", ignoreCase = true) -> "Full Send Mode"
            biggestCat.contains("Food", ignoreCase = true) -> "Foodie Crew"
            biggestCat.contains("Travel", ignoreCase = true) -> "Road Warriors"
            biggestCat.contains("Stay", ignoreCase = true) -> "Villa Vibes"
            else -> "Clean Splits"
        }

        val highlights = buildList {
            add("$topSpender led the group tab across $totalSpend.")
            add("$biggestCat drove the biggest share of the bill${if (burnRate.isNotBlank()) " at $burnRate" else ""}.")
            if (debtSaved.isNotBlank()) {
                add("$debtSaved with SplitFlow's local debt graph engine.")
            }
        }

        return TripStoryNarrative(
            headline = "$groupName Wrapped: $totalSpend",
            vibeTag = vibeTag,
            highlights = highlights,
            closingQuip = "All math verified on-device — settle in one tap and plan the next one."
        )
    }

    private fun extractSection(
        lines: List<String>,
        header: String,
        stopHeaders: Set<String>
    ): List<String> {
        val start = lines.indexOfFirst { it.equals(header, ignoreCase = true) }
        if (start < 0) return emptyList()
        val result = mutableListOf<String>()
        for (i in (start + 1) until lines.size) {
            val l = lines[i]
            if (stopHeaders.any { l.equals(it, ignoreCase = true) }) break
            if (l.startsWith("- ")) result.add(l)
        }
        return result
    }
}
