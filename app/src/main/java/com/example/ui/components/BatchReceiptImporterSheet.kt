package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ReceiptPhotoStore
import com.example.engine.FinanceEngine
import com.example.model.BatchItemStatus
import com.example.model.BatchReceiptItem
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchReceiptImporterSheet(
    snapshot: LedgerSnapshot,
    batchItems: List<BatchReceiptItem>,
    initialGroupId: String?,
    onStartBatch: (List<Pair<String, Bitmap>>, Context) -> Unit,
    onApproveItem: (itemId: String, groupId: String?, paidByMemberId: String) -> Unit,
    onSkipItem: (itemId: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedGroupId by remember { mutableStateOf(initialGroupId ?: snapshot.groups.firstOrNull()?.id) }
    var paidByMemberId by remember {
        mutableStateOf(snapshot.selfMember?.id ?: snapshot.members.firstOrNull()?.id.orEmpty())
    }

    val scopeMembers = remember(selectedGroupId, snapshot.groups, snapshot.members) {
        val grp = snapshot.groups.firstOrNull { it.id == selectedGroupId }
        if (grp != null) {
            val ids = grp.memberIds.toSet()
            snapshot.members.filter { it.id in ids }.ifEmpty { snapshot.members }
        } else {
            snapshot.members
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val decoded = uris.mapIndexedNotNull { idx, uri ->
                val bmp = ReceiptPhotoStore.decodeUri(context, uri)
                if (bmp != null) "Receipt #${idx + 1}" to bmp else null
            }
            if (decoded.isNotEmpty()) {
                onStartBatch(decoded, context)
            }
        }
    }

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
                .testTag("batch_receipt_importer_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DynamicFeed,
                        contentDescription = null,
                        tint = tokens.aiAccent
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Batch Trip Importer", style = MaterialTheme.typography.titleLarge)
                            AiBadge("PARALLEL")
                        }
                        Text(
                            text = "Drop up to 8 trip receipts — extract in parallel & approve fast",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close batch importer")
                }
            }

            // Pick Multi-Photos via zero-permission Android Photo Picker
            Button(
                onClick = {
                    multiPhotoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.acidLime,
                    contentColor = tokens.onAcidLime
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("batch_pick_photos_button")
            ) {
                Icon(Icons.Rounded.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select Receipts from Gallery (up to 8)")
            }

            // Target Group & Default Payer for Approval Queue
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Import Target Group", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("1-on-1 Personal") }
                    )
                    snapshot.groups.forEach { grp ->
                        FilterChip(
                            selected = selectedGroupId == grp.id,
                            onClick = { selectedGroupId = grp.id },
                            label = { Text(grp.name) }
                        )
                    }
                }

                Text("Default Payer", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scopeMembers.forEach { m ->
                        FilterChip(
                            selected = paidByMemberId == m.id,
                            onClick = { paidByMemberId = m.id },
                            label = { Text(m.name.split(" ").first()) }
                        )
                    }
                }
            }

            if (batchItems.isEmpty()) {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Zero-friction multi-bill queue", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Select 2–8 taxi, cafe, restaurant, or hotel bills from your photo library to scan them concurrently on-device with Cactus Mirage OCR and approve each entry in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val readyCount = batchItems.count { it.status == BatchItemStatus.READY }
                val approvedCount = batchItems.count { it.status == BatchItemStatus.APPROVED }
                val scanningCount = batchItems.count { it.status == BatchItemStatus.SCANNING }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queue: $approvedCount approved • $readyCount ready${if (scanningCount > 0) " • $scanningCount scanning" else ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.positive
                    )
                    if (readyCount > 1) {
                        OutlinedButton(
                            onClick = {
                                batchItems.filter { it.status == BatchItemStatus.READY }.forEach { item ->
                                    onApproveItem(item.id, selectedGroupId, paidByMemberId)
                                }
                            },
                            modifier = Modifier.testTag("batch_approve_all_button")
                        ) {
                            Text("Approve All ($readyCount)")
                        }
                    }
                }

                batchItems.forEach { item ->
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (item.status) {
                                BatchItemStatus.QUEUED, BatchItemStatus.SCANNING -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = tokens.aiAccent
                                        )
                                        Column {
                                            Text(item.label, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "Local Mirage OCR extracting line items, GST & total...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.inkMuted
                                            )
                                        }
                                    }
                                }

                                BatchItemStatus.READY -> {
                                    val parsed = item.parsed
                                    if (parsed != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(parsed.merchantName, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${parsed.date} • ${parsed.category.label} • ${parsed.lineItems.size} items",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TabularCurrencyText(
                                                minorUnits = parsed.grandTotalMinor,
                                                currency = parsed.currency,
                                                style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }

                                        val anomalies = FinanceEngine.detectExpenseAnomalies(
                                            candidateTitle = parsed.merchantName,
                                            candidateMinor = parsed.grandTotalMinor,
                                            candidateCurrency = parsed.currency,
                                            candidateDate = parsed.date,
                                            candidateTaxMinor = parsed.taxMinor,
                                            candidateServiceMinor = parsed.serviceChargeMinor,
                                            existingExpenses = snapshot.expenses,
                                            groupId = selectedGroupId
                                        )
                                        AnomalyGuardBanner(anomalies = anomalies)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onSkipItem(item.id) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Skip")
                                            }
                                            Button(
                                                onClick = { onApproveItem(item.id, selectedGroupId, paidByMemberId) },
                                                colors = ButtonDefaults.buttonColors(containerColor = tokens.positive),
                                                modifier = Modifier.weight(1.4f)
                                            ) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Approve & Save")
                                            }
                                        }
                                    }
                                }

                                BatchItemStatus.APPROVED -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = tokens.positive, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = "${item.parsed?.merchantName ?: item.label} • Saved",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = tokens.positive
                                            )
                                        }
                                        item.parsed?.let { p ->
                                            TabularCurrencyText(p.grandTotalMinor, p.currency, style = TabularSmallStyle, color = tokens.positive)
                                        }
                                    }
                                }

                                BatchItemStatus.SKIPPED -> {
                                    Text(
                                        text = "${item.parsed?.merchantName ?: item.label} — Skipped",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkMuted
                                    )
                                }

                                BatchItemStatus.ERROR -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = tokens.negativeContainer,
                                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.3f))
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(item.label, style = MaterialTheme.typography.titleSmall, color = tokens.negative)
                                            Text(
                                                text = item.errorMessage ?: "Extraction failed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = tokens.negative
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Queue")
                }
            }
        }
    }
}


