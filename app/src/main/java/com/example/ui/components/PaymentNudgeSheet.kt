package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.DebtTransfer
import com.example.model.LedgerMember
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularHeroStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentNudgeSheet(
    transfer: DebtTransfer,
    fromMember: LedgerMember?,
    toMember: LedgerMember?,
    groupName: String?,
    currency: CurrencyCode,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fromName = fromMember?.name ?: "Friend"
    val toName = toMember?.name ?: "Member"

    var nudgeText by remember(transfer, groupName, toMember) {
        mutableStateOf(
            FinanceEngine.buildPaymentNudgeMessage(
                debtorName = fromName,
                creditorName = toName,
                amountMinor = transfer.amountMinorUnits,
                currency = currency,
                groupName = groupName,
                upiVpa = toMember?.upiVpa
            )
        )
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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.positiveContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Payment Nudge",
                            tint = tokens.positive,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Send Payment Nudge",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "One-tap WhatsApp or SMS reminder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_nudge_sheet")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Summary strip
            HairlineSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MemberAvatar(member = fromMember, size = 40.dp)
                        Column {
                            Text(
                                text = "$fromName → $toName",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = groupName ?: "Personal balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TabularCurrencyText(
                        minorUnits = transfer.amountMinorUnits,
                        currency = currency,
                        color = tokens.positive,
                        style = TabularHeroStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize)
                    )
                }
            }

            OutlinedTextField(
                value = nudgeText,
                onValueChange = { nudgeText = it },
                label = { Text("Reminder message") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nudge_message_input"),
                shape = RoundedCornerShape(14.dp)
            )

            // Primary WhatsApp CTA
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val encoded = Uri.encode(nudgeText)
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
                    try {
                        context.startActivity(waIntent)
                        onFeedback("Opened WhatsApp reminder for $fromName")
                    } catch (_: Exception) {
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, nudgeText)
                        }
                        context.startActivity(Intent.createChooser(fallback, "Send payment nudge"))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("nudge_whatsapp_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.positive,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send via WhatsApp", fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                            putExtra("sms_body", nudgeText)
                        }
                        try {
                            context.startActivity(smsIntent)
                            onFeedback("Opened SMS composer")
                        } catch (_: Exception) {
                            onFeedback("No SMS app available on device")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("nudge_sms_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, tokens.hairline)
                ) {
                    Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SMS")
                }

                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("SplitFlow Nudge", nudgeText))
                        onFeedback("Copied reminder to clipboard")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("nudge_copy_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, tokens.hairline)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, nudgeText)
                        }
                        context.startActivity(Intent.createChooser(share, "Share payment nudge"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("nudge_share_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, tokens.hairline)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}
