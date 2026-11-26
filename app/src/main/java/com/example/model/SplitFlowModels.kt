package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CurrencyCode(val symbol: String, val label: String) {
    INR("₹", "Indian Rupee (₹)"),
    USD("$", "US Dollar ($)"),
    EUR("€", "Euro (€)"),
    GBP("£", "British Pound (£)"),
    THB("฿", "Thai Baht (฿)");

    companion object {
        fun fromRaw(raw: String?): CurrencyCode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: INR
    }
}

enum class GroupCategory(val label: String) {
    TRIP("Trip"),
    HOME("Home"),
    EVENT("Event"),
    COUPLE("Couple"),
    PERSONAL("Personal");

    companion object {
        fun fromRaw(raw: String?): GroupCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TRIP
    }
}

enum class ExpenseCategory(val label: String, val colorHex: String) {
    FOOD("Dining & Drinks", "#0D9488"),
    TRAVEL("Travel & Cab", "#0284C7"),
    STAY("Stay & Villa", "#4F46E5"),
    GROCERIES("Groceries", "#10B981"),
    UTILITIES("Bills & Utilities", "#F59E0B"),
    ENTERTAINMENT("Activities", "#7C3AED"),
    SHOPPING("Shopping", "#E11D48"),
    OTHER("Other", "#64748B");

    companion object {
        fun fromRaw(raw: String?): ExpenseCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

enum class SplitType(val label: String) {
    EQUAL("Equal"),
    EXACT("Exact"),
    PERCENTAGE("Percent"),
    SHARES("Shares"),
    ITEMIZED("Itemized");

    companion object {
        fun fromRaw(raw: String?): SplitType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EQUAL
    }
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    BANK("Bank Transfer"),
    CASH("Cash"),
    CARD("Card");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UPI
    }
}

enum class RecurringFrequency(val label: String) {
    NONE("One-time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly");

    companion object {
        fun fromRaw(raw: String?): RecurringFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class BudgetHealth {
    NONE,
    HEALTHY,
    WARNING,
    EXCEEDED
}

enum class DietPreference(
    val label: String,
    val shortLabel: String,
    val colorHex: String
) {
    VEG("Veg", "VEG", "#10B981"),
    NON_VEG("Non-Veg", "NON-VEG", "#E11D48"),
    EGGETARIAN("Egg + Veg", "EGG", "#F59E0B"),
    VEGAN("Vegan", "VEGAN", "#14B8A6");

    val eatsNonVeg: Boolean get() = this == NON_VEG

    companion object {
        fun fromRaw(raw: String?): DietPreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NON_VEG
    }
}

enum class LineItemTag(
    val label: String,
    val shortLabel: String,
    val colorHex: String,
    val isAlcoholic: Boolean = false,
    val isDrink: Boolean = false
) {
    VEG("Veg Food", "VEG", "#10B981"),
    NON_VEG("Non-Veg Food", "NON-VEG", "#E11D48"),
    ALCOHOL("Cocktails & Spirits", "BAR", "#A855F7", isAlcoholic = true, isDrink = true),
    BEER("Beer & Pitchers", "BEER", "#F59E0B", isAlcoholic = true, isDrink = true),
    MOCKTAIL("Mocktails & Soft Drinks", "SOFT", "#06B6D4", isAlcoholic = false, isDrink = true),
    DESSERT("Desserts & Coffee", "SWEET", "#FB923C"),
    HOOKAH_EXTRA("Hookah & Extras", "EXTRA", "#EC4899"),
    SHARED("Shared Table", "SHARED", "#64748B");

    companion object {
        fun fromRaw(raw: String?): LineItemTag =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SHARED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val upiVpa: String? = null,
    val defaultCurrency: String = CurrencyCode.INR.name,
    val darkMode: Boolean = true,
    val seededDemo: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val currencyEnum: CurrencyCode get() = CurrencyCode.fromRaw(defaultCurrency)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to displayName.trim().ifBlank { "SplitFlow User" }.take(80),
            "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
            "upiVpa" to upiVpa?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            "defaultCurrency" to CurrencyCode.fromRaw(defaultCurrency).name,
            "darkMode" to darkMode,
            "seededDemo" to seededDemo,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class LedgerMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String? = null,
    val upiVpa: String? = null,
    val avatarColorHex: String = "#0D9488",
    val selfProfile: Boolean = false,
    val guestProfile: Boolean = false,
    val claimedAtLabel: String? = null,
    val dietPreference: String? = null,
    val drinksAlcohol: Boolean? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val dietEnum: DietPreference get() = DietPreference.fromRaw(dietPreference)
    val isAlcoholDrinker: Boolean get() = drinksAlcohol ?: true

    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts.first().first()}${parts.last().first()}".uppercase()
            }
        }

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "name" to name.trim().ifBlank { "Friend" }.take(80),
            "email" to email?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
            "upiVpa" to upiVpa?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            "avatarColorHex" to avatarColorHex.take(9),
            "selfProfile" to selfProfile,
            "guestProfile" to guestProfile,
            "claimedAtLabel" to claimedAtLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
            "dietPreference" to dietPreference?.let { DietPreference.fromRaw(it).name },
            "drinksAlcohol" to drinksAlcohol,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class ExpenseGroup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = GroupCategory.TRIP.name,
    val currency: String = CurrencyCode.INR.name,
    val memberIds: List<String> = emptyList(),
    val accentColorHex: String = "#14B8A6",
    val archived: Boolean = false,
    val budgetMinorUnits: Long? = null,
    val splitMemoryNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: GroupCategory get() = GroupCategory.fromRaw(category)
    val currencyEnum: CurrencyCode get() = CurrencyCode.fromRaw(currency)

    fun toCreateMap(uid: String): Map<String, Any> {
        val validMembers = memberIds.filter { it.isNotBlank() }.distinct().take(50)
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "name" to name.trim().ifBlank { "New Group" }.take(80),
            "category" to GroupCategory.fromRaw(category).name,
            "currency" to CurrencyCode.fromRaw(currency).name,
            "memberIds" to validMembers,
            "accentColorHex" to accentColorHex.take(9),
            "archived" to archived,
            "budgetMinorUnits" to budgetMinorUnits?.coerceAtLeast(0L),
            "splitMemoryNotes" to splitMemoryNotes?.trim()?.takeIf { it.isNotEmpty() }?.take(400),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class PayerEntry(
    val memberId: String = "",
    val amountMinorUnits: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "memberId" to memberId,
        "amountMinorUnits" to amountMinorUnits
    )

    companion object {
        fun fromMap(map: Map<*, *>): PayerEntry = PayerEntry(
            memberId = map["memberId"] as? String ?: "",
            amountMinorUnits = (map["amountMinorUnits"] as? Number)?.toLong() ?: 0L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class SplitAllocation(
    val memberId: String = "",
    val amountMinorUnits: Long = 0L,
    val percentageBps: Long = 0L,
    val shares: Long = 1L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "memberId" to memberId,
        "amountMinorUnits" to amountMinorUnits,
        "percentageBps" to percentageBps,
        "shares" to shares
    )

    companion object {
        fun fromMap(map: Map<*, *>): SplitAllocation = SplitAllocation(
            memberId = map["memberId"] as? String ?: "",
            amountMinorUnits = (map["amountMinorUnits"] as? Number)?.toLong() ?: 0L,
            percentageBps = (map["percentageBps"] as? Number)?.toLong() ?: 0L,
            shares = (map["shares"] as? Number)?.toLong() ?: 1L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ReceiptLineItem(
    val itemId: String = "",
    val name: String = "",
    val quantity: Long = 1L,
    val unitPriceMinor: Long = 0L,
    val totalPriceMinor: Long = 0L,
    val assignedMemberIds: List<String> = emptyList(),
    val itemTag: String? = null
) {
    val tagEnum: LineItemTag get() = LineItemTag.fromRaw(itemTag)

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "itemId" to itemId,
            "name" to name.take(100),
            "quantity" to quantity,
            "unitPriceMinor" to unitPriceMinor,
            "totalPriceMinor" to totalPriceMinor,
            "assignedMemberIds" to assignedMemberIds.distinct().take(50)
        )
        itemTag?.let { map["itemTag"] = LineItemTag.fromRaw(it).name }
        return map
    }

    companion object {
        fun fromMap(map: Map<*, *>): ReceiptLineItem {
            val rawAssigned = (map["assignedMemberIds"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()
            return ReceiptLineItem(
                itemId = map["itemId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                quantity = (map["quantity"] as? Number)?.toLong() ?: 1L,
                unitPriceMinor = (map["unitPriceMinor"] as? Number)?.toLong() ?: 0L,
                totalPriceMinor = (map["totalPriceMinor"] as? Number)?.toLong() ?: 0L,
                assignedMemberIds = rawAssigned,
                itemTag = map["itemTag"] as? String
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ExpenseRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String? = null,
    val title: String = "",
    val notes: String? = null,
    val category: String = ExpenseCategory.FOOD.name,
    val currency: String = CurrencyCode.INR.name,
    val totalMinorUnits: Long = 0L,
    val splitType: String = SplitType.EQUAL.name,
    val dateLabel: String = "",
    val shareCode: String = "",
    val aiExtracted: Boolean = false,
    val aiConfidenceNote: String? = null,
    val settled: Boolean = false,
    val payers: List<PayerEntry> = emptyList(),
    val allocations: List<SplitAllocation> = emptyList(),
    val lineItems: List<ReceiptLineItem> = emptyList(),
    val taxMinorUnits: Long = 0L,
    val serviceChargeMinorUnits: Long = 0L,
    val discountMinorUnits: Long = 0L,
    val recurringFrequency: String? = null,
    val nextDueDateLabel: String? = null,
    val receiptPhotoPath: String? = null,
    val fxRateToBaseMicros: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val categoryEnum: ExpenseCategory get() = ExpenseCategory.fromRaw(category)
    val currencyEnum: CurrencyCode get() = CurrencyCode.fromRaw(currency)
    val splitTypeEnum: SplitType get() = SplitType.fromRaw(splitType)
    val recurringEnum: RecurringFrequency get() = RecurringFrequency.fromRaw(recurringFrequency)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "groupId" to groupId?.trim()?.takeIf { it.isNotEmpty() }?.take(128),
            "title" to title.trim().ifBlank { "Shared Expense" }.take(120),
            "notes" to notes?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
            "category" to ExpenseCategory.fromRaw(category).name,
            "currency" to CurrencyCode.fromRaw(currency).name,
            "totalMinorUnits" to totalMinorUnits.coerceAtLeast(1L),
            "splitType" to SplitType.fromRaw(splitType).name,
            "dateLabel" to dateLabel.trim().ifBlank { "2026-11-21" }.take(40),
            "shareCode" to shareCode.trim().ifBlank { "sf-${System.currentTimeMillis() % 100000}" }.take(64),
            "aiExtracted" to aiExtracted,
            "aiConfidenceNote" to aiConfidenceNote?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "settled" to settled,
            "payers" to payers.take(30).map { it.toMap() },
            "allocations" to allocations.take(50).map { it.toMap() },
            "lineItems" to lineItems.take(100).map { it.toMap() },
            "taxMinorUnits" to taxMinorUnits.coerceAtLeast(0L),
            "serviceChargeMinorUnits" to serviceChargeMinorUnits.coerceAtLeast(0L),
            "discountMinorUnits" to discountMinorUnits.coerceAtLeast(0L),
            "recurringFrequency" to recurringFrequency?.let { RecurringFrequency.fromRaw(it).name },
            "nextDueDateLabel" to nextDueDateLabel?.trim()?.takeIf { it.isNotEmpty() }?.take(40),
            "receiptPhotoPath" to receiptPhotoPath?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "fxRateToBaseMicros" to fxRateToBaseMicros?.takeIf { it >= 1L },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title.trim().ifBlank { "Shared Expense" }.take(120),
            "category" to ExpenseCategory.fromRaw(category).name,
            "currency" to CurrencyCode.fromRaw(currency).name,
            "totalMinorUnits" to totalMinorUnits.coerceAtLeast(1L),
            "splitType" to SplitType.fromRaw(splitType).name,
            "dateLabel" to dateLabel.trim().ifBlank { "2026-11-21" }.take(40),
            "settled" to settled,
            "payers" to payers.take(30).map { it.toMap() },
            "allocations" to allocations.take(50).map { it.toMap() },
            "lineItems" to lineItems.take(100).map { it.toMap() },
            "taxMinorUnits" to taxMinorUnits.coerceAtLeast(0L),
            "serviceChargeMinorUnits" to serviceChargeMinorUnits.coerceAtLeast(0L),
            "discountMinorUnits" to discountMinorUnits.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        groupId?.trim()?.takeIf { it.isNotEmpty() }?.let { map["groupId"] = it.take(128) }
        notes?.trim()?.takeIf { it.isNotEmpty() }?.let { map["notes"] = it.take(500) }
        recurringFrequency?.let { map["recurringFrequency"] = RecurringFrequency.fromRaw(it).name }
        nextDueDateLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { map["nextDueDateLabel"] = it.take(40) }
        receiptPhotoPath?.trim()?.takeIf { it.isNotEmpty() }?.let { map["receiptPhotoPath"] = it.take(300) }
        fxRateToBaseMicros?.takeIf { it >= 1L }?.let { map["fxRateToBaseMicros"] = it }
        return map
    }
}

@Keep
@IgnoreExtraProperties
data class SettlementRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String? = null,
    val fromMemberId: String = "",
    val toMemberId: String = "",
    val amountMinorUnits: Long = 0L,
    val currency: String = CurrencyCode.INR.name,
    val paymentMethod: String = PaymentMethod.UPI.name,
    val note: String? = null,
    val paymentProofPath: String? = null,
    val dateLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val currencyEnum: CurrencyCode get() = CurrencyCode.fromRaw(currency)
    val paymentMethodEnum: PaymentMethod get() = PaymentMethod.fromRaw(paymentMethod)

    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "groupId" to groupId?.trim()?.takeIf { it.isNotEmpty() }?.take(128),
            "fromMemberId" to fromMemberId.trim().take(128),
            "toMemberId" to toMemberId.trim().take(128),
            "amountMinorUnits" to amountMinorUnits.coerceAtLeast(1L),
            "currency" to CurrencyCode.fromRaw(currency).name,
            "paymentMethod" to PaymentMethod.fromRaw(paymentMethod).name,
            "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            "paymentProofPath" to paymentProofPath?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "dateLabel" to dateLabel.trim().ifBlank { "2026-11-21" }.take(40),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "amountMinorUnits" to amountMinorUnits.coerceAtLeast(1L),
            "currency" to CurrencyCode.fromRaw(currency).name,
            "paymentMethod" to PaymentMethod.fromRaw(paymentMethod).name,
            "dateLabel" to dateLabel.trim().ifBlank { "2026-11-26" }.take(40),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        note?.trim()?.takeIf { it.isNotEmpty() }?.let { map["note"] = it.take(200) }
        paymentProofPath?.trim()?.takeIf { it.isNotEmpty() }?.let { map["paymentProofPath"] = it.take(300) }
        return map
    }
}

// --- DOMAIN & ANALYTICS MODELS ---

data class MemberBalance(
    val memberId: String,
    val netMinorUnits: Long // positive => owed to member, negative => member owes
)

data class DebtTransfer(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinorUnits: Long,
    val currency: CurrencyCode = CurrencyCode.INR,
    val groupId: String? = null
)

data class DebtSimplificationReport(
    val balances: List<MemberBalance>,
    val rawTransfers: List<DebtTransfer>,
    val simplifiedTransfers: List<DebtTransfer>,
    val transfersSaved: Int
)

data class CategorySpend(
    val category: ExpenseCategory,
    val totalMinorUnits: Long,
    val percentage: Float
)

data class ItemizedBreakdown(
    val allocations: List<SplitAllocation>,
    val preTaxSubtotalByMember: Map<String, Long>,
    val proratedTaxByMember: Map<String, Long>,
    val proratedServiceByMember: Map<String, Long>,
    val proratedDiscountByMember: Map<String, Long>,
    val assignedItemsTotalMinor: Long,
    val unassignedItemsCount: Int,
    val computedGrandTotalMinor: Long,
    val expectedGrandTotalMinor: Long,
    val isReconciled: Boolean
)

// --- AI STRUCTURED OUTPUT MODELS ---

data class ParsedReceipt(
    val merchantName: String,
    val date: String,
    val currency: CurrencyCode,
    val category: ExpenseCategory,
    val lineItems: List<ReceiptLineItem>,
    val subtotalMinor: Long,
    val taxMinor: Long,
    val serviceChargeMinor: Long,
    val discountMinor: Long,
    val grandTotalMinor: Long
)

enum class VoiceActionType {
    DIRECT_IOU,
    SHARED_EXPENSE,
    SETTLEMENT;

    companion object {
        fun fromRaw(raw: String): VoiceActionType =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: SHARED_EXPENSE
    }
}

data class ParsedVoiceParticipant(
    val memberId: String,
    val memberName: String,
    val amountMinorUnits: Long,
    val shares: Long = 1L,
    val percentageBps: Long = 0L
)

data class ParsedVoiceSplit(
    val transcript: String = "",
    val actionType: VoiceActionType = VoiceActionType.SHARED_EXPENSE,
    val title: String,
    val totalMinorUnits: Long,
    val currency: CurrencyCode,
    val paidByMemberId: String,
    val paidByMemberName: String = "",
    val category: ExpenseCategory,
    val splitType: SplitType,
    val participants: List<ParsedVoiceParticipant>,
    val aiConfidenceNote: String,
    val matchedGroupId: String? = null,
    val newMemberNames: List<String> = emptyList(),
    val assistantReply: String = "",
    val ledgerSummaryLine: String = "",
    val settlementFromMemberId: String? = null,
    val settlementFromMemberName: String? = null,
    val settlementToMemberId: String? = null,
    val settlementToMemberName: String? = null
)

data class VoiceExecutionResult(
    val parsed: ParsedVoiceSplit,
    val applied: Boolean,
    val createdMemberNames: List<String> = emptyList(),
    val savedExpenseId: String? = null,
    val savedSettlementId: String? = null,
    val resolvedGroupId: String? = null,
    val statusMessage: String = ""
)

data class GroupBudgetStatus(
    val spentMinorUnits: Long,
    val budgetMinorUnits: Long?,
    val progressRatio: Float,
    val remainingMinorUnits: Long,
    val health: BudgetHealth
)

data class SpendCoachInsight(
    val title: String,
    val detail: String,
    val potentialSavingsLabel: String,
    val category: String
)

data class SpendCoachReport(
    val healthScore: Int,
    val headline: String,
    val budgetAlertNote: String,
    val insights: List<SpendCoachInsight>
)

// --- V3 ANALYTICS, ANOMALY GUARD, TRIP STORY & BATCH IMPORT MODELS ---

enum class AnomalySeverity {
    INFO,
    WARNING,
    HIGH
}

data class ExpenseAnomaly(
    val title: String,
    val detail: String,
    val severity: AnomalySeverity
)

data class MonthlyCashFlowPoint(
    val monthKey: String,
    val monthLabel: String,
    val totalSpendMinor: Long,
    val lentMinor: Long,
    val borrowedMinor: Long,
    val topCategoryHex: String
)

data class DailyBurnStats(
    val activeDays: Int,
    val avgDailyMinor: Long,
    val peakDayLabel: String,
    val peakDayMinor: Long,
    val projectedDaysLeft: Int?
)

data class TripWrapUpMetrics(
    val scopeName: String,
    val currency: CurrencyCode,
    val totalSpendMinor: Long,
    val expenseCount: Int,
    val topSpenderName: String,
    val topSpenderMinor: Long,
    val biggestCategoryLabel: String,
    val biggestCategoryPercent: Int,
    val transfersSaved: Int,
    val avgDailyMinor: Long
)

data class TripStoryNarrative(
    val headline: String,
    val vibeTag: String,
    val highlights: List<String>,
    val closingQuip: String
)

enum class BatchItemStatus {
    QUEUED,
    SCANNING,
    READY,
    APPROVED,
    SKIPPED,
    ERROR
}

data class BatchReceiptItem(
    val id: String,
    val label: String,
    val savedPhotoPath: String? = null,
    val status: BatchItemStatus = BatchItemStatus.QUEUED,
    val parsed: ParsedReceipt? = null,
    val errorMessage: String? = null
)

sealed interface AsyncUiState<out T> {
    data object Idle : AsyncUiState<Nothing>
    data object Loading : AsyncUiState<Nothing>
    data class Success<T>(val data: T) : AsyncUiState<T>
    data class Error(val message: String) : AsyncUiState<Nothing>
}
