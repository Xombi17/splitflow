package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ReceiptPhotoStore
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.ExpenseRecord
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularHeroStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptViewerSheet(
    expense: ExpenseRecord,
    defaultCurrency: CurrencyCode,
    onInspectSplit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val receiptBitmap = remember(expense.receiptPhotoPath) {
        val file = ReceiptPhotoStore.resolveFile(context, expense.receiptPhotoPath)
        if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${expense.dateLabel} • ${expense.categoryEnum.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_receipt_viewer")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close receipt viewer")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tokens.cardSurface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (receiptBitmap != null) {
                    Image(
                        bitmap = receiptBitmap.asImageBitmap(),
                        contentDescription = "Receipt photo for ${expense.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale
                            )
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = tokens.inkMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Receipt image not found on device",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL RECEIPT",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                    TabularCurrencyText(
                        minorUnits = expense.totalMinorUnits,
                        currency = expense.currencyEnum,
                        style = TabularHeroStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    )
                    if (expense.currencyEnum != defaultCurrency) {
                        val converted = FinanceEngine.convertMinorUnits(
                            expense.totalMinorUnits,
                            expense.currencyEnum,
                            defaultCurrency
                        )
                        Text(
                            text = "≈ ${FinanceEngine.formatMinorUnits(converted, defaultCurrency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(
                    onClick = {
                        onDismiss()
                        onInspectSplit()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("receipt_inspect_split_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Inspect Split")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoProofViewerSheet(
    title: String,
    subtitle: String,
    photoPath: String?,
    amountMinor: Long,
    currency: CurrencyCode,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var zoomScale by remember { mutableFloatStateOf(1f) }

    val proofBitmap = remember(photoPath) {
        val file = ReceiptPhotoStore.resolveFile(context, photoPath)
        if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("photo_proof_viewer_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close proof viewer")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tokens.cardSurface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (proofBitmap != null) {
                    Image(
                        bitmap = proofBitmap.asImageBitmap(),
                        contentDescription = "Settlement payment proof screenshot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                    )
                } else {
                    Text(
                        text = "Payment proof image not available on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SETTLED AMOUNT", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    TabularCurrencyText(
                        minorUnits = amountMinor,
                        currency = currency,
                        color = tokens.positive,
                        style = TabularHeroStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    )
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

