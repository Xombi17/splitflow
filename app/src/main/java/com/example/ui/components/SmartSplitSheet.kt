package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.ui.text.font.FontWeight
import com.example.data.ReceiptPhotoStore
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.DietPreference
import com.example.model.ExpenseCategory
import com.example.model.ExpenseRecord
import com.example.model.LineItemTag
import com.example.model.PayerEntry
import com.example.model.ReceiptLineItem
import com.example.model.RecurringFrequency
import com.example.model.SplitAllocation
import com.example.model.SplitType
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot
import java.util.UUID
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartSplitSheet(
    snapshot: LedgerSnapshot,
    editingExpense: ExpenseRecord?,
    initialGroupId: String?,
    initialPersonalFriendId: String?,
    todayIso: String,
    onSaveExpense: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit,
    onDeleteExpense: ((ExpenseRecord) -> Unit)? = null,
    onOpenDiningStudio: (() -> Unit)? = null
) {
    val tokens = SplitFlowThemeTokens.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selfId = snapshot.selfMember?.id ?: snapshot.members.firstOrNull()?.id.orEmpty()

    var title by remember(editingExpense) { mutableStateOf(editingExpense?.title ?: "") }
    var amountText by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.let { FinanceEngine.minorToMajorInput(it.totalMinorUnits) } ?: ""
        )
    }
    var notes by remember(editingExpense) { mutableStateOf(editingExpense?.notes ?: "") }
    var currency by remember(editingExpense) {
        mutableStateOf(editingExpense?.currencyEnum ?: snapshot.defaultCurrency)
    }
    var category by remember(editingExpense) {
        mutableStateOf(editingExpense?.categoryEnum ?: ExpenseCategory.FOOD)
    }
    var splitType by remember(editingExpense) {
        mutableStateOf(editingExpense?.splitTypeEnum ?: SplitType.EQUAL)
    }
    var recurringFrequency by remember(editingExpense) {
        mutableStateOf(editingExpense?.recurringEnum ?: RecurringFrequency.NONE)
    }
    var receiptPhotoPath by remember(editingExpense) {
        mutableStateOf(editingExpense?.receiptPhotoPath)
    }
    var selectedGroupId by remember(editingExpense, initialGroupId, initialPersonalFriendId) {
        mutableStateOf(
            when {
                editingExpense != null -> editingExpense.groupId
                initialPersonalFriendId != null -> null
                else -> initialGroupId ?: snapshot.groups.firstOrNull()?.id
            }
        )
    }
    val selectedGroup = remember(selectedGroupId, snapshot.groups) {
        snapshot.groups.firstOrNull { it.id == selectedGroupId }
    }
    val targetBaseCurrency = selectedGroup?.currencyEnum ?: snapshot.defaultCurrency
    var editingFxRate by remember { mutableStateOf(false) }
    var customFxRateText by remember(editingExpense, currency, targetBaseCurrency) {
        val initialMicros = editingExpense?.fxRateToBaseMicros
            ?: FinanceEngine.getFxRateMicros(currency, targetBaseCurrency)
        mutableStateOf(FinanceEngine.formatFxRate(initialMicros))
    }
    val receiptPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = ReceiptPhotoStore.saveFromUri(context, uri)
            if (savedPath != null) {
                receiptPhotoPath = savedPath
            }
        }
    }

    val scopeMembers = remember(selectedGroupId, initialPersonalFriendId, snapshot.groups, snapshot.members) {
        val grp = snapshot.groups.firstOrNull { it.id == selectedGroupId }
        when {
            grp != null -> {
                val ids = grp.memberIds.toSet()
                snapshot.members.filter { it.id in ids }.ifEmpty { snapshot.members }
            }
            initialPersonalFriendId != null -> {
                snapshot.members.filter { it.id == selfId || it.id == initialPersonalFriendId }
                    .ifEmpty { snapshot.members }
            }
            else -> snapshot.members
        }
    }

    // Multiple payers state
    var multiPayerMode by remember(editingExpense) {
        mutableStateOf((editingExpense?.payers?.size ?: 1) > 1)
    }
    var singlePayerId by remember(editingExpense) {
        mutableStateOf(editingExpense?.payers?.firstOrNull()?.memberId ?: selfId)
    }
    val multiPayerInputs = remember(editingExpense) {
        mutableStateMapOf<String, String>().apply {
            editingExpense?.payers?.forEach { p ->
                put(p.memberId, FinanceEngine.minorToMajorInput(p.amountMinorUnits))
            }
        }
    }

    // Equal participants
    val equalSelectedIds = remember(editingExpense, scopeMembers) {
        mutableStateListOf<String>().apply {
            if (editingExpense != null) {
                addAll(editingExpense.allocations.map { it.memberId }.distinct())
            } else {
                addAll(scopeMembers.map { it.id })
            }
        }
    }

    // Exact / Percent / Shares maps
    val exactInputs = remember(editingExpense) {
        mutableStateMapOf<String, String>().apply {
            editingExpense?.allocations?.forEach {
                put(it.memberId, FinanceEngine.minorToMajorInput(it.amountMinorUnits))
            }
        }
    }
    val percentInputs = remember(editingExpense) {
        mutableStateMapOf<String, String>().apply {
            editingExpense?.allocations?.forEach {
                put(it.memberId, "%.1f".format(it.percentageBps / 100.0))
            }
        }
    }
    val shareInputs = remember(editingExpense) {
        mutableStateMapOf<String, String>().apply {
            editingExpense?.allocations?.forEach {
                put(it.memberId, it.shares.toString())
            }
        }
    }

    // Itemized manual entries
    val manualLineItems = remember(editingExpense) {
        mutableStateListOf<ReceiptLineItem>().apply {
            if (editingExpense != null && editingExpense.lineItems.isNotEmpty()) {
                addAll(editingExpense.lineItems)
            }
        }
    }
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    var itemizedTaxText by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.taxMinorUnits?.takeIf { it > 0L }?.let { FinanceEngine.minorToMajorInput(it) } ?: ""
        )
    }
    var itemizedServiceText by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.serviceChargeMinorUnits?.takeIf { it > 0L }?.let { FinanceEngine.minorToMajorInput(it) } ?: ""
        )
    }
    var itemizedDiscountText by remember(editingExpense) {
        mutableStateOf(
            editingExpense?.discountMinorUnits?.takeIf { it > 0L }?.let { FinanceEngine.minorToMajorInput(it) } ?: ""
        )
    }
    var confirmDeleteExpense by remember { mutableStateOf(false) }

    val itemizedTaxMinor = if (splitType == SplitType.ITEMIZED) FinanceEngine.parseMajorToMinor(itemizedTaxText) else (editingExpense?.taxMinorUnits ?: 0L)
    val itemizedServiceMinor = if (splitType == SplitType.ITEMIZED) FinanceEngine.parseMajorToMinor(itemizedServiceText) else (editingExpense?.serviceChargeMinorUnits ?: 0L)
    val itemizedDiscountMinor = if (splitType == SplitType.ITEMIZED) FinanceEngine.parseMajorToMinor(itemizedDiscountText) else (editingExpense?.discountMinorUnits ?: 0L)

    val totalMinor = if (splitType == SplitType.ITEMIZED && manualLineItems.isNotEmpty()) {
        (manualLineItems.sumOf { it.totalPriceMinor } + itemizedTaxMinor + itemizedServiceMinor - itemizedDiscountMinor)
            .coerceAtLeast(0L)
    } else {
        FinanceEngine.parseMajorToMinor(amountText)
    }

    // Compute live payers & allocations + guardrails
    val resolvedPayers: List<PayerEntry> = if (!multiPayerMode) {
        if (totalMinor > 0L && singlePayerId.isNotBlank()) listOf(PayerEntry(singlePayerId, totalMinor))
        else emptyList()
    } else {
        scopeMembers.mapNotNull { m ->
            val amt = FinanceEngine.parseMajorToMinor(multiPayerInputs[m.id].orEmpty())
            if (amt > 0L) PayerEntry(m.id, amt) else null
        }
    }
    val payersSumMinor = resolvedPayers.sumOf { it.amountMinorUnits }
    val payersRemainingMinor = totalMinor - payersSumMinor

    val resolvedAllocations: List<SplitAllocation> = when (splitType) {
        SplitType.EQUAL -> FinanceEngine.distributeEqual(totalMinor, equalSelectedIds)
        SplitType.EXACT -> scopeMembers.mapNotNull { m ->
            val amt = FinanceEngine.parseMajorToMinor(exactInputs[m.id].orEmpty())
            if (amt > 0L) SplitAllocation(m.id, amt, if (totalMinor > 0L) (amt * 10_000L) / totalMinor else 0L, 1L)
            else null
        }
        SplitType.PERCENTAGE -> {
            val bpsMap = scopeMembers.associate { m ->
                val pct = percentInputs[m.id]?.toDoubleOrNull() ?: 0.0
                m.id to (pct * 100.0).roundToLong().coerceAtLeast(0L)
            }
            FinanceEngine.distributeByPercentage(totalMinor, bpsMap)
        }
        SplitType.SHARES -> {
            val sharesMap = scopeMembers.associate { m ->
                val s = shareInputs[m.id]?.toLongOrNull() ?: 0L
                m.id to s.coerceAtLeast(0L)
            }
            FinanceEngine.distributeByShares(totalMinor, sharesMap)
        }
        SplitType.ITEMIZED -> {
            FinanceEngine.computeItemizedBreakdown(
                lineItems = manualLineItems,
                taxMinor = itemizedTaxMinor,
                serviceChargeMinor = itemizedServiceMinor,
                discountMinor = itemizedDiscountMinor,
                expectedGrandTotalMinor = totalMinor
            ).allocations
        }
    }

    val allocatedMinor = resolvedAllocations.sumOf { it.amountMinorUnits }
    val allocationRemainingMinor = totalMinor - allocatedMinor
    val totalPercentBps = if (splitType == SplitType.PERCENTAGE) {
        scopeMembers.sumOf { m -> ((percentInputs[m.id]?.toDoubleOrNull() ?: 0.0) * 100.0).roundToLong() }
    } else 10_000L

    val isAllocationValid = when (splitType) {
        SplitType.EQUAL -> equalSelectedIds.isNotEmpty() && totalMinor > 0L
        SplitType.EXACT -> totalMinor > 0L && allocationRemainingMinor == 0L && resolvedAllocations.isNotEmpty()
        SplitType.PERCENTAGE -> totalMinor > 0L && totalPercentBps == 10_000L && resolvedAllocations.isNotEmpty()
        SplitType.SHARES -> totalMinor > 0L && resolvedAllocations.isNotEmpty()
        SplitType.ITEMIZED -> manualLineItems.isNotEmpty() &&
            manualLineItems.none { it.assignedMemberIds.isEmpty() } &&
            allocationRemainingMinor == 0L
    }
    val isPayersValid = totalMinor > 0L && payersRemainingMinor == 0L && resolvedPayers.isNotEmpty()
    val canSave = title.isNotBlank() && isPayersValid && isAllocationValid

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
                .padding(bottom = 32.dp)
                .testTag("smart_split_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingExpense != null) "Edit Expense Split" else "Smart Split Editor",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor")
                }
            }

            // Title & Amount + Currency
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Expense title") },
                placeholder = { Text("e.g., Sunset Yacht Charter") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_title_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (splitType != SplitType.ITEMIZED) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Total (${currency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("split_amount_input")
                    )
                } else {
                    EditorialCard(modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Itemized Total", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                            TabularCurrencyText(totalMinor, currency)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CurrencyCode.entries.forEach { c ->
                        FilterChip(
                            selected = currency == c,
                            onClick = { currency = c },
                            label = { Text(c.symbol) }
                        )
                    }
                }
            }

            // Live Multi-Currency FX Conversion Pill + Locked Per-Bill Rate Override
            if (currency != targetBaseCurrency && totalMinor > 0L) {
                val parsedCustomMicros = customFxRateText.toDoubleOrNull()
                    ?.takeIf { it > 0.0 }
                    ?.let { (it * 1_000_000.0).roundToLong().coerceAtLeast(1L) }
                val effectiveFxMicros = parsedCustomMicros ?: FinanceEngine.getFxRateMicros(currency, targetBaseCurrency)
                val convertedMinor = FinanceEngine.convertMinorUnits(
                    amountMinor = totalMinor,
                    from = currency,
                    to = targetBaseCurrency,
                    rateMicrosOverride = effectiveFxMicros
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.aiContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CurrencyExchange,
                                    contentDescription = "FX Conversion",
                                    tint = tokens.aiAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${FinanceEngine.formatMinorUnits(totalMinor, currency)} → ${FinanceEngine.formatMinorUnits(convertedMinor, targetBaseCurrency)} (@ ${FinanceEngine.formatFxRate(effectiveFxMicros)})",
                                    style = TabularSmallStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (editingFxRate) "Lock" else "Edit Rate",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.aiAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { editingFxRate = !editingFxRate }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        if (editingFxRate) {
                            OutlinedTextField(
                                value = customFxRateText,
                                onValueChange = { customFxRateText = it },
                                label = { Text("Locked per-bill rate (1 ${currency.name} = ? ${targetBaseCurrency.name})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Active Group Smart Split Memory reminder
            GroupMemoryChip(memoryNotes = selectedGroup?.splitMemoryNotes)

            // Group or 1-on-1 Personal Scope
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scope", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("1-on-1 Personal") }
                    )
                    snapshot.groups.forEach { grp ->
                        FilterChip(
                            selected = selectedGroupId == grp.id,
                            onClick = {
                                selectedGroupId = grp.id
                                currency = grp.currencyEnum
                            },
                            label = { Text(grp.name) }
                        )
                    }
                }
            }

            // Category Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }

            if (category == ExpenseCategory.FOOD && onOpenDiningStudio != null && editingExpense == null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.positiveContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, tokens.positive.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenDiningStudio() }
                        .testTag("smart_split_open_dining_studio")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DietMarkIcon(diet = DietPreference.VEG, size = 12.dp)
                                DietMarkIcon(diet = DietPreference.NON_VEG, size = 12.dp)
                                Icon(
                                    imageVector = Icons.Rounded.LocalBar,
                                    contentDescription = null,
                                    tint = tokens.aiAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "Veg / Non-Veg & Drinks Studio",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Split veg/non-veg dishes, drinks by glass & separate Bar VAT",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.positive
                        ) {
                            Text(
                                text = "Launch",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.cardSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Paid By (Single or Multiple Payers!)
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
                        Text("Paid By", style = MaterialTheme.typography.titleSmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Multiple Payers", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                            Switch(
                                checked = multiPayerMode,
                                onCheckedChange = { multiPayerMode = it },
                                modifier = Modifier.testTag("multi_payer_switch")
                            )
                        }
                    }

                    if (!multiPayerMode) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            scopeMembers.forEach { m ->
                                FilterChip(
                                    selected = singlePayerId == m.id,
                                    onClick = { singlePayerId = m.id },
                                    label = { Text(m.name.split(" ").first()) }
                                )
                            }
                        }
                    } else {
                        scopeMembers.forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = multiPayerInputs[m.id].orEmpty(),
                                    onValueChange = { multiPayerInputs[m.id] = it },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                        if (totalMinor > 0L) {
                            val ok = payersRemainingMinor == 0L
                            Text(
                                text = if (ok) "Payers balanced"
                                else "Remaining payer total: ${FinanceEngine.formatMinorUnits(payersRemainingMinor, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ok) tokens.positive else tokens.negative
                            )
                        }
                    }
                }
            }

            // Quick Direct IOU / Full-Amount Presets (solves accidental 2-way split for 1-on-1 IOUs)
            val otherScopeMembers = remember(scopeMembers, selfId) {
                scopeMembers.filter { it.id != selfId }
            }
            if (otherScopeMembers.isNotEmpty()) {
                val primaryFriend = otherScopeMembers.first()
                val friendFirst = primaryFriend.name.split(" ").first()
                val isFriendOwesFull = splitType == SplitType.EXACT &&
                    !multiPayerMode &&
                    singlePayerId == selfId &&
                    resolvedAllocations.size == 1 &&
                    resolvedAllocations.first().memberId == primaryFriend.id
                val isYouOweFull = splitType == SplitType.EXACT &&
                    !multiPayerMode &&
                    singlePayerId == primaryFriend.id &&
                    resolvedAllocations.size == 1 &&
                    resolvedAllocations.first().memberId == selfId

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quick Split Presets", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = splitType == SplitType.EQUAL && equalSelectedIds.size == scopeMembers.size,
                            onClick = {
                                splitType = SplitType.EQUAL
                                equalSelectedIds.clear()
                                equalSelectedIds.addAll(scopeMembers.map { it.id })
                            },
                            label = { Text("Split Equally") },
                            modifier = Modifier.testTag("preset_split_equally")
                        )
                        FilterChip(
                            selected = isFriendOwesFull,
                            onClick = {
                                multiPayerMode = false
                                singlePayerId = selfId
                                splitType = SplitType.EXACT
                                exactInputs.clear()
                                val fullStr = if (totalMinor > 0L) FinanceEngine.minorToMajorInput(totalMinor) else amountText
                                exactInputs[primaryFriend.id] = fullStr
                            },
                            label = { Text("$friendFirst Owes Full (100%)") },
                            modifier = Modifier.testTag("preset_friend_owes_full")
                        )
                        FilterChip(
                            selected = isYouOweFull,
                            onClick = {
                                multiPayerMode = false
                                singlePayerId = primaryFriend.id
                                splitType = SplitType.EXACT
                                exactInputs.clear()
                                val fullStr = if (totalMinor > 0L) FinanceEngine.minorToMajorInput(totalMinor) else amountText
                                exactInputs[selfId] = fullStr
                            },
                            label = { Text("You Owe $friendFirst Full") },
                            modifier = Modifier.testTag("preset_you_owe_full")
                        )
                    }
                }
            }

            // 5 Split Modes Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Split Mode", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SplitType.entries.forEach { mode ->
                        FilterChip(
                            selected = splitType == mode,
                            onClick = { splitType = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("split_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // Split Mode Workbench
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (splitType) {
                        SplitType.EQUAL -> {
                            Text("Select participants (exact penny remainder handled automatically)", style = MaterialTheme.typography.bodySmall, color = tokens.inkMuted)
                            val allocById = resolvedAllocations.associateBy { it.memberId }
                            scopeMembers.forEach { m ->
                                val checked = m.id in equalSelectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) equalSelectedIds.remove(m.id) else equalSelectedIds.add(m.id)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (it && !checked) equalSelectedIds.add(m.id)
                                                else if (!it && checked) equalSelectedIds.remove(m.id)
                                            }
                                        )
                                        Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TabularCurrencyText(
                                        minorUnits = allocById[m.id]?.amountMinorUnits ?: 0L,
                                        currency = currency,
                                        style = TabularSmallStyle
                                    )
                                }
                            }
                        }

                        SplitType.EXACT -> {
                            scopeMembers.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(m.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    OutlinedTextField(
                                        value = exactInputs[m.id].orEmpty(),
                                        onValueChange = { exactInputs[m.id] = it },
                                        placeholder = { Text("0") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                        }

                        SplitType.PERCENTAGE -> {
                            val pctProgress = (totalPercentBps / 10_000f).coerceIn(0f, 1f)
                            val remainingPct = (10_000L - totalPercentBps) / 100.0
                            val allocById = resolvedAllocations.associateBy { it.memberId }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Allocated: ${"%.1f".format(totalPercentBps / 100.0)}% / 100%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (totalPercentBps == 10_000L) tokens.positive else tokens.warning
                                    )
                                    if (remainingPct > 0.05 && scopeMembers.isNotEmpty()) {
                                        Text(
                                            text = "Auto-fill ${"%.1f".format(remainingPct)}%",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = tokens.aiAccent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val target = scopeMembers.lastOrNull {
                                                        (percentInputs[it.id]?.toDoubleOrNull() ?: 0.0) == 0.0
                                                    } ?: scopeMembers.last()
                                                    val current = percentInputs[target.id]?.toDoubleOrNull() ?: 0.0
                                                    percentInputs[target.id] = "%.1f".format(current + remainingPct)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                                .testTag("autofill_remaining_percent_button")
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { pctProgress },
                                    color = if (totalPercentBps == 10_000L) tokens.positive else tokens.aiAccent,
                                    trackColor = tokens.elevatedSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )

                                // Quick Ratio Presets
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (scopeMembers.size == 2) {
                                        listOf(50 to 50, 60 to 40, 70 to 30, 80 to 20).forEach { (a, b) ->
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    percentInputs[scopeMembers[0].id] = a.toString()
                                                    percentInputs[scopeMembers[1].id] = b.toString()
                                                },
                                                label = { Text("$a/$b") },
                                                modifier = Modifier.testTag("pct_preset_${a}_${b}")
                                            )
                                        }
                                    } else if (scopeMembers.isNotEmpty()) {
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                val base = 100.0 / scopeMembers.size
                                                var running = 0.0
                                                scopeMembers.forEachIndexed { idx, m ->
                                                    val v = if (idx == scopeMembers.lastIndex) (100.0 - running) else kotlin.math.floor(base * 10.0) / 10.0
                                                    running += v
                                                    percentInputs[m.id] = "%.1f".format(v)
                                                }
                                            },
                                            label = { Text("Even %") },
                                            modifier = Modifier.testTag("pct_preset_even")
                                        )
                                    }
                                }
                            }

                            scopeMembers.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                        if (totalMinor > 0L) {
                                            TabularCurrencyText(
                                                minorUnits = allocById[m.id]?.amountMinorUnits ?: 0L,
                                                currency = currency,
                                                style = TabularSmallStyle,
                                                color = tokens.positive
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = percentInputs[m.id].orEmpty(),
                                        onValueChange = { percentInputs[m.id] = it },
                                        placeholder = { Text("0%") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.width(110.dp)
                                    )
                                }
                            }
                        }

                        SplitType.SHARES -> {
                            val allocById = resolvedAllocations.associateBy { it.memberId }
                            scopeMembers.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                        TabularCurrencyText(
                                            minorUnits = allocById[m.id]?.amountMinorUnits ?: 0L,
                                            currency = currency,
                                            style = TabularSmallStyle,
                                            color = tokens.positive
                                        )
                                    }
                                    OutlinedTextField(
                                        value = shareInputs[m.id] ?: "1",
                                        onValueChange = { shareInputs[m.id] = it },
                                        label = { Text("Shares") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(100.dp)
                                    )
                                }
                            }
                        }

                        SplitType.ITEMIZED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newItemName,
                                    onValueChange = { newItemName = it },
                                    label = { Text("Item") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newItemPrice,
                                    onValueChange = { newItemPrice = it },
                                    label = { Text(currency.symbol) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(96.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        val priceMinor = FinanceEngine.parseMajorToMinor(newItemPrice)
                                        if (newItemName.isNotBlank() && priceMinor > 0L) {
                                            manualLineItems.add(
                                                ReceiptLineItem(
                                                    itemId = "m_${System.currentTimeMillis()}",
                                                    name = newItemName.trim(),
                                                    quantity = 1L,
                                                    unitPriceMinor = priceMinor,
                                                    totalPriceMinor = priceMinor,
                                                    assignedMemberIds = scopeMembers.map { it.id }
                                                )
                                            )
                                            newItemName = ""
                                            newItemPrice = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add line item")
                                }
                            }
                            manualLineItems.forEachIndexed { idx, item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            LineItemTagPill(
                                                tag = item.tagEnum,
                                                onClick = {
                                                    val allTags = LineItemTag.entries
                                                    val nextTag = allTags[(allTags.indexOf(item.tagEnum) + 1) % allTags.size]
                                                    manualLineItems[idx] = item.copy(
                                                        itemTag = nextTag.name,
                                                        assignedMemberIds = autoAssignMembersForTag(nextTag, scopeMembers)
                                                    )
                                                }
                                            )
                                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TabularCurrencyText(item.totalPriceMinor, currency, style = TabularSmallStyle)
                                            IconButton(onClick = { manualLineItems.removeAt(idx) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove item", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val allSelected = scopeMembers.all { it.id in item.assignedMemberIds }
                                        FilterChip(
                                            selected = allSelected,
                                            onClick = {
                                                manualLineItems[idx] = item.copy(
                                                    assignedMemberIds = if (allSelected) emptyList() else scopeMembers.map { it.id }
                                                )
                                            },
                                            label = { Text("Everyone") }
                                        )
                                        scopeMembers.forEach { m ->
                                            val sel = m.id in item.assignedMemberIds
                                            FilterChip(
                                                selected = sel,
                                                onClick = {
                                                    val next = item.assignedMemberIds.toMutableList()
                                                    if (sel) next.remove(m.id) else next.add(m.id)
                                                    manualLineItems[idx] = item.copy(assignedMemberIds = next)
                                                },
                                                label = { Text(m.name.split(" ").first()) }
                                            )
                                        }
                                    }
                                }
                            }
                            if (manualLineItems.isNotEmpty()) {
                                Text(
                                    text = "Shared Extras (Prorated by item share)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.inkMuted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = itemizedTaxText,
                                        onValueChange = { itemizedTaxText = it },
                                        label = { Text("Tax (${currency.symbol})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = itemizedServiceText,
                                        onValueChange = { itemizedServiceText = it },
                                        label = { Text("Tip/Fee") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = itemizedDiscountText,
                                        onValueChange = { itemizedDiscountText = it },
                                        label = { Text("Discount") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Live Allocation Guardrail Banner
                    val guardrailColor = if (isAllocationValid && isPayersValid) tokens.positive else tokens.warning
                    val guardrailBg = if (isAllocationValid && isPayersValid) tokens.positiveContainer else tokens.warningContainer
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = guardrailBg
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllocationValid && isPayersValid) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = guardrailColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    totalMinor <= 0L -> "Enter an amount greater than zero"
                                    !isPayersValid -> "Remaining to allocate across payers: ${FinanceEngine.formatMinorUnits(payersRemainingMinor, currency)}"
                                    splitType == SplitType.PERCENTAGE && totalPercentBps != 10_000L ->
                                        "Percentages total ${"%.1f".format(totalPercentBps / 100.0)}% — must equal 100.0%"
                                    !isAllocationValid -> "Remaining to allocate: ${FinanceEngine.formatMinorUnits(allocationRemainingMinor, currency)}"
                                    else -> "Balanced: 100% of ${FinanceEngine.formatMinorUnits(totalMinor, currency)} allocated"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = guardrailColor
                            )
                        }
                    }
                }
            }

            // Recurring Bill Schedule Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = tokens.positive,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Recurring Bill Schedule", style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurringFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = recurringFrequency == freq,
                            onClick = { recurringFrequency = freq },
                            label = { Text(freq.label) },
                            modifier = Modifier.testTag("recurring_freq_${freq.name.lowercase()}")
                        )
                    }
                }
                if (recurringFrequency != RecurringFrequency.NONE) {
                    val baseDate = editingExpense?.dateLabel?.ifBlank { todayIso } ?: todayIso
                    val nextDue = FinanceEngine.advanceDueDateIso(baseDate, recurringFrequency)
                    Text(
                        text = "Next reminder due on $nextDue",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.positive
                    )
                }
            }

            // Receipt Photo Attachment (Zero-permission PickVisualMedia)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Receipt Photo", style = MaterialTheme.typography.labelMedium)
                    if (receiptPhotoPath != null) {
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.negative,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { receiptPhotoPath = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                val resolvedThumb = remember(receiptPhotoPath) {
                    val file = ReceiptPhotoStore.resolveFile(context, receiptPhotoPath)
                    if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                if (resolvedThumb != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        color = tokens.cardSurface
                    ) {
                        Image(
                            bitmap = resolvedThumb.asImageBitmap(),
                            contentDescription = "Attached receipt photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            receiptPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("attach_receipt_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Attach Receipt Photo")
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Live Anomaly Guard (Duplicate & Outlier Detection)
            val liveAnomalies = remember(title, totalMinor, currency, selectedGroupId, snapshot.expenses) {
                FinanceEngine.detectExpenseAnomalies(
                    candidateTitle = title,
                    candidateMinor = totalMinor,
                    candidateCurrency = currency,
                    candidateDate = editingExpense?.dateLabel?.ifBlank { todayIso } ?: todayIso,
                    candidateTaxMinor = editingExpense?.taxMinorUnits ?: 0L,
                    candidateServiceMinor = editingExpense?.serviceChargeMinorUnits ?: 0L,
                    existingExpenses = snapshot.expenses,
                    groupId = selectedGroupId,
                    editingExpenseId = editingExpense?.id
                )
            }
            AnomalyGuardBanner(anomalies = liveAnomalies)

            Button(
                onClick = {
                    val effectiveDate = editingExpense?.dateLabel?.ifBlank { todayIso } ?: todayIso
                    val nextDue = if (recurringFrequency != RecurringFrequency.NONE) {
                        editingExpense?.nextDueDateLabel?.takeIf { editingExpense.recurringEnum == recurringFrequency }
                            ?: FinanceEngine.advanceDueDateIso(effectiveDate, recurringFrequency)
                    } else null
                    val fxMicros = if (currency != targetBaseCurrency) {
                        customFxRateText.toDoubleOrNull()
                            ?.takeIf { it > 0.0 }
                            ?.let { (it * 1_000_000.0).roundToLong().coerceAtLeast(1L) }
                            ?: FinanceEngine.getFxRateMicros(currency, targetBaseCurrency)
                    } else null
                    val record = ExpenseRecord(
                        id = editingExpense?.id.orEmpty(),
                        groupId = selectedGroupId,
                        title = title.trim(),
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        category = category.name,
                        currency = currency.name,
                        totalMinorUnits = totalMinor,
                        splitType = splitType.name,
                        dateLabel = effectiveDate,
                        shareCode = editingExpense?.shareCode?.ifBlank { "sf-${UUID.randomUUID().toString().take(8)}" }
                            ?: "sf-${UUID.randomUUID().toString().take(8)}",
                        aiExtracted = editingExpense?.aiExtracted ?: false,
                        aiConfidenceNote = editingExpense?.aiConfidenceNote,
                        settled = editingExpense?.settled ?: false,
                        payers = resolvedPayers,
                        allocations = resolvedAllocations,
                        lineItems = if (splitType == SplitType.ITEMIZED) manualLineItems.toList() else emptyList(),
                        taxMinorUnits = itemizedTaxMinor,
                        serviceChargeMinorUnits = itemizedServiceMinor,
                        discountMinorUnits = itemizedDiscountMinor,
                        recurringFrequency = recurringFrequency.name,
                        nextDueDateLabel = nextDue,
                        receiptPhotoPath = receiptPhotoPath,
                        fxRateToBaseMicros = fxMicros
                    )
                    onSaveExpense(record)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_smart_split_button")
            ) {
                Text(if (editingExpense != null) "Update Split" else "Save Split")
            }

            if (editingExpense != null && onDeleteExpense != null) {
                OutlinedButton(
                    onClick = { confirmDeleteExpense = true },
                    border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.negative),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("delete_expense_in_sheet_button")
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Expense")
                }
            }
        }
    }

    if (confirmDeleteExpense && editingExpense != null && onDeleteExpense != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteExpense = false },
            title = { Text("Delete Expense?") },
            text = { Text("Delete \"${editingExpense.title}\"? You can restore it immediately via Undo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteExpense = false
                        onDeleteExpense(editingExpense)
                        onDismiss()
                    }
                ) {
                    Text("Delete", color = tokens.negative, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteExpense = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
