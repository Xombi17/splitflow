package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalPlay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReceiptPhotoStore
import com.example.engine.FinanceEngine
import com.example.model.ExpenseCategory
import com.example.model.ExpenseRecord
import com.example.model.PaymentMethod
import com.example.model.RecurringFrequency
import com.example.model.SettlementRecord
import com.example.ui.components.AiBadge
import com.example.ui.components.EditorialCard
import com.example.ui.components.FluidSegmentedPillBar
import com.example.ui.components.HairlineSection
import com.example.ui.components.MemberAvatar
import com.example.ui.components.MonthlyCashFlowChart
import com.example.ui.components.TabularCurrencyText
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class ActivityMode(val label: String) {
    ALL("All"),
    RECEIPTS("Receipts"),
    RECURRING("Recurring")
}

private enum class StatusFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    SETTLED("Settled"),
    AI_ONLY("AI Parsed")
}

private data class ExpenseDateSection(
    val header: String,
    val items: List<ExpenseRecord>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LedgerScreen(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSharedSplit: (ExpenseRecord) -> Unit,
    onViewReceipt: (ExpenseRecord) -> Unit,
    onViewSettlementProof: (SettlementRecord) -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onDeleteExpense: (ExpenseRecord) -> Unit,
    onUpdateSettlement: (SettlementRecord) -> Unit,
    onDeleteSettlement: (SettlementRecord) -> Unit,
    onToggleSettled: (ExpenseRecord) -> Unit,
    onLogRecurringCycle: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }
    val groupMap = remember(snapshot.groups) { snapshot.groups.associateBy { it.id } }

    var activityMode by remember { mutableStateOf(ActivityMode.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(StatusFilter.ALL) }
    var selectedGroupFilterId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var editingSettlement by remember { mutableStateOf<SettlementRecord?>(null) }

    val activeFilterCount = (if (selectedStatus != StatusFilter.ALL) 1 else 0) +
        (if (selectedGroupFilterId != null) 1 else 0) +
        (if (selectedCategoryFilter != null) 1 else 0)

    val filteredExpenses = remember(
        snapshot.expenses,
        activityMode,
        searchQuery,
        selectedStatus,
        selectedGroupFilterId,
        selectedCategoryFilter
    ) {
        snapshot.expenses.filter { exp ->
            val matchesMode = when (activityMode) {
                ActivityMode.ALL -> true
                ActivityMode.RECEIPTS -> !exp.receiptPhotoPath.isNullOrBlank()
                ActivityMode.RECURRING -> exp.recurringEnum != RecurringFrequency.NONE
            }
            val matchesQuery = searchQuery.isBlank() ||
                exp.title.contains(searchQuery, ignoreCase = true) ||
                (exp.notes?.contains(searchQuery, ignoreCase = true) == true) ||
                exp.payers.any { memberMap[it.memberId]?.name?.contains(searchQuery, ignoreCase = true) == true } ||
                exp.allocations.any { memberMap[it.memberId]?.name?.contains(searchQuery, ignoreCase = true) == true }
            val matchesStatus = when (selectedStatus) {
                StatusFilter.ALL -> true
                StatusFilter.OPEN -> !exp.settled
                StatusFilter.SETTLED -> exp.settled
                StatusFilter.AI_ONLY -> exp.aiExtracted
            }
            val matchesGroup = when (selectedGroupFilterId) {
                null -> true
                "__personal__" -> exp.groupId.isNullOrBlank()
                else -> exp.groupId == selectedGroupFilterId
            }
            val matchesCategory = selectedCategoryFilter == null || exp.categoryEnum == selectedCategoryFilter
            matchesMode && matchesQuery && matchesStatus && matchesGroup && matchesCategory
        }
    }

    val groupedSections = remember(filteredExpenses) {
        bucketExpensesByRecency(filteredExpenses)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ledger_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Editorial Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .springClickable(pressedScale = 0.90f, onClick = onOpenProfile),
                    contentAlignment = Alignment.Center
                ) {
                    MemberAvatar(
                        member = snapshot.selfMember,
                        size = 36.dp,
                        selected = true,
                        showGuestDot = false
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isGuestMode) tokens.warning else tokens.positive)
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }
            }
        }

        // Pill Search Field + Minimal Filter Icon Trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.cardSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    shadowElevation = if (tokens.isDark) 0.dp else 0.5.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = tokens.inkMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search splits or payers...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(tokens.obsidian),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ledger_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = tokens.inkMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    SpringSurface(
                        onClick = { showFilterSheet = true },
                        shape = CircleShape,
                        color = if (activeFilterCount > 0) tokens.obsidian else tokens.cardSurface,
                        border = BorderStroke(
                            1.dp,
                            if (activeFilterCount > 0) tokens.obsidian else tokens.hairline
                        ),
                        pressedScale = 0.90f,
                        modifier = Modifier
                            .size(46.dp)
                            .testTag("ledger_filter_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Filters",
                                tint = if (activeFilterCount > 0) tokens.onObsidian else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (activeFilterCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = tokens.acidLime,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$activeFilterCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = tokens.onAcidLime
                                )
                            }
                        }
                    }
                }
            }
        }

        // Apple-Style Fluid Sliding Segmented Filter Bar: All / Receipts / Recurring
        item {
            val labels = ActivityMode.entries.map { mode ->
                when (mode) {
                    ActivityMode.ALL -> mode.label
                    ActivityMode.RECEIPTS -> if (snapshot.receiptExpenses.isNotEmpty()) "${mode.label} (${snapshot.receiptExpenses.size})" else mode.label
                    ActivityMode.RECURRING -> if (snapshot.recurringExpenses.isNotEmpty()) "${mode.label} (${snapshot.recurringExpenses.size})" else mode.label
                }
            }
            FluidSegmentedPillBar(
                labels = labels,
                selectedIndex = activityMode.ordinal,
                onSelect = { idx -> activityMode = ActivityMode.entries[idx] },
                testTags = ActivityMode.entries.map { "activity_mode_${it.name.lowercase()}" }
            )
        }

        // 5-Month Capsule Analytics Card (in All mode, single source of truth — no duplicate total header)
        if (activityMode == ActivityMode.ALL && snapshot.expenses.isNotEmpty()) {
            item {
                val cashFlowPoints = remember(snapshot.expenses, snapshot.selfMember?.id, snapshot.defaultCurrency) {
                    FinanceEngine.computeMonthlyCashFlow(
                        expenses = snapshot.expenses,
                        selfMemberId = snapshot.selfMember?.id,
                        baseCurrency = snapshot.defaultCurrency
                    )
                }
                MonthlyCashFlowChart(
                    points = cashFlowPoints,
                    currency = snapshot.defaultCurrency
                )
            }
        }

        if (filteredExpenses.isEmpty()) {
            item {
                HairlineSection {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (activityMode == ActivityMode.RECEIPTS) Icons.Rounded.PhotoLibrary else Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = tokens.inkMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = when (activityMode) {
                                ActivityMode.RECEIPTS -> "No receipt photos attached"
                                ActivityMode.RECURRING -> "No recurring bills scheduled"
                                ActivityMode.ALL -> "No matching expenses"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (activityMode) {
                                ActivityMode.RECEIPTS -> "Scan a bill with AI Lens or attach a receipt photo in the Split Editor."
                                ActivityMode.RECURRING -> "Mark any rent or utility split as Weekly, Monthly, or Quarterly."
                                ActivityMode.ALL -> "Try clearing filters or search terms."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (activityMode == ActivityMode.RECEIPTS) {
            // 2-Column Visual Receipt Photo Gallery
            val paired = filteredExpenses.chunked(2)
            items(paired) { rowPair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowPair.forEach { exp ->
                        val thumb = remember(exp.receiptPhotoPath) {
                            val f = ReceiptPhotoStore.resolveFile(context, exp.receiptPhotoPath)
                            if (f != null) BitmapFactory.decodeFile(f.absolutePath) else null
                        }
                        EditorialCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_gallery_tile_${exp.id}"),
                            onClick = { onViewReceipt(exp) }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(142.dp)
                                        .background(tokens.elevatedSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Receipt for ${exp.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.ReceiptLong,
                                            contentDescription = null,
                                            tint = tokens.inkMuted,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    if (exp.aiExtracted) {
                                        AiBadge(
                                            label = "AI Scan",
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = exp.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatShortDateLabel(exp.dateLabel),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.inkMuted
                                        )
                                        TabularCurrencyText(
                                            minorUnits = exp.totalMinorUnits,
                                            currency = exp.currencyEnum,
                                            style = TabularSmallStyle
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (rowPair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Date-Grouped Single-Container Expense Sections (THIS WEEK / EARLIER)
            groupedSections.forEach { section ->
                item(key = "section_${section.header}") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = section.header,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = tokens.inkMuted,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                        HairlineSection(shape = RoundedCornerShape(24.dp)) {
                            section.items.forEachIndexed { idx, exp ->
                                val selfId = snapshot.selfMember?.id
                                val selfPaid = if (selfId != null) {
                                    exp.payers.filter { it.memberId == selfId }.sumOf { it.amountMinorUnits }
                                } else 0L
                                val selfShare = if (selfId != null) {
                                    exp.allocations.filter { it.memberId == selfId }.sumOf { it.amountMinorUnits }
                                } else 0L
                                val selfDeltaMinor = selfPaid - selfShare
                                val otherParticipants = exp.allocations
                                    .filter { it.memberId != selfId && it.amountMinorUnits > 0L }
                                    .mapNotNull { memberMap[it.memberId]?.name?.split(" ")?.firstOrNull() }
                                    .distinct()
                                val otherPayers = exp.payers
                                    .filter { it.memberId != selfId && it.amountMinorUnits > 0L }
                                    .mapNotNull { memberMap[it.memberId]?.name?.split(" ")?.firstOrNull() }
                                    .distinct()
                                val personalBalanceLabel: Pair<String, Boolean>? = when {
                                    exp.settled -> null
                                    selfDeltaMinor > 0L -> {
                                        val amtStr = FinanceEngine.formatMinorUnits(selfDeltaMinor, exp.currencyEnum)
                                        val text = if (otherParticipants.size == 1) {
                                            "${otherParticipants.first()} owes you $amtStr"
                                        } else {
                                            "You're owed $amtStr"
                                        }
                                        text to true
                                    }
                                    selfDeltaMinor < 0L -> {
                                        val amtStr = FinanceEngine.formatMinorUnits(-selfDeltaMinor, exp.currencyEnum)
                                        val text = if (otherPayers.size == 1) {
                                            "You owe ${otherPayers.first()} $amtStr"
                                        } else {
                                            "You owe $amtStr"
                                        }
                                        text to false
                                    }
                                    else -> null
                                }

                                val payersLabel = exp.payers.joinToString(" & ") {
                                    memberMap[it.memberId]?.name?.split(" ")?.firstOrNull() ?: "?"
                                }
                                val scopeLabel = exp.groupId?.let { groupMap[it]?.name } ?: "Personal"
                                val subtitleText = buildString {
                                    append(scopeLabel)
                                    if (exp.currencyEnum != snapshot.defaultCurrency) {
                                        append(" · ")
                                        append(FinanceEngine.formatMinorUnits(exp.totalMinorUnits, exp.currencyEnum))
                                        append(" ")
                                        append(exp.currencyEnum.name)
                                    } else if (exp.groupId != null) {
                                        append(" · ")
                                        append(payersLabel)
                                    } else {
                                        append(" · ")
                                        append(formatShortDateLabel(exp.dateLabel))
                                    }
                                }
                                val thumb = remember(exp.receiptPhotoPath) {
                                    val f = ReceiptPhotoStore.resolveFile(context, exp.receiptPhotoPath)
                                    if (f != null) BitmapFactory.decodeFile(f.absolutePath) else null
                                }
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleSettled(exp)
                                                false
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onDeleteExpense(exp)
                                                true
                                            }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        val isStart = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(if (isStart) tokens.positiveContainer else tokens.negativeContainer)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isStart) Icons.Rounded.CheckCircle else Icons.Rounded.DeleteOutline,
                                                    contentDescription = null,
                                                    tint = if (isStart) tokens.positive else tokens.negative
                                                )
                                                Text(
                                                    text = if (isStart) (if (exp.settled) "Reopen" else "Settle") else "Delete",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isStart) tokens.positive else tokens.negative
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(tokens.cardSurface)
                                            .springClickable(pressedScale = 0.985f) { onOpenSharedSplit(exp) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                            .testTag("ledger_expense_${exp.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Circular monochrome category icon badge or receipt thumbnail
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb.asImageBitmap(),
                                                contentDescription = "View receipt for ${exp.title}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, tokens.hairline, CircleShape)
                                                    .clickable { onViewReceipt(exp) }
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(tokens.elevatedSurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = categoryIconFor(exp),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = exp.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (exp.aiExtracted) {
                                                    AiBadge(if (exp.shareCode.startsWith("voice-")) "AI Voice" else "AI Scan")
                                                }
                                                if (exp.settled) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = tokens.positiveContainer
                                                    ) {
                                                        Text(
                                                            text = "Settled",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = tokens.positive,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (personalBalanceLabel != null) {
                                                Text(
                                                    text = personalBalanceLabel.first,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (personalBalanceLabel.second) tokens.positive else tokens.negative,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (exp.recurringEnum != RecurringFrequency.NONE) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${exp.recurringEnum.label} • next ${exp.nextDueDateLabel ?: "soon"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = tokens.positive
                                                    )
                                                    Text(
                                                        text = "Log Cycle",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(tokens.elevatedSurface)
                                                            .clickable { onLogRecurringCycle(exp) }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val displayMinor = if (exp.currencyEnum != snapshot.defaultCurrency) {
                                                FinanceEngine.convertMinorUnits(
                                                    exp.totalMinorUnits,
                                                    exp.currencyEnum,
                                                    snapshot.defaultCurrency,
                                                    exp.fxRateToBaseMicros
                                                )
                                            } else {
                                                exp.totalMinorUnits
                                            }
                                            TabularCurrencyText(
                                                minorUnits = displayMinor,
                                                currency = snapshot.defaultCurrency,
                                                style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                SpringIconButton(
                                                    onClick = { onEditExpense(exp) },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("edit_expense_${exp.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = "Edit ${exp.title}",
                                                        tint = tokens.inkMuted,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                SpringIconButton(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onDeleteExpense(exp)
                                                    },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .testTag("delete_expense_${exp.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Delete ${exp.title}",
                                                        tint = tokens.negative,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (idx < section.items.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = tokens.hairline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recorded Settlements Section (in All mode)
        if (activityMode == ActivityMode.ALL && snapshot.settlements.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RECORDED SETTLEMENTS (${snapshot.settlements.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = tokens.inkMuted,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                    HairlineSection(shape = RoundedCornerShape(24.dp)) {
                        snapshot.settlements.forEachIndexed { idx, settlement ->
                            val from = memberMap[settlement.fromMemberId]?.name ?: "Member"
                            val to = memberMap[settlement.toMemberId]?.name ?: "Member"
                            val settleDismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteSettlement(settlement)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = settleDismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(tokens.negativeContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = tokens.negative
                                            )
                                            Text(
                                                text = "Delete",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = tokens.negative
                                            )
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(tokens.cardSurface)
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("ledger_settlement_${settlement.id}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$from paid $to",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            text = "${settlement.paymentMethodEnum.label} · ${formatShortDateLabel(settlement.dateLabel)}" +
                                                (settlement.note?.let { " · $it" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkMuted
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (!settlement.paymentProofPath.isNullOrBlank()) {
                                                SpringSurface(
                                                    onClick = { onViewSettlementProof(settlement) },
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = tokens.positiveContainer,
                                                    modifier = Modifier.testTag("view_settlement_proof_${settlement.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Verified,
                                                            contentDescription = "View payment proof",
                                                            tint = tokens.positive,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = "Proof",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = tokens.positive
                                                        )
                                                    }
                                                }
                                            }
                                            TabularCurrencyText(
                                                minorUnits = settlement.amountMinorUnits,
                                                currency = settlement.currencyEnum,
                                                style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold),
                                                color = tokens.positive
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            SpringIconButton(
                                                onClick = { editingSettlement = settlement },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("edit_settlement_${settlement.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Edit,
                                                    contentDescription = "Edit settlement",
                                                    tint = tokens.inkMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            SpringIconButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onDeleteSettlement(settlement)
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("delete_settlement_${settlement.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete settlement",
                                                    tint = tokens.negative,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (idx < snapshot.settlements.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = tokens.hairline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Compact Filter Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Activity",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    SpringIconButton(onClick = { showFilterSheet = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close filters")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = tokens.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusFilter.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Group / Scope", style = MaterialTheme.typography.labelMedium, color = tokens.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedGroupFilterId == null,
                            onClick = { selectedGroupFilterId = null },
                            label = { Text("All Scopes") }
                        )
                        FilterChip(
                            selected = selectedGroupFilterId == "__personal__",
                            onClick = { selectedGroupFilterId = "__personal__" },
                            label = { Text("1-on-1 Personal") }
                        )
                        snapshot.groups.forEach { grp ->
                            FilterChip(
                                selected = selectedGroupFilterId == grp.id,
                                onClick = { selectedGroupFilterId = if (selectedGroupFilterId == grp.id) null else grp.id },
                                label = { Text(grp.name) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = tokens.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { selectedCategoryFilter = null },
                            label = { Text("Any") }
                        )
                        ExpenseCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpringOutlinedButton(
                        onClick = {
                            selectedStatus = StatusFilter.ALL
                            selectedGroupFilterId = null
                            selectedCategoryFilter = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, tokens.hairline)
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.onSurface)
                    }
                    SpringButton(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.obsidian,
                            contentColor = tokens.onObsidian
                        )
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Edit Settlement ModalBottomSheet
    val targetSettlement = editingSettlement
    if (targetSettlement != null) {
        var amountInput by remember(targetSettlement.id) {
            mutableStateOf(FinanceEngine.minorToMajorInput(targetSettlement.amountMinorUnits))
        }
        var method by remember(targetSettlement.id) {
            mutableStateOf(targetSettlement.paymentMethodEnum)
        }
        var dateInput by remember(targetSettlement.id) {
            mutableStateOf(targetSettlement.dateLabel)
        }
        var noteInput by remember(targetSettlement.id) {
            mutableStateOf(targetSettlement.note.orEmpty())
        }
        val fromName = memberMap[targetSettlement.fromMemberId]?.name ?: "Member"
        val toName = memberMap[targetSettlement.toMemberId]?.name ?: "Member"
        val parsedAmountMinor = FinanceEngine.parseMajorToMinor(amountInput)

        ModalBottomSheet(
            onDismissRequest = { editingSettlement = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .testTag("edit_settlement_sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit Settlement",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$fromName paid $toName",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                    SpringIconButton(onClick = { editingSettlement = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close edit settlement")
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount (${targetSettlement.currencyEnum.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_settlement_amount_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Payment Method", style = MaterialTheme.typography.labelMedium, color = tokens.inkMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.entries.forEach { pm ->
                            FilterChip(
                                selected = method == pm,
                                onClick = { method = pm },
                                label = { Text(pm.label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDeleteSettlement(targetSettlement)
                            editingSettlement = null
                        },
                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.negative),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_settlement_in_sheet_button")
                    ) {
                        Text("Delete")
                    }
                    Button(
                        onClick = {
                            onUpdateSettlement(
                                targetSettlement.copy(
                                    amountMinorUnits = parsedAmountMinor,
                                    paymentMethod = method.name,
                                    dateLabel = dateInput.trim().ifBlank { targetSettlement.dateLabel },
                                    note = noteInput.trim().takeIf { it.isNotEmpty() }
                                )
                            )
                            editingSettlement = null
                        },
                        enabled = parsedAmountMinor > 0L,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_settlement_edit_button")
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

private fun categoryIconFor(exp: ExpenseRecord): ImageVector {
    val titleLower = exp.title.lowercase(Locale.ROOT)
    return when {
        exp.recurringEnum != RecurringFrequency.NONE -> Icons.Rounded.Repeat
        titleLower.contains("coffee") || titleLower.contains("cafe") || titleLower.contains("tokai") -> Icons.Rounded.LocalCafe
        titleLower.contains("cab") || titleLower.contains("uber") || titleLower.contains("activa") -> Icons.Rounded.DirectionsCar
        else -> when (exp.categoryEnum) {
            ExpenseCategory.FOOD -> Icons.Rounded.Restaurant
            ExpenseCategory.GROCERIES -> Icons.Rounded.ShoppingBag
            ExpenseCategory.TRAVEL -> Icons.Rounded.Flight
            ExpenseCategory.STAY -> Icons.Rounded.Home
            ExpenseCategory.UTILITIES -> Icons.Rounded.Bolt
            ExpenseCategory.ENTERTAINMENT -> Icons.Rounded.LocalPlay
            ExpenseCategory.SHOPPING -> Icons.Rounded.ShoppingBag
            ExpenseCategory.OTHER -> Icons.Rounded.ReceiptLong
        }
    }
}

private fun formatShortDateLabel(isoDate: String): String {
    return runCatching {
        val parsed = LocalDate.parse(isoDate.take(10))
        parsed.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    }.getOrElse { isoDate }
}

private fun bucketExpensesByRecency(expenses: List<ExpenseRecord>): List<ExpenseDateSection> {
    if (expenses.isEmpty()) return emptyList()
    val parsedDates = expenses.mapNotNull { runCatching { LocalDate.parse(it.dateLabel.take(10)) }.getOrNull() }
    val referenceDate = parsedDates.maxOrNull() ?: LocalDate.now()

    val thisWeek = mutableListOf<ExpenseRecord>()
    val earlier = mutableListOf<ExpenseRecord>()

    expenses.forEach { exp ->
        val date = runCatching { LocalDate.parse(exp.dateLabel.take(10)) }.getOrNull()
        if (date == null || ChronoUnit.DAYS.between(date, referenceDate) <= 7L) {
            thisWeek += exp
        } else {
            earlier += exp
        }
    }

    return buildList {
        if (thisWeek.isNotEmpty()) add(ExpenseDateSection("THIS WEEK", thisWeek))
        if (earlier.isNotEmpty()) add(ExpenseDateSection("EARLIER", earlier))
    }
}
