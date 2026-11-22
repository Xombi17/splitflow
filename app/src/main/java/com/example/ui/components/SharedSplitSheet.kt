package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.ExpenseRecord
import com.example.model.LedgerMember
import com.example.ui.screens.ClaimGuestProfileDialog
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SharedSplitSheet(
    expense: ExpenseRecord,
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    onToggleLineItemClaim: (itemId: String, memberId: String) -> Unit,
    onClaimGuestProfile: (memberId: String, name: String, email: String, upiVpa: String?) -> Unit,
    onEditExpense: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }

    val participants = remember(expense, snapshot.members) {
        val ids = (expense.allocations.map { it.memberId } + expense.payers.map { it.memberId }).distinct()
        ids.mapNotNull { memberMap[it] }
    }
    var activeViewingMemberId by remember(expense, participants, isGuestMode) {
        mutableStateOf(
            (if (isGuestMode) participants.firstOrNull { it.guestProfile }?.id else null)
                ?: snapshot.selfMember?.id
                ?: participants.firstOrNull()?.id.orEmpty()
        )
    }
    var guestToClaim by remember { mutableStateOf<LedgerMember?>(null) }

    val shareUrl = "https://splitflow.app/#/share/${expense.shareCode}"
    val viewingMember = memberMap[activeViewingMemberId]
    val viewingAllocation = expense.allocations.firstOrNull { it.memberId == activeViewingMemberId }

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
                .testTag("shared_split_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Shared Split Preview", style = MaterialTheme.typography.titleLarge)
                        if (expense.aiExtracted) AiBadge("AI")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = tokens.positive, modifier = Modifier.size(14.dp))
                        Text(shareUrl, style = MaterialTheme.typography.labelSmall, color = tokens.positive)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close shared split view")
                }
            }

            // Summary Header Card
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${expense.categoryEnum.label} · ${expense.splitTypeEnum.label} · ${expense.dateLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TabularCurrencyText(
                            minorUnits = expense.totalMinorUnits,
                            currency = expense.currencyEnum,
                            style = TabularAmountStyle
                        )
                    }
                    if (!expense.notes.isNullOrBlank()) {
                        Text(
                            text = expense.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!expense.aiConfidenceNote.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = tokens.aiContainer.copy(alpha = 0.5f)) {
                            Text(
                                text = expense.aiConfidenceNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.aiAccent,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // "Inspect As" Guest / Member Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preview Link As Member / Guest", style = MaterialTheme.typography.labelMedium, color = tokens.inkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    participants.forEach { member ->
                        val selected = member.id == activeViewingMemberId
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) tokens.positiveContainer else tokens.elevatedSurface,
                            border = BorderStroke(1.dp, if (selected) tokens.positive else tokens.hairline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { activeViewingMemberId = member.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MemberAvatar(member, size = 22.dp, selected = selected, showGuestDot = false)
                                Text(
                                    text = member.name.split(" ").first() + if (isGuestMode && member.guestProfile) " (Guest)" else "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) tokens.positive else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Viewing Member's Exact Share
            viewingMember?.let { viewer ->
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MemberAvatar(viewer, size = 34.dp, showGuestDot = isGuestMode)
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(viewer.name, style = MaterialTheme.typography.titleSmall)
                                        if (isGuestMode) {
                                            GuestBadge(viewer.guestProfile)
                                        }
                                    }
                                    Text(
                                        "Share of this bill",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.inkMuted
                                    )
                                }
                            }
                            TabularCurrencyText(
                                minorUnits = viewingAllocation?.amountMinorUnits ?: 0L,
                                currency = expense.currencyEnum,
                                style = TabularAmountStyle,
                                color = tokens.negative
                            )
                        }

                        if (isGuestMode && viewer.guestProfile) {
                            Button(
                                onClick = { guestToClaim = viewer },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("shared_view_claim_profile_button")
                            ) {
                                Icon(Icons.Rounded.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Claim This Profile (${viewer.name})")
                            }
                        }
                    }
                }
            }

            // Interactive Itemized Line Items (tap to claim/unclaim live!)
            if (expense.lineItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Itemized Receipt — Tap to Claim as ${viewingMember?.name?.split(" ")?.firstOrNull() ?: "Member"}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    expense.lineItems.forEach { item ->
                        val claimedByViewer = activeViewingMemberId in item.assignedMemberIds
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (activeViewingMemberId.isNotBlank()) {
                                    onToggleLineItemClaim(item.itemId, activeViewingMemberId)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${item.quantity}x ${item.name}", style = MaterialTheme.typography.titleSmall)
                                    val claimantsLabel = item.assignedMemberIds.mapNotNull {
                                        memberMap[it]?.name?.split(" ")?.firstOrNull()
                                    }.joinToString(", ").ifBlank { "Unassigned — tap to claim" }
                                    Text(
                                        text = claimantsLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (claimedByViewer) tokens.positive else tokens.inkMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    TabularCurrencyText(item.totalPriceMinor, expense.currencyEnum, style = TabularSmallStyle)
                                    Text(
                                        text = if (claimedByViewer) "Claimed ✓" else "Tap to claim",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (claimedByViewer) tokens.positive else tokens.aiAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Full Breakdown
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Split Breakdown", style = MaterialTheme.typography.titleSmall)
                    expense.allocations.forEach { alloc ->
                        val m = memberMap[alloc.memberId]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m?.name ?: "Member", style = MaterialTheme.typography.bodySmall)
                            TabularCurrencyText(alloc.amountMinorUnits, expense.currencyEnum, style = TabularSmallStyle)
                        }
                    }
                }
            }

            // Bottom Share & Edit Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onEditExpense(expense) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit Split")
                }
                Button(
                    onClick = {
                        val text = buildString {
                            appendLine("SplitFlow Bill: ${expense.title}")
                            appendLine("Total: ${FinanceEngine.formatMinorUnits(expense.totalMinorUnits, expense.currencyEnum)}")
                            appendLine("Inspect & claim your items: $shareUrl")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Split Link"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_split_link_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Link")
                }
            }
        }
    }

    guestToClaim?.let { guest ->
        ClaimGuestProfileDialog(
            guest = guest,
            sharedExpenseCount = snapshot.expenses.count { exp ->
                exp.payers.any { it.memberId == guest.id } || exp.allocations.any { it.memberId == guest.id }
            },
            onDismiss = { guestToClaim = null },
            onConfirmClaim = { name, email, upi ->
                onClaimGuestProfile(guest.id, name, email, upi)
                guestToClaim = null
            }
        )
    }
}
