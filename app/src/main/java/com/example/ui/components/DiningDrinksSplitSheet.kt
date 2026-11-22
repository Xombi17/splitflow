package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.NoDrinks
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.DietPreference
import com.example.model.ExpenseCategory
import com.example.model.ExpenseRecord
import com.example.model.LedgerMember
import com.example.model.LineItemTag
import com.example.model.PayerEntry
import com.example.model.ReceiptLineItem
import com.example.model.SplitAllocation
import com.example.model.SplitType
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot
import java.util.UUID
import kotlin.math.roundToLong

private enum class DiningStudioMode(val label: String) {
    QUICK_BUCKETS("Quick Buckets"),
    BAR_MENU("Drinks & Dish Picker")
}

private data class MenuPreset(
    val id: String,
    val title: String,
    val tag: LineItemTag,
    val defaultPriceMinor: Long,
    val icon: ImageVector
)

private val DRINKS_SHELF_PRESETS = listOf(
    MenuPreset("beer_pint", "Beer Pint", LineItemTag.BEER, 35_000L, Icons.Rounded.SportsBar),
    MenuPreset("beer_pitcher", "Beer Pitcher", LineItemTag.BEER, 140_000L, Icons.Rounded.SportsBar),
    MenuPreset("cocktail", "Craft Cocktail", LineItemTag.ALCOHOL, 55_000L, Icons.Rounded.LocalBar),
    MenuPreset("whiskey_peg", "Whiskey / Gin Peg", LineItemTag.ALCOHOL, 45_000L, Icons.Rounded.WineBar),
    MenuPreset("wine_glass", "Wine Glass", LineItemTag.ALCOHOL, 50_000L, Icons.Rounded.WineBar),
    MenuPreset("shots", "Tequila / Jager Shot", LineItemTag.ALCOHOL, 35_000L, Icons.Rounded.LocalBar),
    MenuPreset("mocktail", "Virgin Mojito", LineItemTag.MOCKTAIL, 22_000L, Icons.Rounded.LocalCafe),
    MenuPreset("soft_drink", "Fresh Lime / Soda", LineItemTag.MOCKTAIL, 14_000L, Icons.Rounded.LocalCafe)
)

private val FOOD_SHELF_PRESETS = listOf(
    MenuPreset("veg_starter", "Veg Starter", LineItemTag.VEG, 38_000L, Icons.Rounded.Restaurant),
    MenuPreset("veg_main", "Veg Main & Breads", LineItemTag.VEG, 62_000L, Icons.Rounded.Restaurant),
    MenuPreset("nonveg_starter", "Non-Veg Starter", LineItemTag.NON_VEG, 48_000L, Icons.Rounded.Restaurant),
    MenuPreset("nonveg_main", "Non-Veg Curry / Grill", LineItemTag.NON_VEG, 72_000L, Icons.Rounded.Restaurant),
    MenuPreset("seafood", "Seafood Platter", LineItemTag.NON_VEG, 95_000L, Icons.Rounded.Restaurant),
    MenuPreset("dessert", "Dessert / Tiramisu", LineItemTag.DESSERT, 34_000L, Icons.Rounded.Cake),
    MenuPreset("hookah", "Hookah / Extras", LineItemTag.HOOKAH_EXTRA, 90_000L, Icons.Rounded.LocalBar)
)

private data class TrayItem(
    val id: String,
    val name: String,
    val tag: LineItemTag,
    val quantity: Int,
    val unitPriceText: String,
    val assignedIds: List<String>
)

private data class DinerSummaryRow(
    val member: LedgerMember,
    val foodMinor: Long,
    val drinksMinor: Long,
    val taxAndTipMinor: Long,
    val totalMinor: Long
)

/**
 * Crisp Indian FSSAI-style square-dot dietary indicator (Green = Veg/Vegan, Red = Non-Veg, Amber = Egg).
 */
@Composable
fun DietMarkIcon(
    diet: DietPreference,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    val tint = parseHexColor(diet.colorHex)
    Box(
        modifier = modifier
            .size(size)
            .border(1.4.dp, tint, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.46f)
                .clip(CircleShape)
                .background(tint)
        )
    }
}

/**
 * Compact color-coded badge for LineItemTag (Veg, Non-Veg, Bar, Beer, Soft, Dessert, Extra).
 */
@Composable
fun LineItemTagPill(
    tag: LineItemTag,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tint = parseHexColor(tag.colorHex)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (tag) {
                LineItemTag.VEG -> DietMarkIcon(DietPreference.VEG, size = 10.dp)
                LineItemTag.NON_VEG -> DietMarkIcon(DietPreference.NON_VEG, size = 10.dp)
                LineItemTag.ALCOHOL -> Icon(Icons.Rounded.LocalBar, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                LineItemTag.BEER -> Icon(Icons.Rounded.SportsBar, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                LineItemTag.MOCKTAIL -> Icon(Icons.Rounded.LocalCafe, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                LineItemTag.DESSERT -> Icon(Icons.Rounded.Cake, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
                else -> Unit
            }
            Text(
                text = tag.shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

/**
 * Helper to auto-assign a LineItemTag to eligible diners based on Veg/Non-Veg and Alcohol flags.
 */
fun autoAssignMembersForTag(
    tag: LineItemTag,
    activeMembers: List<LedgerMember>,
    dietOverrides: Map<String, DietPreference> = emptyMap(),
    drinkOverrides: Map<String, Boolean> = emptyMap(),
    vegFoodSharedByAll: Boolean = true
): List<String> {
    if (activeMembers.isEmpty()) return emptyList()
    val allIds = activeMembers.map { it.id }
    val nonVegIds = activeMembers.filter { (dietOverrides[it.id] ?: it.dietEnum).eatsNonVeg }.map { it.id }
    val vegOnlyIds = activeMembers.filter { !(dietOverrides[it.id] ?: it.dietEnum).eatsNonVeg }.map { it.id }
    val drinkerIds = activeMembers.filter { drinkOverrides[it.id] ?: it.isAlcoholDrinker }.map { it.id }
    val nonDrinkerIds = activeMembers.filter { !(drinkOverrides[it.id] ?: it.isAlcoholDrinker) }.map { it.id }

    return when (tag) {
        LineItemTag.VEG -> if (vegFoodSharedByAll) allIds else vegOnlyIds.ifEmpty { allIds }
        LineItemTag.NON_VEG -> nonVegIds.ifEmpty { allIds }
        LineItemTag.ALCOHOL, LineItemTag.BEER -> drinkerIds.ifEmpty { allIds }
        LineItemTag.MOCKTAIL -> nonDrinkerIds.ifEmpty { allIds }
        LineItemTag.DESSERT, LineItemTag.HOOKAH_EXTRA, LineItemTag.SHARED -> allIds
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiningDrinksSplitSheet(
    snapshot: LedgerSnapshot,
    initialGroupId: String?,
    todayIso: String,
    onSaveExpense: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selfId = snapshot.selfMember?.id ?: snapshot.members.firstOrNull()?.id.orEmpty()

    var title by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf(initialGroupId ?: snapshot.groups.firstOrNull()?.id) }
    val selectedGroup = remember(selectedGroupId, snapshot.groups) {
        snapshot.groups.firstOrNull { it.id == selectedGroupId }
    }
    var currency by remember(selectedGroupId) {
        mutableStateOf(selectedGroup?.currencyEnum ?: snapshot.defaultCurrency)
    }
    var paidByMemberId by remember { mutableStateOf(selfId) }
    var studioMode by remember { mutableStateOf(DiningStudioMode.QUICK_BUCKETS) }

    val scopeMembers = remember(selectedGroupId, snapshot.groups, snapshot.members) {
        val grp = snapshot.groups.firstOrNull { it.id == selectedGroupId }
        if (grp != null) {
            val ids = grp.memberIds.toSet()
            snapshot.members.filter { it.id in ids }.ifEmpty { snapshot.members }
        } else {
            snapshot.members
        }
    }

    // Per-meal overrides (seeded from saved member preferences)
    val excludedIds = remember(selectedGroupId) { mutableStateListOf<String>() }
    val dietOverrides = remember(selectedGroupId, scopeMembers) {
        mutableStateMapOf<String, DietPreference>().apply {
            scopeMembers.forEach { put(it.id, it.dietEnum) }
        }
    }
    val drinkOverrides = remember(selectedGroupId, scopeMembers) {
        mutableStateMapOf<String, Boolean>().apply {
            scopeMembers.forEach { put(it.id, it.isAlcoholDrinker) }
        }
    }

    val activeDiners = scopeMembers.filter { it.id !in excludedIds }
    val nonVegDiners = activeDiners.filter { (dietOverrides[it.id] ?: it.dietEnum).eatsNonVeg }
    val vegOnlyDiners = activeDiners.filter { !(dietOverrides[it.id] ?: it.dietEnum).eatsNonVeg }
    val alcoholDrinkers = activeDiners.filter { drinkOverrides[it.id] ?: it.isAlcoholDrinker }
    val nonDrinkers = activeDiners.filter { !(drinkOverrides[it.id] ?: it.isAlcoholDrinker) }

    // --- MODE A: QUICK BUCKETS STATE ---
    var vegBucketText by remember { mutableStateOf("") }
    var vegSharedByAll by remember { mutableStateOf(true) }
    var nonVegBucketText by remember { mutableStateOf("") }
    var alcoholBucketText by remember { mutableStateOf("") }
    var beerBucketText by remember { mutableStateOf("") }
    var mocktailBucketText by remember { mutableStateOf("") }
    var dessertBucketText by remember { mutableStateOf("") }
    val drinkWeightMap = remember(selectedGroupId, scopeMembers) {
        mutableStateMapOf<String, Int>().apply {
            scopeMembers.forEach { put(it.id, if (it.isAlcoholDrinker) 1 else 0) }
        }
    }
    val mocktailSelectedIds = remember(selectedGroupId, scopeMembers) {
        mutableStateListOf<String>().apply {
            val defaults = scopeMembers.filter { !it.isAlcoholDrinker }.map { it.id }
            addAll(defaults.ifEmpty { scopeMembers.map { it.id } })
        }
    }
    val dessertSelectedIds = remember(selectedGroupId, scopeMembers) {
        mutableStateListOf<String>().apply {
            addAll(scopeMembers.map { it.id })
        }
    }

    // --- MODE B: BAR & MENU TAP PICKER TRAY ---
    val trayItems = remember { mutableStateListOf<TrayItem>() }

    // --- TAX & TIP CONTROLS ---
    var foodGstText by remember { mutableStateOf("") }
    var barVatText by remember { mutableStateOf("") }
    var tipText by remember { mutableStateOf("") }

    // Build deterministic per-person breakdown
    val vegMinor = FinanceEngine.parseMajorToMinor(vegBucketText)
    val nonVegMinor = FinanceEngine.parseMajorToMinor(nonVegBucketText)
    val alcoholMinor = FinanceEngine.parseMajorToMinor(alcoholBucketText)
    val beerMinor = FinanceEngine.parseMajorToMinor(beerBucketText)
    val mocktailMinor = FinanceEngine.parseMajorToMinor(mocktailBucketText)
    val dessertMinor = FinanceEngine.parseMajorToMinor(dessertBucketText)

    val foodSubtotalByMember = mutableMapOf<String, Long>()
    val drinkSubtotalByMember = mutableMapOf<String, Long>()
    val alcoholOnlyByMember = mutableMapOf<String, Long>()
    val generatedLineItems = mutableListOf<ReceiptLineItem>()

    if (studioMode == DiningStudioMode.QUICK_BUCKETS) {
        if (vegMinor > 0L) {
            val targets = (if (vegSharedByAll) activeDiners else vegOnlyDiners.ifEmpty { activeDiners }).map { it.id }
            FinanceEngine.distributeEqual(vegMinor, targets).forEach { a ->
                foodSubtotalByMember[a.memberId] = (foodSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
            }
            generatedLineItems.add(
                ReceiptLineItem(
                    itemId = "dine_veg",
                    name = if (vegSharedByAll) "Veg Food (Shared)" else "Veg Food (Veg Diners)",
                    quantity = 1L,
                    unitPriceMinor = vegMinor,
                    totalPriceMinor = vegMinor,
                    assignedMemberIds = targets,
                    itemTag = LineItemTag.VEG.name
                )
            )
        }
        if (nonVegMinor > 0L) {
            val targets = nonVegDiners.ifEmpty { activeDiners }.map { it.id }
            FinanceEngine.distributeEqual(nonVegMinor, targets).forEach { a ->
                foodSubtotalByMember[a.memberId] = (foodSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
            }
            generatedLineItems.add(
                ReceiptLineItem(
                    itemId = "dine_nonveg",
                    name = "Non-Veg Food",
                    quantity = 1L,
                    unitPriceMinor = nonVegMinor,
                    totalPriceMinor = nonVegMinor,
                    assignedMemberIds = targets,
                    itemTag = LineItemTag.NON_VEG.name
                )
            )
        }
        val combinedBarMinor = alcoholMinor + beerMinor
        if (combinedBarMinor > 0L) {
            val eligibleDrinkers = alcoholDrinkers.ifEmpty { activeDiners }
            val weights = eligibleDrinkers.associate { d ->
                val w = (drinkWeightMap[d.id] ?: 1).coerceAtLeast(0).toLong()
                d.id to w
            }.filterValues { it > 0L }.ifEmpty { eligibleDrinkers.associate { it.id to 1L } }

            if (alcoholMinor > 0L) {
                FinanceEngine.distributeByShares(alcoholMinor, weights).forEach { a ->
                    drinkSubtotalByMember[a.memberId] = (drinkSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                    alcoholOnlyByMember[a.memberId] = (alcoholOnlyByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                }
                generatedLineItems.add(
                    ReceiptLineItem(
                        itemId = "dine_alcohol",
                        name = "Cocktails & Spirits",
                        quantity = 1L,
                        unitPriceMinor = alcoholMinor,
                        totalPriceMinor = alcoholMinor,
                        assignedMemberIds = weights.keys.toList(),
                        itemTag = LineItemTag.ALCOHOL.name
                    )
                )
            }
            if (beerMinor > 0L) {
                FinanceEngine.distributeByShares(beerMinor, weights).forEach { a ->
                    drinkSubtotalByMember[a.memberId] = (drinkSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                    alcoholOnlyByMember[a.memberId] = (alcoholOnlyByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                }
                generatedLineItems.add(
                    ReceiptLineItem(
                        itemId = "dine_beer",
                        name = "Beer & Pitchers",
                        quantity = 1L,
                        unitPriceMinor = beerMinor,
                        totalPriceMinor = beerMinor,
                        assignedMemberIds = weights.keys.toList(),
                        itemTag = LineItemTag.BEER.name
                    )
                )
            }
        }
        if (mocktailMinor > 0L) {
            val targets = mocktailSelectedIds.filter { id -> activeDiners.any { it.id == id } }
                .ifEmpty { nonDrinkers.ifEmpty { activeDiners }.map { it.id } }
            FinanceEngine.distributeEqual(mocktailMinor, targets).forEach { a ->
                drinkSubtotalByMember[a.memberId] = (drinkSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
            }
            generatedLineItems.add(
                ReceiptLineItem(
                    itemId = "dine_mocktail",
                    name = "Mocktails & Soft Drinks",
                    quantity = 1L,
                    unitPriceMinor = mocktailMinor,
                    totalPriceMinor = mocktailMinor,
                    assignedMemberIds = targets,
                    itemTag = LineItemTag.MOCKTAIL.name
                )
            )
        }
        if (dessertMinor > 0L) {
            val targets = dessertSelectedIds.filter { id -> activeDiners.any { it.id == id } }
                .ifEmpty { activeDiners.map { it.id } }
            FinanceEngine.distributeEqual(dessertMinor, targets).forEach { a ->
                foodSubtotalByMember[a.memberId] = (foodSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
            }
            generatedLineItems.add(
                ReceiptLineItem(
                    itemId = "dine_dessert",
                    name = "Desserts / Hookah / Extras",
                    quantity = 1L,
                    unitPriceMinor = dessertMinor,
                    totalPriceMinor = dessertMinor,
                    assignedMemberIds = targets,
                    itemTag = LineItemTag.DESSERT.name
                )
            )
        }
    } else {
        trayItems.forEach { item ->
            val unitMinor = FinanceEngine.parseMajorToMinor(item.unitPriceText)
            val lineTotal = unitMinor * item.quantity.coerceAtLeast(1)
            val validAssigned = item.assignedIds.filter { id -> activeDiners.any { it.id == id } }
            if (lineTotal > 0L && validAssigned.isNotEmpty()) {
                val shares = FinanceEngine.distributeEqual(lineTotal, validAssigned)
                shares.forEach { a ->
                    if (item.tag.isDrink) {
                        drinkSubtotalByMember[a.memberId] = (drinkSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                        if (item.tag.isAlcoholic) {
                            alcoholOnlyByMember[a.memberId] = (alcoholOnlyByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                        }
                    } else {
                        foodSubtotalByMember[a.memberId] = (foodSubtotalByMember[a.memberId] ?: 0L) + a.amountMinorUnits
                    }
                }
                generatedLineItems.add(
                    ReceiptLineItem(
                        itemId = item.id,
                        name = item.name,
                        quantity = item.quantity.toLong().coerceAtLeast(1L),
                        unitPriceMinor = unitMinor,
                        totalPriceMinor = lineTotal,
                        assignedMemberIds = validAssigned,
                        itemTag = item.tag.name
                    )
                )
            }
        }
    }

    val totalFoodAndSoftMinor = foodSubtotalByMember.values.sum() +
        (drinkSubtotalByMember.values.sum() - alcoholOnlyByMember.values.sum()).coerceAtLeast(0L)
    val totalAlcoholMinor = alcoholOnlyByMember.values.sum()
    val preTaxSubtotalMinor = foodSubtotalByMember.values.sum() + drinkSubtotalByMember.values.sum()

    val foodGstMinor = FinanceEngine.parseMajorToMinor(foodGstText)
    val barVatMinor = FinanceEngine.parseMajorToMinor(barVatText)
    val tipMinor = FinanceEngine.parseMajorToMinor(tipText)
    val grandTotalMinor = preTaxSubtotalMinor + foodGstMinor + barVatMinor + tipMinor

    // Prorate Food GST strictly on non-alcohol subtotal, Bar VAT strictly on alcohol subtotal, Tip across full subtotal!
    val nonAlcoholWeightByMember = activeDiners.associate { d ->
        val f = foodSubtotalByMember[d.id] ?: 0L
        val soft = ((drinkSubtotalByMember[d.id] ?: 0L) - (alcoholOnlyByMember[d.id] ?: 0L)).coerceAtLeast(0L)
        d.id to (f + soft)
    }.filterValues { it > 0L }

    val alcoholWeightByMember = alcoholOnlyByMember.filterValues { it > 0L }
    val allSubtotalByMember = activeDiners.associate { d ->
        d.id to ((foodSubtotalByMember[d.id] ?: 0L) + (drinkSubtotalByMember[d.id] ?: 0L))
    }.filterValues { it > 0L }

    val foodGstProrated = if (foodGstMinor > 0L && nonAlcoholWeightByMember.isNotEmpty()) {
        FinanceEngine.distributeByShares(foodGstMinor, nonAlcoholWeightByMember).associate { it.memberId to it.amountMinorUnits }
    } else if (foodGstMinor > 0L && allSubtotalByMember.isNotEmpty()) {
        FinanceEngine.distributeByShares(foodGstMinor, allSubtotalByMember).associate { it.memberId to it.amountMinorUnits }
    } else emptyMap()

    val barVatProrated = if (barVatMinor > 0L && alcoholWeightByMember.isNotEmpty()) {
        FinanceEngine.distributeByShares(barVatMinor, alcoholWeightByMember).associate { it.memberId to it.amountMinorUnits }
    } else if (barVatMinor > 0L && allSubtotalByMember.isNotEmpty()) {
        FinanceEngine.distributeByShares(barVatMinor, allSubtotalByMember).associate { it.memberId to it.amountMinorUnits }
    } else emptyMap()

    val tipProrated = if (tipMinor > 0L && allSubtotalByMember.isNotEmpty()) {
        FinanceEngine.distributeByShares(tipMinor, allSubtotalByMember).associate { it.memberId to it.amountMinorUnits }
    } else emptyMap()

    val dinerSummaries = activeDiners.mapNotNull { m ->
        val f = foodSubtotalByMember[m.id] ?: 0L
        val dr = drinkSubtotalByMember[m.id] ?: 0L
        val tx = (foodGstProrated[m.id] ?: 0L) + (barVatProrated[m.id] ?: 0L) + (tipProrated[m.id] ?: 0L)
        val tot = f + dr + tx
        if (tot > 0L) DinerSummaryRow(m, f, dr, tx, tot) else null
    }

    val finalAllocations = dinerSummaries.map { row ->
        SplitAllocation(
            memberId = row.member.id,
            amountMinorUnits = row.totalMinor,
            percentageBps = if (grandTotalMinor > 0L) (row.totalMinor * 10_000L) / grandTotalMinor else 0L,
            shares = 1L
        )
    }

    val canSave = grandTotalMinor > 0L && finalAllocations.isNotEmpty() && paidByMemberId.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .testTag("dining_drinks_split_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        DietMarkIcon(DietPreference.VEG, size = 14.dp)
                        DietMarkIcon(DietPreference.NON_VEG, size = 14.dp)
                        Icon(
                            imageVector = Icons.Rounded.LocalBar,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Table & Bar Splitter", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Fair Veg / Non-Veg + Drinks split with separate Food GST & Bar VAT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close dining splitter")
                }
            }

            // Venue / Title + Group + Paid By
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Restaurant, Bar or Party Name") },
                placeholder = { Text("e.g., Toit Brewpub Saturday Night") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dining_title_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Group / Scope", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedGroupId == null,
                                onClick = { selectedGroupId = null },
                                label = { Text("Personal") }
                            )
                        }
                        items(snapshot.groups, key = { it.id }) { grp ->
                            FilterChip(
                                selected = selectedGroupId == grp.id,
                                onClick = { selectedGroupId = grp.id },
                                label = { Text(grp.name) }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paid By", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(scopeMembers, key = { it.id }) { m ->
                        FilterChip(
                            selected = paidByMemberId == m.id,
                            onClick = { paidByMemberId = m.id },
                            label = { Text(m.name.split(" ").first()) }
                        )
                    }
                }
            }

            // STEP 1: WHO'S AT THE TABLE (Veg / Non-Veg + Drinks Matrix)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Table Crew (${activeDiners.size} diners)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${vegOnlyDiners.size} Veg · ${nonVegDiners.size} Non-Veg · ${alcoholDrinkers.size} Drink",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.positive
                    )
                }

                HairlineSection {
                    scopeMembers.forEachIndexed { idx, member ->
                        val isAtTable = member.id !in excludedIds
                        val diet = dietOverrides[member.id] ?: member.dietEnum
                        val drinks = drinkOverrides[member.id] ?: member.isAlcoholDrinker

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("dining_member_row_${member.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isAtTable) excludedIds.add(member.id) else excludedIds.remove(member.id)
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isAtTable) tokens.positive else tokens.elevatedSurface)
                                        .border(1.dp, if (isAtTable) tokens.positive else tokens.hairline, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isAtTable) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                                MemberAvatar(member = member, size = 28.dp, selected = isAtTable, showGuestDot = false)
                                Text(
                                    text = member.name.split(" ").first(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isAtTable) MaterialTheme.colorScheme.onSurface else tokens.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isAtTable) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Veg / Non-Veg / Egg quick cycle pill
                                    val dietColor = parseHexColor(diet.colorHex)
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = dietColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, dietColor.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val next = when (diet) {
                                                    DietPreference.VEG -> DietPreference.NON_VEG
                                                    DietPreference.NON_VEG -> DietPreference.EGGETARIAN
                                                    DietPreference.EGGETARIAN -> DietPreference.VEG
                                                    DietPreference.VEGAN -> DietPreference.NON_VEG
                                                }
                                                dietOverrides[member.id] = next
                                            }
                                            .testTag("dining_diet_toggle_${member.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            DietMarkIcon(diet = diet, size = 11.dp)
                                            Text(
                                                text = diet.shortLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = dietColor
                                            )
                                        }
                                    }

                                    // Drinks / Zero-Proof toggle pill
                                    val drinkTint = if (drinks) Color(0xFFA855F7) else Color(0xFF06B6D4)
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = drinkTint.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, drinkTint.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val nextDrinks = !drinks
                                                drinkOverrides[member.id] = nextDrinks
                                                drinkWeightMap[member.id] = if (nextDrinks) (drinkWeightMap[member.id]?.coerceAtLeast(1) ?: 1) else 0
                                            }
                                            .testTag("dining_drink_toggle_${member.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (drinks) Icons.Rounded.LocalBar else Icons.Rounded.NoDrinks,
                                                contentDescription = if (drinks) "Drinks alcohol" else "No alcohol",
                                                tint = drinkTint,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (drinks) "BAR" else "0%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = drinkTint
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text("Skipped", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                            }
                        }
                        if (idx < scopeMembers.lastIndex) {
                            HorizontalDivider(color = tokens.hairline)
                        }
                    }
                }
            }

            // STEP 2: MODE SWITCHER (Quick Buckets vs Drinks & Dish Tap Picker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.cardSurface)
                    .border(1.dp, tokens.hairline, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DiningStudioMode.entries.forEach { mode ->
                    val selected = studioMode == mode
                    Surface(
                        onClick = { studioMode = mode },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) tokens.elevatedSurface else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dining_mode_${mode.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MaterialTheme.colorScheme.onSurface else tokens.inkMuted
                            )
                        }
                    }
                }
            }

            if (studioMode == DiningStudioMode.QUICK_BUCKETS) {
                // QUICK BUCKETS WORKBENCH
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. Veg Food Bucket
                    BucketInputCard(
                        title = "Veg Food",
                        subtitle = if (vegSharedByAll) "Split among all ${activeDiners.size} diners" else "Split among ${vegOnlyDiners.size} veg-only diners",
                        accentColor = Color(0xFF10B981),
                        leadingBadge = { DietMarkIcon(DietPreference.VEG, size = 16.dp) },
                        amountText = vegBucketText,
                        onAmountChange = { vegBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_veg_input",
                        extraContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = vegSharedByAll,
                                    onClick = { vegSharedByAll = true },
                                    label = { Text("Everyone ate Veg") }
                                )
                                FilterChip(
                                    selected = !vegSharedByAll,
                                    onClick = { vegSharedByAll = false },
                                    label = { Text("Veg diners only") }
                                )
                            }
                        }
                    )

                    // 2. Non-Veg Food Bucket
                    BucketInputCard(
                        title = "Non-Veg Food",
                        subtitle = if (nonVegDiners.isNotEmpty()) {
                            "Strictly among ${nonVegDiners.joinToString(", ") { it.name.split(" ").first() }}"
                        } else "No Non-Veg diners toggled above",
                        accentColor = Color(0xFFE11D48),
                        leadingBadge = { DietMarkIcon(DietPreference.NON_VEG, size = 16.dp) },
                        amountText = nonVegBucketText,
                        onAmountChange = { nonVegBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_nonveg_input"
                    )

                    // 3. Cocktails & Spirits + Per-Drinker Glass/Peg Counter
                    BucketInputCard(
                        title = "Cocktails & Spirits",
                        subtitle = if (alcoholDrinkers.isNotEmpty()) {
                            "Weighted by drinks among ${alcoholDrinkers.size} drinker(s)"
                        } else "No drinkers toggled above",
                        accentColor = Color(0xFFA855F7),
                        leadingBadge = {
                            Icon(Icons.Rounded.LocalBar, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                        },
                        amountText = alcoholBucketText,
                        onAmountChange = { alcoholBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_alcohol_input",
                        extraContent = if (alcoholDrinkers.isNotEmpty()) {
                            {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Drinks / Pegs Count (for fair bar weighting)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkMuted
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        alcoholDrinkers.forEach { d ->
                                            val count = (drinkWeightMap[d.id] ?: 1).coerceAtLeast(0)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = tokens.elevatedSurface,
                                                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.35f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(d.name.split(" ").first(), style = MaterialTheme.typography.labelMedium)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(tokens.cardSurface)
                                                            .clickable {
                                                                if (count > 0) drinkWeightMap[d.id] = count - 1
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.Remove, contentDescription = "Less drinks", modifier = Modifier.size(14.dp))
                                                    }
                                                    Text(
                                                        text = "${count}x",
                                                        style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                                                        color = Color(0xFFA855F7)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(tokens.cardSurface)
                                                            .clickable { drinkWeightMap[d.id] = (count + 1).coerceAtMost(20) },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Rounded.Add, contentDescription = "More drinks", modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else null
                    )

                    // 4. Beer & Pitchers
                    BucketInputCard(
                        title = "Beer & Pitchers",
                        subtitle = "Uses same drinker weights (${alcoholDrinkers.size} drinker(s))",
                        accentColor = Color(0xFFF59E0B),
                        leadingBadge = {
                            Icon(Icons.Rounded.SportsBar, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        },
                        amountText = beerBucketText,
                        onAmountChange = { beerBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_beer_input"
                    )

                    // 5. Mocktails & Soft Drinks
                    BucketInputCard(
                        title = "Mocktails & Soft Drinks",
                        subtitle = "Tap who ordered zero-proof drinks or sodas",
                        accentColor = Color(0xFF06B6D4),
                        leadingBadge = {
                            Icon(Icons.Rounded.LocalCafe, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(18.dp))
                        },
                        amountText = mocktailBucketText,
                        onAmountChange = { mocktailBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_mocktail_input",
                        extraContent = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeDiners.forEach { m ->
                                    val sel = m.id in mocktailSelectedIds
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            if (sel) mocktailSelectedIds.remove(m.id) else mocktailSelectedIds.add(m.id)
                                        },
                                        label = { Text(m.name.split(" ").first()) }
                                    )
                                }
                            }
                        }
                    )

                    // 6. Desserts / Hookah / Extras
                    BucketInputCard(
                        title = "Desserts / Hookah / Extras",
                        subtitle = "Tap everyone who shared desserts or hookah",
                        accentColor = Color(0xFFFB923C),
                        leadingBadge = {
                            Icon(Icons.Rounded.Cake, contentDescription = null, tint = Color(0xFFFB923C), modifier = Modifier.size(18.dp))
                        },
                        amountText = dessertBucketText,
                        onAmountChange = { dessertBucketText = it },
                        currency = currency,
                        testTagKey = "bucket_dessert_input",
                        extraContent = {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeDiners.forEach { m ->
                                    val sel = m.id in dessertSelectedIds
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            if (sel) dessertSelectedIds.remove(m.id) else dessertSelectedIds.add(m.id)
                                        },
                                        label = { Text(m.name.split(" ").first()) }
                                    )
                                }
                            }
                        }
                    )
                }
            } else {
                // MODE B: COOL BAR & MENU TAP PICKER
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tap to Add Drinks & Bar Items", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DRINKS_SHELF_PRESETS, key = { it.id }) { preset ->
                                MenuPresetTile(
                                    preset = preset,
                                    currency = currency,
                                    onTap = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val autoIds = autoAssignMembersForTag(
                                            tag = preset.tag,
                                            activeMembers = activeDiners,
                                            dietOverrides = dietOverrides,
                                            drinkOverrides = drinkOverrides,
                                            vegFoodSharedByAll = vegSharedByAll
                                        )
                                        trayItems.add(
                                            TrayItem(
                                                id = "tray_${System.currentTimeMillis()}_${trayItems.size}",
                                                name = preset.title,
                                                tag = preset.tag,
                                                quantity = 1,
                                                unitPriceText = FinanceEngine.minorToMajorInput(preset.defaultPriceMinor),
                                                assignedIds = autoIds
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tap to Add Veg / Non-Veg Dishes & Extras", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FOOD_SHELF_PRESETS, key = { it.id }) { preset ->
                                MenuPresetTile(
                                    preset = preset,
                                    currency = currency,
                                    onTap = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val autoIds = autoAssignMembersForTag(
                                            tag = preset.tag,
                                            activeMembers = activeDiners,
                                            dietOverrides = dietOverrides,
                                            drinkOverrides = drinkOverrides,
                                            vegFoodSharedByAll = vegSharedByAll
                                        )
                                        trayItems.add(
                                            TrayItem(
                                                id = "tray_${System.currentTimeMillis()}_${trayItems.size}",
                                                name = preset.title,
                                                tag = preset.tag,
                                                quantity = 1,
                                                unitPriceText = FinanceEngine.minorToMajorInput(preset.defaultPriceMinor),
                                                assignedIds = autoIds
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (trayItems.isEmpty()) {
                        EditorialCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Your table tray is empty", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Tap any drink, veg dish, or non-veg platter above — SplitFlow automatically assigns it to matching diners!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }
                    } else {
                        trayItems.forEachIndexed { idx, item ->
                            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            LineItemTagPill(tag = item.tag)
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { trayItems.removeAt(idx) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove item", tint = tokens.inkMuted, modifier = Modifier.size(17.dp))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Quantity stepper
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = tokens.elevatedSurface,
                                            border = BorderStroke(1.dp, tokens.hairline)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (item.quantity > 1) trayItems[idx] = item.copy(quantity = item.quantity - 1)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease qty", modifier = Modifier.size(15.dp))
                                                }
                                                Text("${item.quantity}", style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold))
                                                IconButton(
                                                    onClick = { trayItems[idx] = item.copy(quantity = (item.quantity + 1).coerceAtMost(30)) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Add, contentDescription = "Increase qty", modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = item.unitPriceText,
                                            onValueChange = { trayItems[idx] = item.copy(unitPriceText = it) },
                                            label = { Text("Unit (${currency.symbol})") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val lineTotal = FinanceEngine.parseMajorToMinor(item.unitPriceText) * item.quantity
                                        TabularCurrencyText(
                                            minorUnits = lineTotal,
                                            currency = currency,
                                            style = TabularSmallStyle,
                                            color = tokens.positive
                                        )
                                    }

                                    // Who had this item?
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        activeDiners.forEach { m ->
                                            val claimed = m.id in item.assignedIds
                                            FilterChip(
                                                selected = claimed,
                                                onClick = {
                                                    val next = item.assignedIds.toMutableList()
                                                    if (claimed) next.remove(m.id) else next.add(m.id)
                                                    trayItems[idx] = item.copy(assignedIds = next)
                                                },
                                                label = { Text(m.name.split(" ").first()) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: SEPARATE FOOD GST, BAR VAT & TIP
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("3. Smart Tax & Tip (Fair Alcohol VAT)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Food GST applies only to food & soft drinks; Bar VAT applies strictly to alcohol drinkers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                    // Quick one-tap tax presets
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (totalFoodAndSoftMinor > 0L) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val gst5 = (totalFoodAndSoftMinor * 0.05).roundToLong()
                                    foodGstText = FinanceEngine.minorToMajorInput(gst5)
                                },
                                label = { Text("+5% Food GST") }
                            )
                        }
                        if (totalAlcoholMinor > 0L) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val vat18 = (totalAlcoholMinor * 0.18).roundToLong()
                                    barVatText = FinanceEngine.minorToMajorInput(vat18)
                                },
                                label = { Text("+18% Bar VAT") }
                            )
                        }
                        if (preTaxSubtotalMinor > 0L) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val tip10 = (preTaxSubtotalMinor * 0.10).roundToLong()
                                    tipText = FinanceEngine.minorToMajorInput(tip10)
                                },
                                label = { Text("+10% Service / Tip") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = foodGstText,
                            onValueChange = { foodGstText = it },
                            label = { Text("Food GST") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = barVatText,
                            onValueChange = { barVatText = it },
                            label = { Text("Bar VAT") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tipText,
                            onValueChange = { tipText = it },
                            label = { Text("Tip/Svc") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // STEP 4: LIVE PER-PERSON FAIR BREAKDOWN
            if (dinerSummaries.isNotEmpty()) {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Fair Per-Person Breakdown", style = MaterialTheme.typography.titleMedium)
                            TabularCurrencyText(
                                minorUnits = grandTotalMinor,
                                currency = currency,
                                style = TabularAmountStyle,
                                color = tokens.positive
                            )
                        }
                        HorizontalDivider(color = tokens.hairline)
                        dinerSummaries.forEach { row ->
                            val diet = dietOverrides[row.member.id] ?: row.member.dietEnum
                            val drinks = drinkOverrides[row.member.id] ?: row.member.isAlcoholDrinker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DietMarkIcon(diet = diet, size = 11.dp)
                                        Text(row.member.name.split(" ").first(), style = MaterialTheme.typography.titleSmall)
                                        if (!drinks) {
                                            Text(
                                                text = "0% Bar",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF06B6D4)
                                            )
                                        }
                                    }
                                    Text(
                                        text = buildString {
                                            append("Food ${FinanceEngine.formatMinorUnits(row.foodMinor, currency)}")
                                            if (row.drinksMinor > 0L) append(" · Drinks ${FinanceEngine.formatMinorUnits(row.drinksMinor, currency)}")
                                            if (row.taxAndTipMinor > 0L) append(" · Tax/Tip ${FinanceEngine.formatMinorUnits(row.taxAndTipMinor, currency)}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkMuted
                                    )
                                }
                                TabularCurrencyText(
                                    minorUnits = row.totalMinor,
                                    currency = currency,
                                    style = TabularAmountStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    val cleanTitle = title.trim().ifBlank { "Dining & Drinks Split" }
                    val targetBase = selectedGroup?.currencyEnum ?: snapshot.defaultCurrency
                    val fxMicros = if (currency != targetBase) {
                        FinanceEngine.getFxRateMicros(currency, targetBase)
                    } else null
                    val record = ExpenseRecord(
                        groupId = selectedGroupId,
                        title = cleanTitle,
                        notes = "Veg/Non-Veg & Drinks Studio (${vegOnlyDiners.size} Veg, ${nonVegDiners.size} Non-Veg, ${alcoholDrinkers.size} Drinkers)",
                        category = ExpenseCategory.FOOD.name,
                        currency = currency.name,
                        totalMinorUnits = grandTotalMinor,
                        splitType = SplitType.ITEMIZED.name,
                        dateLabel = todayIso,
                        shareCode = "dine-${UUID.randomUUID().toString().take(8)}",
                        aiExtracted = false,
                        settled = false,
                        payers = listOf(PayerEntry(paidByMemberId, grandTotalMinor)),
                        allocations = finalAllocations,
                        lineItems = generatedLineItems,
                        taxMinorUnits = foodGstMinor + barVatMinor,
                        serviceChargeMinorUnits = tipMinor,
                        discountMinorUnits = 0L,
                        fxRateToBaseMicros = fxMicros
                    )
                    onSaveExpense(record)
                },
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.positive,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("dining_save_split_button")
            ) {
                Icon(Icons.Rounded.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (grandTotalMinor > 0L) {
                        "Save Dining & Drinks Split (${FinanceEngine.formatMinorUnits(grandTotalMinor, currency)})"
                    } else {
                        "Enter Food or Drinks Amounts"
                    }
                )
            }
        }
    }
}

@Composable
private fun BucketInputCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    leadingBadge: @Composable () -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    currency: CurrencyCode,
    testTagKey: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, if (amountText.isNotBlank()) accentColor.copy(alpha = 0.5f) else tokens.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    leadingBadge()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    placeholder = { Text("${currency.symbol}0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .width(114.dp)
                        .testTag(testTagKey)
                )
            }
            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
private fun MenuPresetTile(
    preset: MenuPreset,
    currency: CurrencyCode,
    onTap: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val tint = parseHexColor(preset.tag.colorHex)
    Surface(
        onClick = onTap,
        shape = RoundedCornerShape(14.dp),
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(136.dp)
            .testTag("menu_preset_${preset.id}")
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                LineItemTagPill(tag = preset.tag)
            }
            Text(
                text = preset.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "+ ${FinanceEngine.formatMinorUnits(preset.defaultPriceMinor, currency)}",
                style = TabularSmallStyle,
                color = tint
            )
        }
    }
}
