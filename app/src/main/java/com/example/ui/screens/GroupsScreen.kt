package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Celebration
import com.example.engine.FinanceEngine
import com.example.model.BudgetHealth
import com.example.model.CurrencyCode
import com.example.model.DebtTransfer
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.model.GroupCategory
import com.example.ui.components.AiBadge
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DailyBurnMeterCard
import com.example.ui.components.EditorialCard
import com.example.ui.components.GuestBadge
import com.example.ui.components.HairlineSection
import com.example.ui.components.MemberAvatar
import com.example.ui.components.TabularCurrencyText
import com.example.ui.components.parseHexColor
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringFilledTonalButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringNavHostContainer
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.SpringTransitionStyle
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.ui.theme.TabularTitleStyle
import com.example.viewmodel.LedgerSnapshot

@Composable
fun GroupsScreen(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    selectedGroupId: String?,
    onOpenProfile: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onOpenCreateGroup: (GroupCategory, String?) -> Unit,
    onUpdateGroupBudget: (String, Long?) -> Unit,
    onUpdateGroupMemory: (String, String?) -> Unit,
    onOpenTripStory: (ExpenseGroup) -> Unit,
    onAddExpenseToGroup: (ExpenseGroup) -> Unit,
    onOpenSettleUp: (DebtTransfer) -> Unit,
    onOpenNudge: (DebtTransfer) -> Unit,
    onOpenSpendCoachForGroup: (String) -> Unit,
    onOpenSharedSplit: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedGroup = remember(snapshot.groups, selectedGroupId) {
        snapshot.groups.firstOrNull { it.id == selectedGroupId }
    }

    if (selectedGroup != null) {
        BackHandler { onSelectGroup(null) }
    }

    SpringNavHostContainer(
        targetState = selectedGroup,
        style = SpringTransitionStyle.PUSH_POP,
        isForward = { initial, target -> initial == null && target != null },
        contentKey = { it?.id ?: "groups_hub" },
        label = "group_push_pop"
    ) { group ->
        if (group != null) {
            GroupDetailView(
                group = group,
                snapshot = snapshot,
                onBack = { onSelectGroup(null) },
                onUpdateBudget = { cap -> onUpdateGroupBudget(group.id, cap) },
                onUpdateMemory = { notes -> onUpdateGroupMemory(group.id, notes) },
                onOpenTripStory = { onOpenTripStory(group) },
                onAddExpense = { onAddExpenseToGroup(group) },
                onOpenSettleUp = onOpenSettleUp,
                onOpenNudge = onOpenNudge,
                onOpenSpendCoach = { onOpenSpendCoachForGroup(group.id) },
                onOpenSharedSplit = onOpenSharedSplit,
                modifier = modifier
            )
        } else {
            GroupsHubList(
                snapshot = snapshot,
                isGuestMode = isGuestMode,
                onOpenProfile = onOpenProfile,
                onSelectGroup = { onSelectGroup(it.id) },
                onOpenCreateGroup = onOpenCreateGroup,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupsHubList(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    onOpenProfile: () -> Unit,
    onSelectGroup: (ExpenseGroup) -> Unit,
    onOpenCreateGroup: (GroupCategory, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }

    val groupCardsData = remember(snapshot.groups, snapshot.expenses, snapshot.settlements, snapshot.members) {
        snapshot.groups.map { group ->
            val groupExpenses = snapshot.expenses.filter { it.groupId == group.id }
            val budgetStatus = FinanceEngine.computeGroupBudgetStatus(group, groupExpenses)
            val groupMembers = group.memberIds.mapNotNull { memberMap[it] }
            val hasForeignFx = groupExpenses.any { it.currencyEnum != group.currencyEnum }
            val report = FinanceEngine.computeDebtSimplification(
                members = groupMembers,
                expenses = snapshot.expenses,
                settlements = snapshot.settlements,
                groupIdFilter = group.id,
                currency = group.currencyEnum
            )
            val selfBalance = report.balances.firstOrNull { it.memberId == snapshot.selfMember?.id }?.netMinorUnits ?: 0L
            GroupSummaryCardModel(
                group = group,
                budgetStatus = budgetStatus,
                members = groupMembers,
                hasForeignFx = hasForeignFx,
                selfBalanceMinor = selfBalance
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("groups_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Editorial Header: Groups + Matte Obsidian + New pill
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Groups",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringButton(
                        onClick = { onOpenCreateGroup(GroupCategory.TRIP, null) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.obsidian,
                            contentColor = tokens.onObsidian
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                        modifier = Modifier.testTag("create_group_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelLarge)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .springClickable(pressedScale = 0.90f, onClick = onOpenProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(member = snapshot.selfMember, size = 36.dp, selected = true, showGuestDot = false)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isGuestMode) tokens.acidLime else tokens.positive)
                                .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
            }
        }

        // 2-Column Compact Metric Summary Strip
        if (groupCardsData.isNotEmpty()) {
            item {
                val first = groupCardsData.getOrNull(0)
                val second = groupCardsData.getOrNull(1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (first != null) {
                        CompactGroupMetricChip(
                            title = first.group.name,
                            amountMinor = first.selfBalanceMinor,
                            currency = first.group.currencyEnum,
                            category = first.group.categoryEnum,
                            onClick = { onSelectGroup(first.group) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (second != null) {
                        CompactGroupMetricChip(
                            title = second.group.name,
                            amountMinor = second.selfBalanceMinor,
                            currency = second.group.currencyEnum,
                            category = second.group.categoryEnum,
                            onClick = { onSelectGroup(second.group) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val totalSpend = groupCardsData.sumOf { it.budgetStatus.spentMinorUnits }
                        CompactGroupMetricChip(
                            title = "Total Tracked",
                            amountMinor = totalSpend,
                            currency = snapshot.defaultCurrency,
                            category = GroupCategory.PERSONAL,
                            showPlus = false,
                            onClick = { onOpenCreateGroup(GroupCategory.HOME, null) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Spacious White Editorial Group Cards (rounded 24dp, thin progress bar, overlapping avatars, max 1 subtle tag)
        items(groupCardsData, key = { it.group.id }) { item ->
            val group = item.group
            val budgetStatus = item.budgetStatus
            val selfBalance = item.selfBalanceMinor

            EditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_item_${group.id}"),
                shape = RoundedCornerShape(24.dp),
                onClick = { onSelectGroup(group) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Total ${FinanceEngine.formatMinorUnits(budgetStatus.spentMinorUnits, group.currencyEnum)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkMuted
                            )
                        }
                        TabularCurrencyText(
                            minorUnits = selfBalance,
                            currency = group.currencyEnum,
                            showPlusSign = true,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = when {
                                selfBalance > 0L -> tokens.positive
                                selfBalance < 0L -> tokens.negative
                                else -> tokens.inkMuted
                            }
                        )
                    }

                    if (budgetStatus.budgetMinorUnits != null) {
                        BudgetProgressBar(
                            status = budgetStatus,
                            currency = group.currencyEnum,
                            compact = true,
                            showStatusLabel = false
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Overlapping circular avatars
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            item.members.take(5).forEachIndexed { idx, member ->
                                MemberAvatar(
                                    member = member,
                                    size = 30.dp,
                                    selected = true,
                                    showGuestDot = false,
                                    ringColor = tokens.cardSurface,
                                    modifier = Modifier.offset(x = (-7 * idx).dp)
                                )
                            }
                            if (item.members.size > 5) {
                                Text(
                                    text = "+${item.members.size - 5}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }

                        // Max 1 subtle metadata tag
                        if (item.hasForeignFx) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.elevatedSurface
                            ) {
                                Text(
                                    text = "Multi-FX",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dashed-Border "Start a shared ledger" Card with Quick Template Chips (Trip, Flatmates, Event)
        item {
            val dashColor = tokens.hairline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = dashColor,
                            cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                            )
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .testTag("start_shared_ledger_card"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (groupCardsData.isEmpty()) "Create your first group" else "Start a shared ledger",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Pick a 1-tap template or start custom — add friends & contacts right inside",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                    SpringButton(
                        onClick = { onOpenCreateGroup(GroupCategory.TRIP, null) },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.acidLime,
                            contentColor = tokens.onAcidLime
                        ),
                        modifier = Modifier.testTag("groups_empty_create_button")
                    ) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Create New Group",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val templates = listOf(
                            Triple("Trip", GroupCategory.TRIP, "Weekend Trip"),
                            Triple("Flatmates", GroupCategory.HOME, "Flatmates"),
                            Triple("Dinner", GroupCategory.EVENT, "Dinner Squad"),
                            Triple("Event", GroupCategory.EVENT, "House Party")
                        )
                        templates.forEach { (label, cat, suggested) ->
                            SpringSurface(
                                onClick = { onOpenCreateGroup(cat, suggested) },
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.cardSurface,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.testTag("group_template_${label.lowercase()}")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class GroupSummaryCardModel(
    val group: ExpenseGroup,
    val budgetStatus: com.example.model.GroupBudgetStatus,
    val members: List<com.example.model.LedgerMember>,
    val hasForeignFx: Boolean,
    val selfBalanceMinor: Long
)

@Composable
private fun CompactGroupMetricChip(
    title: String,
    amountMinor: Long,
    currency: CurrencyCode,
    category: GroupCategory,
    onClick: () -> Unit,
    showPlus: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val icon = when (category) {
        GroupCategory.TRIP -> Icons.Rounded.Flight
        GroupCategory.HOME -> Icons.Rounded.Home
        GroupCategory.EVENT -> Icons.Rounded.Celebration
        else -> Icons.Rounded.Groups
    }
    EditorialCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tokens.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TabularCurrencyText(
                    minorUnits = amountMinor,
                    currency = currency,
                    showPlusSign = showPlus,
                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        !showPlus -> MaterialTheme.colorScheme.onSurface
                        amountMinor > 0L -> tokens.positive
                        amountMinor < 0L -> tokens.negative
                        else -> tokens.inkMuted
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupDetailView(
    group: ExpenseGroup,
    snapshot: LedgerSnapshot,
    onBack: () -> Unit,
    onUpdateBudget: (Long?) -> Unit,
    onUpdateMemory: (String?) -> Unit,
    onOpenTripStory: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenSettleUp: (DebtTransfer) -> Unit,
    onOpenNudge: (DebtTransfer) -> Unit,
    onOpenSpendCoach: () -> Unit,
    onOpenSharedSplit: (ExpenseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }
    val groupMembers = remember(group, snapshot.members) {
        group.memberIds.mapNotNull { memberMap[it] }
    }
    val groupExpenses = remember(group.id, snapshot.expenses) {
        snapshot.expenses.filter { it.groupId == group.id }
    }
    val groupSettlements = remember(group.id, snapshot.settlements) {
        snapshot.settlements.filter { it.groupId == group.id }
    }
    val budgetStatus = remember(group, groupExpenses) {
        FinanceEngine.computeGroupBudgetStatus(group, groupExpenses)
    }
    val dailyBurnStats = remember(group, groupExpenses, budgetStatus) {
        FinanceEngine.computeDailyBurnStats(group, groupExpenses, budgetStatus)
    }
    val foreignCurrencies = remember(groupExpenses, group.currencyEnum) {
        groupExpenses.map { it.currencyEnum }.filter { it != group.currencyEnum }.distinct()
    }
    val debtReport = remember(groupMembers, groupExpenses, groupSettlements) {
        FinanceEngine.computeDebtSimplification(
            members = groupMembers,
            expenses = snapshot.expenses,
            settlements = snapshot.settlements,
            groupIdFilter = group.id,
            currency = group.currencyEnum
        )
    }
    val categoryBreakdown = remember(groupExpenses) {
        FinanceEngine.computeCategoryBreakdown(groupExpenses)
    }
    var showSimplified by remember { mutableStateOf(true) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }

    fun sharePlainText(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("group_detail_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Back button
        item {
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
                    SpringIconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("group_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to groups")
                    }
                    Column {
                        Text(
                            text = "${group.categoryEnum.label.uppercase()} · ${group.currencyEnum.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = parseHexColor(group.accentColorHex)
                        )
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                SpringFilledTonalButton(
                    onClick = onAddExpense,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("group_add_expense_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Split")
                }
            }
        }

        // Spend Metrics + Budget Health Cap + FX Normalization Badge
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GROUP TOTAL SPEND",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted
                            )
                            TabularCurrencyText(
                                minorUnits = budgetStatus.spentMinorUnits,
                                currency = group.currencyEnum,
                                style = TabularTitleStyle
                            )
                        }
                        SpringOutlinedButton(
                            onClick = { showBudgetDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, tokens.hairline),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("edit_group_budget_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (group.budgetMinorUnits != null) "Edit Cap" else "Set Budget",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (budgetStatus.budgetMinorUnits != null) {
                        BudgetProgressBar(
                            status = budgetStatus,
                            currency = group.currencyEnum
                        )
                        if (budgetStatus.health == BudgetHealth.WARNING || budgetStatus.health == BudgetHealth.EXCEEDED) {
                            val isOver = budgetStatus.health == BudgetHealth.EXCEEDED
                            val alertTint = if (isOver) tokens.negative else tokens.warning
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isOver) tokens.negativeContainer else tokens.warningContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = alertTint, modifier = Modifier.size(17.dp))
                                        Text(
                                            text = if (isOver) {
                                                "Over budget by ${FinanceEngine.formatMinorUnits(-budgetStatus.remainingMinorUnits, group.currencyEnum)}"
                                            } else {
                                                "${FinanceEngine.formatMinorUnits(budgetStatus.remainingMinorUnits, group.currencyEnum)} left before cap"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "AI Coach",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.aiAccent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onOpenSpendCoach)
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("group_budget_ai_coach_button")
                                    )
                                }
                            }
                        }
                    }

                    if (foreignCurrencies.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = tokens.aiAccent, modifier = Modifier.size(15.dp))
                            Text(
                                text = "Includes FX-converted ${foreignCurrencies.joinToString(", ") { it.name }} expenses normalized into ${group.currencyEnum.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpringFilledTonalButton(
                                onClick = onOpenTripStory,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_trip_story_button")
                            ) {
                                Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = tokens.aiAccent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Trip Story", style = MaterialTheme.typography.labelMedium)
                            }
                            SpringOutlinedButton(
                                onClick = onOpenSpendCoach,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_ai_coach_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = tokens.aiAccent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("AI Coach", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpringOutlinedButton(
                                onClick = {
                                    val csv = FinanceEngine.exportLedgerToCsv(
                                        scopeName = group.name,
                                        members = groupMembers,
                                        expenses = groupExpenses,
                                        settlements = groupSettlements
                                    )
                                    sharePlainText("${group.name} - SplitFlow CSV", csv)
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_group_csv_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Export CSV", style = MaterialTheme.typography.labelMedium)
                            }
                            SpringOutlinedButton(
                                onClick = {
                                    val summary = FinanceEngine.buildPrintableSettlementSummary(
                                        title = group.name,
                                        members = groupMembers,
                                        expenses = groupExpenses,
                                        report = debtReport,
                                        currency = group.currencyEnum
                                    )
                                    sharePlainText("${group.name} - Settlement Summary", summary)
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_group_summary_button")
                            ) {
                                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Summary", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Daily Burn Meter Card
        if (groupExpenses.isNotEmpty()) {
            item {
                DailyBurnMeterCard(
                    stats = dailyBurnStats,
                    currency = group.currencyEnum
                )
            }
        }

        // Smart Split Memory Card
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = tokens.aiAccent,
                                modifier = Modifier.size(17.dp)
                            )
                            Text("Smart Split Memory", style = MaterialTheme.typography.titleSmall)
                            AiBadge("AI RULE")
                        }
                        TextButton(
                            onClick = { showMemoryDialog = true },
                            modifier = Modifier.testTag("edit_group_memory_button")
                        ) {
                            Text(if (group.splitMemoryNotes.isNullOrBlank()) "Add Rules" else "Edit")
                        }
                    }
                    Text(
                        text = group.splitMemoryNotes?.takeIf { it.isNotBlank() }
                            ?: "Teach SplitFlow group defaults (e.g., \"Alex is vegetarian\", \"Sam & Priya share room costs 60/40\"). Injected automatically into AI Voice & Lens splits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (group.splitMemoryNotes.isNullOrBlank()) tokens.inkMuted else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Category Breakdown Chart
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
                    CategoryDonutChart(breakdown = categoryBreakdown, currency = group.currencyEnum)
                }
            }
        }

        // Group Debt Simplifier (Hairline List + Nudge + Settle)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.AccountTree, contentDescription = null, tint = tokens.positive, modifier = Modifier.size(18.dp))
                        Text("Debt Simplification", style = MaterialTheme.typography.titleMedium)
                    }
                    if (debtReport.transfersSaved > 0) {
                        Surface(shape = RoundedCornerShape(999.dp), color = tokens.positiveContainer) {
                            Text(
                                text = "Saved ${debtReport.transfersSaved}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.positive,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.cardSurface)
                        .border(1.dp, tokens.hairline, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToggleSegment(
                        label = "Simplified (${debtReport.simplifiedTransfers.size})",
                        selected = showSimplified,
                        onClick = { showSimplified = true },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleSegment(
                        label = "Raw (${debtReport.rawTransfers.size})",
                        selected = !showSimplified,
                        onClick = { showSimplified = false },
                        modifier = Modifier.weight(1f)
                    )
                }

                val list = if (showSimplified) debtReport.simplifiedTransfers else debtReport.rawTransfers
                HairlineSection {
                    if (list.isEmpty()) {
                        Text(
                            "All group balances are settled!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.positive,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        list.forEachIndexed { idx, t ->
                            val from = memberMap[t.fromMemberId]
                            val to = memberMap[t.toMemberId]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    MemberAvatar(from, size = 30.dp)
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(from?.name?.split(" ")?.firstOrNull() ?: "?", style = MaterialTheme.typography.titleSmall)
                                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Text(to?.name?.split(" ")?.firstOrNull() ?: "?", style = MaterialTheme.typography.titleSmall)
                                        }
                                        TabularCurrencyText(
                                            minorUnits = t.amountMinorUnits,
                                            currency = group.currencyEnum,
                                            style = TabularSmallStyle,
                                            color = tokens.positive
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SpringIconButton(onClick = { onOpenNudge(t) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsActive,
                                            contentDescription = "Send payment nudge",
                                            tint = tokens.inkMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    SpringFilledTonalButton(
                                        onClick = { onOpenSettleUp(t) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Settle", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (idx < list.lastIndex) {
                                HorizontalDivider(color = tokens.hairline)
                            }
                        }
                    }
                }
            }
        }

        // Hairline-Divided Member Net Balances
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Member Ledger", style = MaterialTheme.typography.titleMedium)
                HairlineSection {
                    val sorted = debtReport.balances.sortedByDescending { it.netMinorUnits }
                    sorted.forEachIndexed { idx, bal ->
                        val member = memberMap[bal.memberId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MemberAvatar(member, size = 32.dp)
                                Text(member?.name ?: "Member", style = MaterialTheme.typography.bodyMedium)
                                if (member != null) GuestBadge(member.guestProfile)
                            }
                            TabularCurrencyText(
                                minorUnits = bal.netMinorUnits,
                                currency = group.currencyEnum,
                                showPlusSign = true,
                                color = when {
                                    bal.netMinorUnits > 0L -> tokens.positive
                                    bal.netMinorUnits < 0L -> tokens.negative
                                    else -> tokens.inkMuted
                                }
                            )
                        }
                        if (idx < sorted.lastIndex) {
                            HorizontalDivider(color = tokens.hairline)
                        }
                    }
                }
            }
        }

        // Hairline-Divided Group Expenses
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Group Expenses (${groupExpenses.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                HairlineSection {
                    groupExpenses.forEachIndexed { idx, exp ->
                        val payersStr = exp.payers.mapNotNull { memberMap[it.memberId]?.name?.split(" ")?.firstOrNull() }.joinToString(" & ")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .springClickable(pressedScale = 0.985f) { onOpenSharedSplit(exp) }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(exp.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (exp.aiExtracted) AiBadge("AI")
                                }
                                Text(
                                    "$payersStr paid · ${exp.splitTypeEnum.label} · ${exp.dateLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                TabularCurrencyText(minorUnits = exp.totalMinorUnits, currency = exp.currencyEnum)
                                if (exp.currencyEnum != group.currencyEnum) {
                                    val conv = FinanceEngine.convertMinorUnits(
                                        exp.totalMinorUnits,
                                        exp.currencyEnum,
                                        group.currencyEnum,
                                        exp.fxRateToBaseMicros
                                    )
                                    Text(
                                        text = "≈ ${FinanceEngine.formatMinorUnits(conv, group.currencyEnum)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkMuted
                                    )
                                }
                            }
                        }
                        if (idx < groupExpenses.lastIndex) {
                            HorizontalDivider(color = tokens.hairline)
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        EditGroupBudgetDialog(
            group = group,
            onDismiss = { showBudgetDialog = false },
            onSave = { cap ->
                onUpdateBudget(cap)
                showBudgetDialog = false
            }
        )
    }

    if (showMemoryDialog) {
        EditGroupMemoryDialog(
            group = group,
            onDismiss = { showMemoryDialog = false },
            onSave = { notes ->
                onUpdateMemory(notes)
                showMemoryDialog = false
            }
        )
    }
}

@Composable
private fun EditGroupMemoryDialog(
    group: ExpenseGroup,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var notesInput by remember(group.id, group.splitMemoryNotes) {
        mutableStateOf(group.splitMemoryNotes.orEmpty())
    }
    val tokens = SplitFlowThemeTokens.colors
    val focusedBorder = if (tokens.isDark) tokens.acidLime else tokens.obsidian
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = tokens.cardSurface,
        title = { Text("Smart Split Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Store persistent split rules for ${group.name} (max 400 chars). Local Voice Split & Receipt OCR use these automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkMuted
                )
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { if (it.length <= 400) notesInput = it },
                    label = { Text("Group Split Defaults") },
                    placeholder = { Text("e.g. Alex is vegetarian; Sam & Priya share rooms 60/40") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    supportingText = { Text("${notesInput.length}/400") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.elevatedSurface,
                        unfocusedContainerColor = tokens.elevatedSurface,
                        focusedBorderColor = focusedBorder,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_memory_input")
                )
            }
        },
        confirmButton = {
            SpringButton(
                onClick = { onSave(notesInput.trim().takeIf { it.isNotBlank() }) },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.acidLime,
                    contentColor = tokens.onAcidLime
                ),
                modifier = Modifier.testTag("save_group_memory_button")
            ) {
                Text("Save Memory", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditGroupBudgetDialog(
    group: ExpenseGroup,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val focusedBorder = if (tokens.isDark) tokens.acidLime else tokens.obsidian
    var budgetInput by remember(group) {
        mutableStateOf(group.budgetMinorUnits?.let { FinanceEngine.minorToMajorInput(it) } ?: "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = tokens.cardSurface,
        title = { Text("Group Budget Cap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Set a spending limit in ${group.currencyEnum.name} (${group.currencyEnum.symbol}) to receive amber alerts at 78% and overspend warnings at 100%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkMuted
                )
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it },
                    label = { Text("Budget cap (${group.currencyEnum.symbol})") },
                    placeholder = { Text("e.g., 35000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.elevatedSurface,
                        unfocusedContainerColor = tokens.elevatedSurface,
                        focusedBorderColor = focusedBorder,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_budget_cap_input")
                )
            }
        },
        confirmButton = {
            SpringButton(
                onClick = {
                    val minor = FinanceEngine.parseMajorToMinor(budgetInput)
                    onSave(minor.takeIf { it > 0L })
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.acidLime,
                    contentColor = tokens.onAcidLime
                ),
                modifier = Modifier.testTag("save_budget_cap_button")
            ) {
                Text("Save Cap", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (group.budgetMinorUnits != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
