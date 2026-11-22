package com.example.engine

import android.net.Uri
import com.example.model.AnomalySeverity
import com.example.model.BudgetHealth
import com.example.model.CategorySpend
import com.example.model.CurrencyCode
import com.example.model.DailyBurnStats
import com.example.model.DebtSimplificationReport
import com.example.model.DebtTransfer
import com.example.model.ExpenseAnomaly
import com.example.model.ExpenseCategory
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.GroupBudgetStatus
import com.example.model.ItemizedBreakdown
import com.example.model.LedgerMember
import com.example.model.MemberBalance
import com.example.model.MonthlyCashFlowPoint
import com.example.model.ReceiptLineItem
import com.example.model.RecurringFrequency
import com.example.model.SettlementRecord
import com.example.model.SplitAllocation
import com.example.model.TripWrapUpMetrics
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object FinanceEngine {

    /**
     * Deterministic reference FX rates expressed as INR per 1 unit of currency in micro-units (1_000_000 = 1.0).
     */
    private val defaultInrPerUnitMicros: Map<CurrencyCode, Long> = mapOf(
        CurrencyCode.INR to 1_000_000L,
        CurrencyCode.USD to 84_000_000L,
        CurrencyCode.EUR to 91_500_000L,
        CurrencyCode.GBP to 107_000_000L,
        CurrencyCode.THB to 2_450_000L
    )

    fun getFxRateMicros(
        from: CurrencyCode,
        to: CurrencyCode,
        customInrPerUnitMicros: Map<CurrencyCode, Long> = emptyMap()
    ): Long {
        if (from == to) return 1_000_000L
        val fromInr = customInrPerUnitMicros[from] ?: defaultInrPerUnitMicros[from] ?: 1_000_000L
        val toInr = customInrPerUnitMicros[to] ?: defaultInrPerUnitMicros[to] ?: 1_000_000L
        return ((fromInr.toDouble() / toInr.toDouble()) * 1_000_000.0).roundToLong().coerceAtLeast(1L)
    }

    fun convertMinorUnits(
        amountMinor: Long,
        from: CurrencyCode,
        to: CurrencyCode,
        rateMicrosOverride: Long? = null,
        customInrPerUnitMicros: Map<CurrencyCode, Long> = emptyMap()
    ): Long {
        if (from == to || amountMinor == 0L) return amountMinor
        val rateMicros = rateMicrosOverride?.takeIf { it >= 1L }
            ?: getFxRateMicros(from, to, customInrPerUnitMicros)
        return ((amountMinor.toDouble() * rateMicros.toDouble()) / 1_000_000.0).roundToLong()
    }

    fun formatFxRate(rateMicros: Long): String {
        val value = rateMicros / 1_000_000.0
        return if (value >= 10.0) "%.2f".format(Locale.US, value) else "%.4f".format(Locale.US, value)
    }

    fun computeGroupBudgetStatus(
        group: ExpenseGroup,
        groupExpenses: List<ExpenseRecord>,
        customFxRates: Map<CurrencyCode, Long> = emptyMap()
    ): GroupBudgetStatus {
        val spentInGroupCurrency = groupExpenses.sumOf { exp ->
            convertMinorUnits(
                amountMinor = exp.totalMinorUnits,
                from = exp.currencyEnum,
                to = group.currencyEnum,
                rateMicrosOverride = exp.fxRateToBaseMicros,
                customInrPerUnitMicros = customFxRates
            )
        }
        val cap = group.budgetMinorUnits?.takeIf { it > 0L }
            ?: return GroupBudgetStatus(
                spentMinorUnits = spentInGroupCurrency,
                budgetMinorUnits = null,
                progressRatio = 0f,
                remainingMinorUnits = 0L,
                health = BudgetHealth.NONE
            )
        val ratio = (spentInGroupCurrency.toFloat() / cap.toFloat()).coerceAtLeast(0f)
        val health = when {
            ratio >= 1.0f -> BudgetHealth.EXCEEDED
            ratio >= 0.78f -> BudgetHealth.WARNING
            else -> BudgetHealth.HEALTHY
        }
        return GroupBudgetStatus(
            spentMinorUnits = spentInGroupCurrency,
            budgetMinorUnits = cap,
            progressRatio = ratio,
            remainingMinorUnits = cap - spentInGroupCurrency,
            health = health
        )
    }

    fun advanceDueDateIso(currentIso: String, frequency: RecurringFrequency): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val parsed = runCatching { fmt.parse(currentIso) }.getOrNull()
        if (parsed != null) cal.time = parsed
        when (frequency) {
            RecurringFrequency.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurringFrequency.QUARTERLY -> cal.add(Calendar.MONTH, 3)
            RecurringFrequency.NONE -> Unit
        }
        return fmt.format(cal.time)
    }

    fun daysUntilIso(dueIso: String?, todayIso: String): Int? {
        if (dueIso.isNullOrBlank()) return null
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val due = runCatching { fmt.parse(dueIso) }.getOrNull() ?: return null
        val today = runCatching { fmt.parse(todayIso) }.getOrNull() ?: return null
        val diffMs = due.time - today.time
        return (diffMs / (24L * 60L * 60L * 1000L)).toInt()
    }

    fun buildPaymentNudgeMessage(
        debtorName: String,
        creditorName: String,
        amountMinor: Long,
        currency: CurrencyCode,
        groupName: String?,
        upiVpa: String?
    ): String {
        val formatted = formatMinorUnits(amountMinor, currency)
        val firstDebtor = debtorName.split(" ").firstOrNull().orEmpty().ifBlank { "there" }
        val scope = if (!groupName.isNullOrBlank()) " for $groupName" else ""
        val upiLine = if (!upiVpa.isNullOrBlank() && currency == CurrencyCode.INR) {
            val link = buildUpiDeepLink(
                payeeVpa = upiVpa,
                payeeName = creditorName,
                amountMinorUnits = amountMinor,
                currency = currency,
                note = "SplitFlow${if (!groupName.isNullOrBlank()) " - $groupName" else ""}"
            )
            "\nUPI ID: $upiVpa\nQuick Pay: $link"
        } else ""
        return "Hey $firstDebtor! Quick nudge from SplitFlow — $formatted is pending$scope to $creditorName.$upiLine"
    }

    /**
     * Converts a user-entered major unit decimal string (e.g. "3200.50") into integer minor units (320050).
     */
    fun parseMajorToMinor(input: String): Long {
        val cleaned = input.replace(",", "").replace("₹", "").replace("$", "")
            .replace("€", "").replace("£", "").replace("฿", "").trim()
        val parsed = cleaned.toDoubleOrNull() ?: return 0L
        return (parsed * 100.0).roundToLong().coerceAtLeast(0L)
    }

    fun minorToMajorInput(minorUnits: Long): String {
        val absVal = abs(minorUnits)
        val whole = absVal / 100L
        val frac = absVal % 100L
        return if (frac == 0L) "$whole" else "$whole.${frac.toString().padStart(2, '0')}"
    }

    /**
     * Formats integer minor units cleanly with Indian comma formatting for INR and standard grouping for USD/EUR/GBP.
     */
    fun formatMinorUnits(
        minorUnits: Long,
        currency: CurrencyCode = CurrencyCode.INR,
        showPlusSign: Boolean = false
    ): String {
        val negative = minorUnits < 0L
        val absVal = abs(minorUnits)
        val whole = absVal / 100L
        val frac = absVal % 100L

        val formattedWhole = if (currency == CurrencyCode.INR) {
            formatIndianInteger(whole)
        } else {
            formatWesternInteger(whole)
        }
        val sign = when {
            negative -> "-"
            showPlusSign && minorUnits > 0L -> "+"
            else -> ""
        }
        val decimalPart = if (frac == 0L) "" else ".${frac.toString().padStart(2, '0')}"
        return "$sign${currency.symbol}$formattedWhole$decimalPart"
    }

    private fun formatIndianInteger(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val groupedRest = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$groupedRest,$last3"
    }

    private fun formatWesternInteger(value: Long): String {
        return value.toString().reversed().chunked(3).joinToString(",").reversed()
    }

    /**
     * Deterministically distributes integer minor units equally across members.
     * Remainder pennies are distributed +1 to the first N sorted member IDs so total always reconciles 100%.
     */
    fun distributeEqual(totalMinor: Long, memberIds: List<String>): List<SplitAllocation> {
        val unique = memberIds.distinct().filter { it.isNotBlank() }
        if (unique.isEmpty() || totalMinor <= 0L) return emptyList()
        val count = unique.size.toLong()
        val base = totalMinor / count
        val remainder = (totalMinor % count).toInt()
        val bpsBase = 10_000L / count
        val bpsRem = (10_000L % count).toInt()
        return unique.mapIndexed { idx, memberId ->
            val amt = base + if (idx < remainder) 1L else 0L
            val bps = bpsBase + if (idx < bpsRem) 1L else 0L
            SplitAllocation(
                memberId = memberId,
                amountMinorUnits = amt,
                percentageBps = bps,
                shares = 1L
            )
        }
    }

    /**
     * Distributes minor units by basis-point percentages (100.00% == 10000 bps).
     */
    fun distributeByPercentage(
        totalMinor: Long,
        bpsByMember: Map<String, Long>
    ): List<SplitAllocation> {
        val active = bpsByMember.entries.filter { it.value > 0L }
        if (active.isEmpty() || totalMinor <= 0L) return emptyList()
        val totalBps = active.sumOf { it.value }
        if (totalBps <= 0L) return emptyList()

        val rawAllocations = active.map { (memberId, bps) ->
            val exact = (totalMinor * bps) / totalBps
            val rem = (totalMinor * bps) % totalBps
            Triple(memberId, exact, rem)
        }.toMutableList()

        val allocated = rawAllocations.sumOf { it.second }
        var leftover = (totalMinor - allocated).toInt()
        val orderByRemainder = rawAllocations.indices.sortedByDescending { rawAllocations[it].third }
        for (idx in orderByRemainder) {
            if (leftover <= 0) break
            val cur = rawAllocations[idx]
            rawAllocations[idx] = Triple(cur.first, cur.second + 1L, cur.third)
            leftover--
        }

        return rawAllocations.map { (memberId, amount, _) ->
            SplitAllocation(
                memberId = memberId,
                amountMinorUnits = amount,
                percentageBps = bpsByMember[memberId] ?: 0L,
                shares = 1L
            )
        }
    }

    /**
     * Distributes minor units by integer shares/weights deterministically.
     */
    fun distributeByShares(
        totalMinor: Long,
        sharesByMember: Map<String, Long>
    ): List<SplitAllocation> {
        val active = sharesByMember.entries.filter { it.value > 0L }
        if (active.isEmpty() || totalMinor <= 0L) return emptyList()
        val totalShares = active.sumOf { it.value }
        if (totalShares <= 0L) return emptyList()

        val raw = active.map { (memberId, shares) ->
            val exact = (totalMinor * shares) / totalShares
            val rem = (totalMinor * shares) % totalShares
            Triple(memberId, exact, rem)
        }.toMutableList()

        var leftover = (totalMinor - raw.sumOf { it.second }).toInt()
        val sortedByRem = raw.indices.sortedByDescending { raw[it].third }
        for (idx in sortedByRem) {
            if (leftover <= 0) break
            val cur = raw[idx]
            raw[idx] = Triple(cur.first, cur.second + 1L, cur.third)
            leftover--
        }

        return raw.map { (memberId, amount, _) ->
            val s = sharesByMember[memberId] ?: 1L
            SplitAllocation(
                memberId = memberId,
                amountMinorUnits = amount,
                percentageBps = (s * 10_000L) / totalShares,
                shares = s
            )
        }
    }

    /**
     * Itemized Receipt Split Workbench engine:
     * - Splits each claimed line item equally across its assigned members (with deterministic penny distribution).
     * - Prorates tax, service charge, and discount strictly proportionally to each member's pre-tax subtotal.
     * - Produces exact per-member breakdown and verifies reconciliation against receipt grand total.
     */
    fun computeItemizedBreakdown(
        lineItems: List<ReceiptLineItem>,
        taxMinor: Long,
        serviceChargeMinor: Long,
        discountMinor: Long,
        expectedGrandTotalMinor: Long
    ): ItemizedBreakdown {
        val preTax = linkedMapOf<String, Long>()
        var unassignedCount = 0
        var assignedItemsSum = 0L

        lineItems.forEach { item ->
            val claimants = item.assignedMemberIds.distinct().filter { it.isNotBlank() }
            if (claimants.isEmpty()) {
                unassignedCount++
            } else {
                assignedItemsSum += item.totalPriceMinor
                val perItemAllocs = distributeEqual(item.totalPriceMinor, claimants)
                perItemAllocs.forEach { alloc ->
                    preTax[alloc.memberId] = (preTax[alloc.memberId] ?: 0L) + alloc.amountMinorUnits
                }
            }
        }

        if (preTax.isEmpty()) {
            return ItemizedBreakdown(
                allocations = emptyList(),
                preTaxSubtotalByMember = emptyMap(),
                proratedTaxByMember = emptyMap(),
                proratedServiceByMember = emptyMap(),
                proratedDiscountByMember = emptyMap(),
                assignedItemsTotalMinor = 0L,
                unassignedItemsCount = unassignedCount,
                computedGrandTotalMinor = 0L,
                expectedGrandTotalMinor = expectedGrandTotalMinor,
                isReconciled = false
            )
        }

        val proratedTax = proratePool(taxMinor, preTax)
        val proratedService = proratePool(serviceChargeMinor, preTax)
        val proratedDiscount = proratePool(discountMinor, preTax)

        val memberIds = preTax.keys.toList()
        val totalsByMember = memberIds.associateWith { id ->
            ((preTax[id] ?: 0L) + (proratedTax[id] ?: 0L) + (proratedService[id] ?: 0L) - (proratedDiscount[id] ?: 0L))
                .coerceAtLeast(0L)
        }.toMutableMap()

        val computedGrandTotal = totalsByMember.values.sum()
        val targetTotal = if (expectedGrandTotalMinor > 0L) expectedGrandTotalMinor else computedGrandTotal
        val reconciled = unassignedCount == 0 && abs(computedGrandTotal - targetTotal) <= 100L

        // If reconciled within small rounding tolerance (e.g., thermal receipt rounded Rs. 1), absorb difference into largest share
        if (unassignedCount == 0 && expectedGrandTotalMinor > 0L && computedGrandTotal != expectedGrandTotalMinor && abs(computedGrandTotal - expectedGrandTotalMinor) <= 100L) {
            val diff = expectedGrandTotalMinor - computedGrandTotal
            val largestMember = totalsByMember.maxByOrNull { it.value }?.key
            if (largestMember != null) {
                totalsByMember[largestMember] = (totalsByMember[largestMember]!! + diff).coerceAtLeast(0L)
            }
        }

        val finalSum = totalsByMember.values.sum().coerceAtLeast(1L)
        val allocations = memberIds.map { id ->
            val amt = totalsByMember[id] ?: 0L
            SplitAllocation(
                memberId = id,
                amountMinorUnits = amt,
                percentageBps = (amt * 10_000L) / finalSum,
                shares = 1L
            )
        }

        return ItemizedBreakdown(
            allocations = allocations,
            preTaxSubtotalByMember = preTax,
            proratedTaxByMember = proratedTax,
            proratedServiceByMember = proratedService,
            proratedDiscountByMember = proratedDiscount,
            assignedItemsTotalMinor = assignedItemsSum,
            unassignedItemsCount = unassignedCount,
            computedGrandTotalMinor = allocations.sumOf { it.amountMinorUnits },
            expectedGrandTotalMinor = targetTotal,
            isReconciled = unassignedCount == 0 && allocations.sumOf { it.amountMinorUnits } == targetTotal
        )
    }

    private fun proratePool(poolMinor: Long, weightsByMember: Map<String, Long>): Map<String, Long> {
        if (poolMinor <= 0L || weightsByMember.isEmpty()) {
            return weightsByMember.keys.associateWith { 0L }
        }
        val totalWeight = weightsByMember.values.sum()
        if (totalWeight <= 0L) return weightsByMember.keys.associateWith { 0L }

        val entries = weightsByMember.entries.map { (id, w) ->
            val exact = (poolMinor * w) / totalWeight
            val rem = (poolMinor * w) % totalWeight
            Triple(id, exact, rem)
        }.toMutableList()

        var leftover = (poolMinor - entries.sumOf { it.second }).toInt()
        val sortedByRem = entries.indices.sortedByDescending { entries[it].third }
        for (idx in sortedByRem) {
            if (leftover <= 0) break
            val cur = entries[idx]
            entries[idx] = Triple(cur.first, cur.second + 1L, cur.third)
            leftover--
        }
        return entries.associate { it.first to it.second }
    }

    /**
     * Graph-Based Minimum Cash Flow Debt Simplifier.
     * Computes:
     * 1. Net balance per member across unsettled expenses and settlements.
     * 2. Raw pairwise debt edges (netted only per direct pair).
     * 3. Simplified minimum-cash-flow settlement transfers pairing largest debtor with largest creditor.
     */
    fun computeDebtSimplification(
        members: List<LedgerMember>,
        expenses: List<ExpenseRecord>,
        settlements: List<SettlementRecord>,
        groupIdFilter: String? = null,
        currency: CurrencyCode = CurrencyCode.INR
    ): DebtSimplificationReport {
        val filteredExpenses = expenses.filter { exp ->
            !exp.settled && (groupIdFilter == null || exp.groupId == groupIdFilter)
        }
        val filteredSettlements = settlements.filter { set ->
            groupIdFilter == null || set.groupId == groupIdFilter
        }

        val netMap = linkedMapOf<String, Long>()
        members.forEach { netMap[it.id] = 0L }

        // Pairwise directed ledger: (debtor -> creditor) -> minorUnits
        val pairwise = mutableMapOf<Pair<String, String>, Long>()

        fun addPairwiseDebt(debtor: String, creditor: String, amount: Long) {
            if (debtor == creditor || amount <= 0L) return
            val reverseKey = creditor to debtor
            val existingReverse = pairwise[reverseKey] ?: 0L
            if (existingReverse > 0L) {
                if (existingReverse > amount) {
                    pairwise[reverseKey] = existingReverse - amount
                } else if (existingReverse < amount) {
                    pairwise.remove(reverseKey)
                    val forwardKey = debtor to creditor
                    pairwise[forwardKey] = (pairwise[forwardKey] ?: 0L) + (amount - existingReverse)
                } else {
                    pairwise.remove(reverseKey)
                }
            } else {
                val forwardKey = debtor to creditor
                pairwise[forwardKey] = (pairwise[forwardKey] ?: 0L) + amount
            }
        }

        // Accumulate FX-normalized expenses into net balances AND raw pairwise graph
        filteredExpenses.forEach { expense ->
            val fxOverride = if (expense.currencyEnum != currency) expense.fxRateToBaseMicros else null
            val convertedPayers = expense.payers.map { p ->
                p.memberId to convertMinorUnits(p.amountMinorUnits, expense.currencyEnum, currency, fxOverride)
            }
            val convertedAllocs = expense.allocations.map { a ->
                a.memberId to convertMinorUnits(a.amountMinorUnits, expense.currencyEnum, currency, fxOverride)
            }
            val payersSum = convertedPayers.sumOf { it.second }
            val allocsSum = convertedAllocs.sumOf { it.second }
            val normalizedAllocs = if (payersSum > 0L && allocsSum != payersSum && convertedAllocs.isNotEmpty()) {
                val diff = payersSum - allocsSum
                val maxIdx = convertedAllocs.indices.maxByOrNull { convertedAllocs[it].second } ?: 0
                convertedAllocs.mapIndexed { idx, pair ->
                    if (idx == maxIdx) pair.first to (pair.second + diff).coerceAtLeast(0L) else pair
                }
            } else convertedAllocs

            convertedPayers.forEach { (payerId, paidAmt) ->
                netMap[payerId] = (netMap[payerId] ?: 0L) + paidAmt
            }
            val payerWeightMap = convertedPayers.toMap()
            normalizedAllocs.forEach { (memberId, shareAmt) ->
                netMap[memberId] = (netMap[memberId] ?: 0L) - shareAmt
                val payerShares = proratePool(shareAmt, payerWeightMap)
                payerShares.forEach { (payerId, owedToPayer) ->
                    if (owedToPayer > 0L && memberId != payerId) {
                        addPairwiseDebt(memberId, payerId, owedToPayer)
                    }
                }
            }
        }

        // Apply recorded settlements (FX-normalized to target currency)
        filteredSettlements.forEach { s ->
            val convertedAmt = convertMinorUnits(s.amountMinorUnits, s.currencyEnum, currency)
            netMap[s.fromMemberId] = (netMap[s.fromMemberId] ?: 0L) + convertedAmt
            netMap[s.toMemberId] = (netMap[s.toMemberId] ?: 0L) - convertedAmt
            addPairwiseDebt(s.toMemberId, s.fromMemberId, convertedAmt)
        }

        val rawTransfers = pairwise.entries
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .map { (pair, amt) ->
                DebtTransfer(
                    fromMemberId = pair.first,
                    toMemberId = pair.second,
                    amountMinorUnits = amt,
                    currency = currency,
                    groupId = groupIdFilter
                )
            }

        // Minimum Cash Flow greedy pairing: largest debtor <-> largest creditor
        val debtors = netMap.entries
            .filter { it.value < 0L }
            .map { it.key to -it.value }
            .toMutableList()
        val creditors = netMap.entries
            .filter { it.value > 0L }
            .map { it.key to it.value }
            .toMutableList()

        val simplified = mutableListOf<DebtTransfer>()
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.second }
            creditors.sortByDescending { it.second }

            val (debtorId, debtAmt) = debtors.removeAt(0)
            val (creditorId, credAmt) = creditors.removeAt(0)
            val settleAmt = minOf(debtAmt, credAmt)

            if (settleAmt > 0L) {
                simplified.add(
                    DebtTransfer(
                        fromMemberId = debtorId,
                        toMemberId = creditorId,
                        amountMinorUnits = settleAmt,
                        currency = currency,
                        groupId = groupIdFilter
                    )
                )
            }
            val remDebt = debtAmt - settleAmt
            val remCred = credAmt - settleAmt
            if (remDebt > 0L) debtors.add(debtorId to remDebt)
            if (remCred > 0L) creditors.add(creditorId to remCred)
        }

        val balances = netMap.entries.map { MemberBalance(it.key, it.value) }
        val saved = (rawTransfers.size - simplified.size).coerceAtLeast(0)

        return DebtSimplificationReport(
            balances = balances,
            rawTransfers = rawTransfers,
            simplifiedTransfers = simplified,
            transfersSaved = saved
        )
    }

    fun computeCategoryBreakdown(expenses: List<ExpenseRecord>): List<CategorySpend> {
        val total = expenses.sumOf { it.totalMinorUnits }
        if (total <= 0L) return emptyList()
        return expenses.groupBy { it.categoryEnum }
            .map { (category, list) ->
                val sum = list.sumOf { it.totalMinorUnits }
                CategorySpend(
                    category = category,
                    totalMinorUnits = sum,
                    percentage = (sum.toFloat() / total.toFloat()) * 100f
                )
            }
            .sortedByDescending { it.totalMinorUnits }
    }

    /**
     * Builds a standards-compliant UPI deep link URI (`upi://pay`).
     */
    fun buildUpiDeepLink(
        payeeVpa: String,
        payeeName: String,
        amountMinorUnits: Long,
        currency: CurrencyCode = CurrencyCode.INR,
        note: String = "SplitFlow Settlement"
    ): String {
        val amountFormatted = "%.2f".format(amountMinorUnits / 100.0)
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", payeeVpa.ifBlank { "splitflow@okaxis" })
            .appendQueryParameter("pn", payeeName.ifBlank { "SplitFlow Member" })
            .appendQueryParameter("am", amountFormatted)
            .appendQueryParameter("cu", currency.name)
            .appendQueryParameter("tn", note.take(50))
            .build()
            .toString()
    }

    /**
     * Generates a crisp deterministic 21x21 QR-style matrix with standard 3 finder patterns
     * seeded by the UPI payload for instant visual rendering in the Settle Up drawer.
     */
    fun generateDeterministicQrMatrix(payload: String, size: Int = 21): Array<BooleanArray> {
        val grid = Array(size) { BooleanArray(size) }

        fun drawFinder(top: Int, left: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isInner = r in 2..4 && c in 2..4
                    if (top + r < size && left + c < size) {
                        grid[top + r][left + c] = isBorder || isInner
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        var seed = payload.hashCode().toLong() * 2654435761L
        for (r in 0 until size) {
            for (c in 0 until size) {
                val inTopLeft = r < 8 && c < 8
                val inTopRight = r < 8 && c >= size - 8
                val inBottomLeft = r >= size - 8 && c < 8
                if (!inTopLeft && !inTopRight && !inBottomLeft) {
                    seed = (seed * 6364136223846793005L + 1442695040888963407L)
                    grid[r][c] = ((seed ushr 33) and 1L) == 1L
                }
            }
        }
        return grid
    }

    fun exportLedgerToCsv(
        scopeName: String,
        members: List<LedgerMember>,
        expenses: List<ExpenseRecord>,
        settlements: List<SettlementRecord>
    ): String {
        val memberMap = members.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Type,Date,Title,Category,Currency,Amount,SplitType,PaidBy,Participants,Status,ShareLink")
        expenses.forEach { exp ->
            val payersStr = exp.payers.joinToString(" & ") { p ->
                "${memberMap[p.memberId]?.name ?: p.memberId} (${minorToMajorInput(p.amountMinorUnits)})"
            }
            val allocStr = exp.allocations.joinToString("; ") { a ->
                "${memberMap[a.memberId]?.name ?: a.memberId}: ${minorToMajorInput(a.amountMinorUnits)}"
            }
            val safeTitle = exp.title.replace("\"", "\"\"")
            val status = if (exp.settled) "Settled" else "Open"
            sb.appendLine(
                "EXPENSE,${exp.dateLabel},\"$safeTitle\",${exp.categoryEnum.label},${exp.currency},${minorToMajorInput(exp.totalMinorUnits)},${exp.splitType},\"$payersStr\",\"$allocStr\",$status,https://splitflow.app/#/share/${exp.shareCode}"
            )
        }
        settlements.forEach { set ->
            val from = memberMap[set.fromMemberId]?.name ?: set.fromMemberId
            val to = memberMap[set.toMemberId]?.name ?: set.toMemberId
            val safeNote = (set.note ?: "Settlement").replace("\"", "\"\"")
            sb.appendLine(
                "SETTLEMENT,${set.dateLabel},\"$from -> $to\",${set.paymentMethodEnum.label},${set.currency},${minorToMajorInput(set.amountMinorUnits)},TRANSFER,\"$from\",\"$to ($safeNote)\",Settled,-"
            )
        }
        return sb.toString()
    }

    fun buildPrintableSettlementSummary(
        title: String,
        members: List<LedgerMember>,
        expenses: List<ExpenseRecord>,
        report: DebtSimplificationReport,
        currency: CurrencyCode
    ): String {
        val memberMap = members.associateBy { it.id }
        val totalSpend = expenses.sumOf { it.totalMinorUnits }
        val sb = StringBuilder()
        sb.appendLine("══════════════════════════════════")
        sb.appendLine("SPLITFLOW SETTLEMENT SUMMARY")
        sb.appendLine("Scope: $title")
        sb.appendLine("Total Spend: ${formatMinorUnits(totalSpend, currency)}")
        sb.appendLine("Expenses Logged: ${expenses.size}")
        sb.appendLine("──────────────────────────────────")
        sb.appendLine("SIMPLIFIED SETTLEMENT PLAN (Saved ${report.transfersSaved} transfers)")
        if (report.simplifiedTransfers.isEmpty()) {
            sb.appendLine("All balances are settled!")
        } else {
            report.simplifiedTransfers.forEachIndexed { i, t ->
                val from = memberMap[t.fromMemberId]?.name ?: t.fromMemberId
                val to = memberMap[t.toMemberId]?.name ?: t.toMemberId
                sb.appendLine("${i + 1}. $from pays $to -> ${formatMinorUnits(t.amountMinorUnits, currency)}")
            }
        }
        sb.appendLine("──────────────────────────────────")
        sb.appendLine("NET MEMBER BALANCES")
        report.balances.sortedByDescending { it.netMinorUnits }.forEach { b ->
            val name = memberMap[b.memberId]?.name ?: b.memberId
            val tag = if (memberMap[b.memberId]?.guestProfile == true) " [Guest]" else ""
            sb.appendLine("• $name$tag: ${formatMinorUnits(b.netMinorUnits, currency, showPlusSign = true)}")
        }
        sb.appendLine("══════════════════════════════════")
        return sb.toString()
    }

    /**
     * Anomaly Guard: inspects a candidate expense before saving for:
     * 1. Possible duplicate receipts (same/similar merchant + exact or near-identical amount within 2 days)
     * 2. High tax + tip/service charge spikes (>25% of subtotal)
     * 3. Unusual spend outliers (>2.5x group median expense)
     */
    fun detectExpenseAnomalies(
        candidateTitle: String,
        candidateMinor: Long,
        candidateCurrency: CurrencyCode,
        candidateDate: String,
        candidateTaxMinor: Long = 0L,
        candidateServiceMinor: Long = 0L,
        existingExpenses: List<ExpenseRecord>,
        groupId: String?,
        editingExpenseId: String? = null
    ): List<ExpenseAnomaly> {
        if (candidateMinor <= 0L) return emptyList()
        val alerts = mutableListOf<ExpenseAnomaly>()
        val normalizedTitle = candidateTitle.trim().lowercase(Locale.US)

        // 1. Duplicate receipt check
        val dupMatch = existingExpenses.firstOrNull { exp ->
            if (editingExpenseId != null && exp.id == editingExpenseId) return@firstOrNull false
            if (exp.currencyEnum != candidateCurrency) return@firstOrNull false
            val amountDelta = abs(exp.totalMinorUnits - candidateMinor)
            val amountClose = amountDelta <= maxOf(100L, candidateMinor / 50L)
            if (!amountClose) return@firstOrNull false
            val daysGap = abs(daysUntilIso(exp.dateLabel, candidateDate) ?: 99)
            if (daysGap > 2) return@firstOrNull false
            val existingNorm = exp.title.trim().lowercase(Locale.US)
            normalizedTitle.isNotBlank() && (
                existingNorm == normalizedTitle ||
                    existingNorm.contains(normalizedTitle) ||
                    normalizedTitle.contains(existingNorm)
                )
        }
        if (dupMatch != null) {
            alerts.add(
                ExpenseAnomaly(
                    title = "Possible duplicate receipt",
                    detail = "\"${dupMatch.title}\" for ${formatMinorUnits(dupMatch.totalMinorUnits, dupMatch.currencyEnum)} was already logged on ${dupMatch.dateLabel}.",
                    severity = AnomalySeverity.HIGH
                )
            )
        }

        // 2. Tax + service spike guard (>25% combined)
        val combinedExtras = candidateTaxMinor + candidateServiceMinor
        val preExtras = (candidateMinor - combinedExtras).coerceAtLeast(1L)
        if (combinedExtras > 0L) {
            val extraRatio = combinedExtras.toFloat() / preExtras.toFloat()
            if (extraRatio > 0.25f) {
                val pct = (extraRatio * 100f).toInt()
                alerts.add(
                    ExpenseAnomaly(
                        title = "Unusual tax & tip spike ($pct%)",
                        detail = "Tax + service charges (${formatMinorUnits(combinedExtras, candidateCurrency)}) exceed 25% of the pre-tax bill.",
                        severity = AnomalySeverity.WARNING
                    )
                )
            }
        }

        // 3. Group spend outlier guard (>2.5x median when >= 3 expenses exist in group)
        val scopedExpenses = existingExpenses.filter { exp ->
            (groupId == null || exp.groupId == groupId) &&
                exp.id != editingExpenseId &&
                exp.currencyEnum == candidateCurrency
        }
        if (scopedExpenses.size >= 3) {
            val sorted = scopedExpenses.map { it.totalMinorUnits }.sorted()
            val median = sorted[sorted.size / 2].coerceAtLeast(1L)
            if (candidateMinor >= (median * 5L) / 2L && candidateMinor >= 100_000L) {
                val ratio = candidateMinor.toDouble() / median.toDouble()
                alerts.add(
                    ExpenseAnomaly(
                        title = "Spend outlier (${"%.1f".format(Locale.US, ratio)}× typical)",
                        detail = "This entry is well above the typical ${formatMinorUnits(median, candidateCurrency)} bill here — double-check decimals.",
                        severity = AnomalySeverity.INFO
                    )
                )
            }
        }

        return alerts
    }

    /**
     * Monthly Cash-Flow & Spend Timeline: aggregates the last 6 active months into
     * total spend, self lent (paid > share), and self borrowed (share > paid).
     */
    fun computeMonthlyCashFlow(
        expenses: List<ExpenseRecord>,
        selfMemberId: String?,
        baseCurrency: CurrencyCode
    ): List<MonthlyCashFlowPoint> {
        if (expenses.isEmpty()) return emptyList()
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val grouped = expenses.groupBy { exp ->
            if (exp.dateLabel.length >= 7) exp.dateLabel.substring(0, 7) else "2026-11"
        }
        return grouped.entries
            .sortedBy { it.key }
            .takeLast(6)
            .map { (monthKey, list) ->
                var totalSpend = 0L
                var lent = 0L
                var borrowed = 0L
                val catTotals = mutableMapOf<ExpenseCategory, Long>()

                list.forEach { exp ->
                    val fx = if (exp.currencyEnum != baseCurrency) exp.fxRateToBaseMicros else null
                    val totalBase = convertMinorUnits(exp.totalMinorUnits, exp.currencyEnum, baseCurrency, fx)
                    totalSpend += totalBase
                    catTotals[exp.categoryEnum] = (catTotals[exp.categoryEnum] ?: 0L) + totalBase

                    if (selfMemberId != null) {
                        val paidSelf = exp.payers.filter { it.memberId == selfMemberId }
                            .sumOf { convertMinorUnits(it.amountMinorUnits, exp.currencyEnum, baseCurrency, fx) }
                        val shareSelf = exp.allocations.filter { it.memberId == selfMemberId }
                            .sumOf { convertMinorUnits(it.amountMinorUnits, exp.currencyEnum, baseCurrency, fx) }
                        val delta = paidSelf - shareSelf
                        if (delta > 0L) lent += delta else if (delta < 0L) borrowed += -delta
                    }
                }

                val topCat = catTotals.maxByOrNull { it.value }?.key ?: ExpenseCategory.OTHER
                val monthIdx = monthKey.substringAfter("-", "11").toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 10
                MonthlyCashFlowPoint(
                    monthKey = monthKey,
                    monthLabel = monthNames[monthIdx],
                    totalSpendMinor = totalSpend,
                    lentMinor = lent,
                    borrowedMinor = borrowed,
                    topCategoryHex = topCat.colorHex
                )
            }
    }

    /**
     * Daily Burn Meter for a group: computes active days, average daily burn, peak day,
     * and projected days remaining against the configured group budget cap.
     */
    fun computeDailyBurnStats(
        group: ExpenseGroup,
        groupExpenses: List<ExpenseRecord>,
        budgetStatus: GroupBudgetStatus
    ): DailyBurnStats {
        if (groupExpenses.isEmpty()) {
            return DailyBurnStats(
                activeDays = 0,
                avgDailyMinor = 0L,
                peakDayLabel = "—",
                peakDayMinor = 0L,
                projectedDaysLeft = null
            )
        }
        val byDay = groupExpenses.groupBy { it.dateLabel.ifBlank { "2026-11-21" } }
            .mapValues { (_, list) ->
                list.sumOf { exp ->
                    convertMinorUnits(
                        amountMinor = exp.totalMinorUnits,
                        from = exp.currencyEnum,
                        to = group.currencyEnum,
                        rateMicrosOverride = exp.fxRateToBaseMicros
                    )
                }
            }
        val activeDays = byDay.size.coerceAtLeast(1)
        val total = byDay.values.sum()
        val avgDaily = (total / activeDays).coerceAtLeast(1L)
        val peakEntry = byDay.maxByOrNull { it.value }
        val projectedDays = if (budgetStatus.budgetMinorUnits != null && avgDaily > 0L) {
            if (budgetStatus.remainingMinorUnits <= 0L) 0
            else (budgetStatus.remainingMinorUnits / avgDaily).toInt().coerceAtMost(999)
        } else null

        return DailyBurnStats(
            activeDays = activeDays,
            avgDailyMinor = avgDaily,
            peakDayLabel = peakEntry?.key ?: "—",
            peakDayMinor = peakEntry?.value ?: 0L,
            projectedDaysLeft = projectedDays
        )
    }

    /**
     * Deterministic Trip Wrap-Up & Ledger Story metrics for a group.
     */
    fun computeTripWrapUpMetrics(
        group: ExpenseGroup,
        members: List<LedgerMember>,
        groupExpenses: List<ExpenseRecord>,
        groupSettlements: List<SettlementRecord>
    ): TripWrapUpMetrics {
        val memberMap = members.associateBy { it.id }
        val report = computeDebtSimplification(
            members = members,
            expenses = groupExpenses,
            settlements = groupSettlements,
            groupIdFilter = group.id,
            currency = group.currencyEnum
        )
        val paidByMember = mutableMapOf<String, Long>()
        val catTotals = mutableMapOf<ExpenseCategory, Long>()
        var totalSpend = 0L
        val activeDays = mutableSetOf<String>()

        groupExpenses.forEach { exp ->
            val fx = if (exp.currencyEnum != group.currencyEnum) exp.fxRateToBaseMicros else null
            val expTotal = convertMinorUnits(exp.totalMinorUnits, exp.currencyEnum, group.currencyEnum, fx)
            totalSpend += expTotal
            activeDays.add(exp.dateLabel.ifBlank { "2026-11-21" })
            catTotals[exp.categoryEnum] = (catTotals[exp.categoryEnum] ?: 0L) + expTotal
            exp.payers.forEach { p ->
                val pAmt = convertMinorUnits(p.amountMinorUnits, exp.currencyEnum, group.currencyEnum, fx)
                paidByMember[p.memberId] = (paidByMember[p.memberId] ?: 0L) + pAmt
            }
        }

        val topSpenderEntry = paidByMember.maxByOrNull { it.value }
        val topSpenderName = topSpenderEntry?.key?.let { memberMap[it]?.name } ?: "—"
        val topCatEntry = catTotals.maxByOrNull { it.value }
        val topCatPct = if (totalSpend > 0L && topCatEntry != null) {
            ((topCatEntry.value * 100L) / totalSpend).toInt()
        } else 0
        val avgDaily = if (activeDays.isNotEmpty()) totalSpend / activeDays.size else 0L

        return TripWrapUpMetrics(
            scopeName = group.name,
            currency = group.currencyEnum,
            totalSpendMinor = totalSpend,
            expenseCount = groupExpenses.size,
            topSpenderName = topSpenderName,
            topSpenderMinor = topSpenderEntry?.value ?: 0L,
            biggestCategoryLabel = topCatEntry?.key?.label ?: "General",
            biggestCategoryPercent = topCatPct,
            transfersSaved = report.transfersSaved,
            avgDailyMinor = avgDaily
        )
    }
}
