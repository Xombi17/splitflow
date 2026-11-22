package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.ReceiptPhotoStore
import com.example.data.UpiQrStore
import com.example.engine.FinanceEngine
import com.example.model.DebtTransfer
import com.example.model.PaymentMethod
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettleUpSheet(
    transfer: DebtTransfer,
    snapshot: LedgerSnapshot,
    customUpiQrPath: String? = null,
    onCustomUpiQrChanged: (String?) -> Unit = {},
    onConfirmSettlement: (
        fromMemberId: String,
        toMemberId: String,
        amountMinorUnits: Long,
        paymentMethod: PaymentMethod,
        note: String?,
        paymentProofPath: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }

    val fromMember = memberMap[transfer.fromMemberId]
    val toMember = memberMap[transfer.toMemberId]
    val isSelfPayee = toMember?.selfProfile == true
    val payeeVpa = toMember?.upiVpa?.takeIf { it.isNotBlank() }
        ?: if (isSelfPayee) snapshot.profile?.upiVpa.orEmpty() else ""

    val customUpiQrBitmap = remember(customUpiQrPath) {
        val file = ReceiptPhotoStore.resolveFile(context, customUpiQrPath)
        if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    var useUploadedPersonalQr by remember(isSelfPayee, customUpiQrBitmap != null) {
        mutableStateOf(isSelfPayee && customUpiQrBitmap != null)
    }

    var amountInput by remember(transfer) {
        mutableStateOf(FinanceEngine.minorToMajorInput(transfer.amountMinorUnits))
    }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var note by remember { mutableStateOf("SplitFlow Settlement") }
    var paymentProofPath by remember(transfer) { mutableStateOf<String?>(null) }
    var upiLaunchFeedback by remember { mutableStateOf<String?>(null) }

    val proofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = ReceiptPhotoStore.saveFromUri(context, uri)
            if (saved != null) paymentProofPath = saved
        }
    }

    val personalQrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = UpiQrStore.saveCustomUpiQr(context, uri)
            if (savedPath != null) {
                onCustomUpiQrChanged(savedPath)
                useUploadedPersonalQr = true
            }
        }
    }

    val enteredMinor = FinanceEngine.parseMajorToMinor(amountInput)
    val remainingAfterMinor = (transfer.amountMinorUnits - enteredMinor).coerceAtLeast(0L)
    val isPartial = enteredMinor in 1L until transfer.amountMinorUnits

    val upiUri = remember(payeeVpa, toMember?.name, enteredMinor, note) {
        FinanceEngine.buildUpiDeepLink(
            payeeVpa = payeeVpa.ifBlank { "splitflow@okaxis" },
            payeeName = toMember?.name ?: "Member",
            amountMinorUnits = enteredMinor.coerceAtLeast(100L),
            currency = transfer.currency,
            note = note.ifBlank { "SplitFlow Settlement" }
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
                .padding(bottom = 32.dp)
                .testTag("settle_up_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settle Up", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close settle up")
                }
            }

            // Payer -> Payee Banner with subtle editorial gradient
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                            )
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MemberAvatar(fromMember, size = 44.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(fromMember?.name?.split(" ")?.firstOrNull() ?: "Payer", style = MaterialTheme.typography.titleSmall)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "pays", tint = tokens.positive)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MemberAvatar(toMember, size = 44.dp, selected = true)
                        Spacer(Modifier.height(6.dp))
                        Text(toMember?.name?.split(" ")?.firstOrNull() ?: "Payee", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            // Partial & Milestone Quick Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val q25 = (transfer.amountMinorUnits / 4L).coerceAtLeast(100L)
                    val q50 = (transfer.amountMinorUnits / 2L).coerceAtLeast(100L)
                    FilterChip(
                        selected = enteredMinor == q25,
                        onClick = {
                            amountInput = FinanceEngine.minorToMajorInput(q25)
                            note = "25% milestone settlement"
                        },
                        label = { Text("25% Milestone") },
                        modifier = Modifier.testTag("settle_milestone_25")
                    )
                    FilterChip(
                        selected = enteredMinor == q50,
                        onClick = {
                            amountInput = FinanceEngine.minorToMajorInput(q50)
                            note = "50% milestone settlement"
                        },
                        label = { Text("50% Half") },
                        modifier = Modifier.testTag("settle_milestone_50")
                    )
                    FilterChip(
                        selected = enteredMinor == transfer.amountMinorUnits,
                        onClick = {
                            amountInput = FinanceEngine.minorToMajorInput(transfer.amountMinorUnits)
                            note = "Full settlement"
                        },
                        label = { Text("Full Balance") },
                        modifier = Modifier.testTag("settle_milestone_full")
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Settlement Amount (${transfer.currency.symbol}) — Full or Partial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settle_amount_input")
                )

                if (isPartial) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.warningContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Partial milestone — remaining balance",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.warning
                            )
                            TabularCurrencyText(
                                minorUnits = remainingAfterMinor,
                                currency = transfer.currency,
                                style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                                color = tokens.warning
                            )
                        }
                    }
                }
            }

            // Payment Method Chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.label) }
                    )
                }
            }

            // UPI QR & Deep-Link Card (supports user's own uploaded UPI QR when they are the payee)
            if (paymentMethod == PaymentMethod.UPI) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, tokens.heroBorder),
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
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = tokens.acidLime, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (isSelfPayee && customUpiQrBitmap != null && useUploadedPersonalQr) {
                                    "Your Uploaded UPI QR"
                                } else {
                                    "UPI Instant Pay QR"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.onHero
                            )
                        }

                        if (isSelfPayee && customUpiQrBitmap != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(true to "My Uploaded QR", false to "Dynamic Amount QR").forEach { (isUploaded, title) ->
                                    val selected = useUploadedPersonalQr == isUploaded
                                    Surface(
                                        onClick = { useUploadedPersonalQr = isUploaded },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (selected) tokens.acidLime else Color.Transparent
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (selected) tokens.onAcidLime else tokens.onHero.copy(alpha = 0.72f),
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isSelfPayee && customUpiQrBitmap != null && useUploadedPersonalQr) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White
                            ) {
                                Image(
                                    bitmap = customUpiQrBitmap.asImageBitmap(),
                                    contentDescription = "Your uploaded personal UPI QR code",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(196.dp)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        } else {
                            UpiQrCanvas(payload = upiUri)
                        }

                        Text(
                            text = "VPA: ${payeeVpa.ifBlank { "Set VPA in Profile Settings" }}",
                            style = TabularSmallStyle,
                            color = tokens.onHero.copy(alpha = 0.74f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    runCatching {
                                        context.startActivity(intent)
                                    }.onFailure {
                                        upiLaunchFeedback = "No UPI app installed on this device — log settlement below once paid."
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                modifier = Modifier.testTag("launch_upi_app_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = tokens.onHero, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "Open UPI App",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.onHero
                                    )
                                }
                            }

                            if (isSelfPayee) {
                                Surface(
                                    onClick = {
                                        personalQrPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.acidLime,
                                    modifier = Modifier.testTag("settle_upload_my_upi_qr_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = tokens.onAcidLime, modifier = Modifier.size(15.dp))
                                        Text(
                                            text = if (customUpiQrBitmap != null) "Replace My QR" else "Add My UPI QR",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = tokens.onAcidLime
                                        )
                                    }
                                }
                            }
                        }

                        upiLaunchFeedback?.let { msg ->
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = tokens.warning)
                        }
                    }
                }
            }

            // Payment Proof Screenshot Attachment (Zero-permission PickVisualMedia)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Proof Screenshot (Optional)", style = MaterialTheme.typography.labelMedium)
                    if (paymentProofPath != null) {
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.negative,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { paymentProofPath = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                val proofThumb = remember(paymentProofPath) {
                    val file = ReceiptPhotoStore.resolveFile(context, paymentProofPath)
                    if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                if (proofThumb != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tokens.positive.copy(alpha = 0.45f)),
                        color = tokens.cardSurface
                    ) {
                        Image(
                            bitmap = proofThumb.asImageBitmap(),
                            contentDescription = "Attached UPI or bank transfer proof",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            proofPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("attach_payment_proof_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Attach UPI / Bank Screenshot")
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Reference / Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val canConfirm = enteredMinor > 0L
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (canConfirm) {
                            Brush.horizontalGradient(
                                colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(tokens.elevatedSurface, tokens.elevatedSurface)
                            )
                        }
                    )
                    .clickable(enabled = canConfirm) {
                        onConfirmSettlement(
                            transfer.fromMemberId,
                            transfer.toMemberId,
                            enteredMinor,
                            paymentMethod,
                            note.trim().takeIf { it.isNotEmpty() },
                            paymentProofPath
                        )
                    }
                    .testTag("confirm_settlement_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (canConfirm) tokens.onAcidLime else tokens.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    val label = if (isPartial) "Log Partial Settlement" else "Log Settlement"
                    Text(
                        text = "$label (${FinanceEngine.formatMinorUnits(enteredMinor, transfer.currency)})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (canConfirm) tokens.onAcidLime else tokens.inkMuted
                    )
                }
            }
        }
    }
}

