package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ReceiptPhotoStore
import com.example.data.UpiQrStore
import com.example.engine.FinanceEngine
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyUpiQrSheet(
    snapshot: LedgerSnapshot,
    customUpiQrPath: String?,
    onCustomUpiQrChanged: (String?) -> Unit,
    onOpenProfileToEditVpa: () -> Unit,
    onFeedback: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val displayName = snapshot.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: snapshot.selfMember?.name
        ?: "SplitFlow Member"
    val upiVpa = snapshot.profile?.upiVpa?.takeIf { it.isNotBlank() }
        ?: snapshot.selfMember?.upiVpa?.takeIf { it.isNotBlank() }
        ?: ""

    val customQrBitmap = remember(customUpiQrPath) {
        val file = ReceiptPhotoStore.resolveFile(context, customUpiQrPath)
        if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    var showUploadedQr by remember(customQrBitmap != null) { mutableStateOf(customQrBitmap != null) }

    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = UpiQrStore.saveCustomUpiQr(context, uri)
            if (savedPath != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onCustomUpiQrChanged(savedPath)
                showUploadedQr = true
                onFeedback("Personal UPI QR saved! Friends can scan it anytime.")
            } else {
                onFeedback("Could not load that image — please pick a clear QR screenshot.")
            }
        }
    }

    val fallbackUpiPayload = remember(upiVpa, displayName, snapshot.defaultCurrency) {
        FinanceEngine.buildUpiDeepLink(
            payeeVpa = upiVpa.ifBlank { "splitflow@okaxis" },
            payeeName = displayName,
            amountMinorUnits = 0L,
            currency = snapshot.defaultCurrency,
            note = "SplitFlow Payment"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("my_upi_qr_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = tokens.onAcidLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "My UPI QR Code",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Let friends scan & settle instantly",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_my_upi_qr_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close My UPI QR sheet",
                        tint = tokens.inkMuted
                    )
                }
            }

            // Editorial Gradient QR Showcase Card
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, tokens.heroBorder),
                shadowElevation = if (tokens.isDark) 0.dp else 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.heroGradientStart,
                                    tokens.heroGradientMid,
                                    tokens.heroGradientEnd
                                )
                            )
                        )
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(tokens.heroAccentGlow, Color.Transparent),
                                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                                    radius = size.width * 0.65f
                                ),
                                radius = size.width * 0.65f,
                                center = Offset(size.width * 0.88f, size.height * 0.12f)
                            )
                        }
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Identity + Active Mode Badge
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
                            MemberAvatar(
                                member = snapshot.selfMember,
                                size = 38.dp,
                                selected = true,
                                showGuestDot = false
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.onHero,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = upiVpa.ifBlank { "No UPI ID set yet" },
                                    style = TabularSmallStyle,
                                    color = tokens.onHero.copy(alpha = 0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (customQrBitmap != null && showUploadedQr) tokens.acidLime else tokens.heroDivider
                        ) {
                            Text(
                                text = if (customQrBitmap != null && showUploadedQr) "MY QR" else "AUTO VPA",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (customQrBitmap != null && showUploadedQr) tokens.onAcidLime else tokens.onHero,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Toggle pill when user has uploaded their own UPI QR image
                    if (customQrBitmap != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf(true to "Uploaded QR", false to "Auto VPA QR")
                            tabs.forEach { (uploadedTab, title) ->
                                val selected = showUploadedQr == uploadedTab
                                Surface(
                                    onClick = { showUploadedQr = uploadedTab },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) tokens.acidLime else Color.Transparent
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) tokens.onAcidLime else tokens.onHero.copy(alpha = 0.72f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Crisp White Scannable QR Frame
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .size(236.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (customQrBitmap != null && showUploadedQr) {
                                Image(
                                    bitmap = customQrBitmap.asImageBitmap(),
                                    contentDescription = "Your uploaded personal UPI QR code",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(212.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                UpiQrCanvas(
                                    payload = fallbackUpiPayload,
                                    modifier = Modifier.size(196.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (customQrBitmap != null && showUploadedQr) {
                            "Scan with GPay, PhonePe, Paytm, or any BHIM UPI app"
                        } else {
                            "Upload your GPay / PhonePe / Paytm QR screenshot below for 1-tap scanning"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.onHero.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Primary Acid-Lime Gradient CTA: Upload / Replace Personal UPI QR Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                        )
                    )
                    .clickable {
                        qrPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("upload_my_upi_qr_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = tokens.onAcidLime,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (customQrBitmap != null) "Replace UPI QR Image" else "Add Your UPI QR Image",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onAcidLime
                    )
                }
            }

            // Utility Actions: Copy UPI ID + Share UPI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (upiVpa.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("UPI ID", upiVpa))
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFeedback("Copied UPI ID: $upiVpa")
                        } else {
                            onOpenProfileToEditVpa()
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, tokens.hairline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_my_upi_id_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (upiVpa.isNotBlank()) "Copy UPI ID" else "Set UPI ID",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            append("Pay $displayName via UPI")
                            if (upiVpa.isNotBlank()) {
                                append("\nUPI ID: $upiVpa")
                                append("\nQuick Pay: $fallbackUpiPayload")
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "UPI Payment Details - $displayName")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share UPI details"))
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, tokens.hairline),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_my_upi_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share UPI", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Secondary Footer Row: Edit UPI VPA & Remove Uploaded QR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenProfileToEditVpa,
                    modifier = Modifier.testTag("edit_vpa_from_qr_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit UPI VPA in Profile")
                }

                if (customQrBitmap != null) {
                    TextButton(
                        onClick = {
                            UpiQrStore.clearCustomUpiQr(context)
                            onCustomUpiQrChanged(null)
                            showUploadedQr = false
                            onFeedback("Removed custom UPI QR image.")
                        },
                        modifier = Modifier.testTag("remove_my_upi_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = tokens.negative,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Remove QR", color = tokens.negative)
                    }
                }
            }
        }
    }
}
