package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.ai.RoutingStrategy
import com.example.ai.SplitFlowLocale
import com.example.ai.SplitFlowVoicePersona
import com.example.ai.VoiceSettingsState
import com.example.data.ReceiptPhotoStore
import com.example.data.UpiQrStore
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.ui.components.EditorialCard
import com.example.ui.components.HairlineSection
import com.example.ui.components.MemberAvatar
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    isDarkMode: Boolean,
    isGoogleSigningIn: Boolean,
    isPrivacyBlurEnabled: Boolean,
    customUpiQrPath: String?,
    onCustomUpiQrChanged: (String?) -> Unit,
    onOpenMyUpiQr: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onTogglePrivacyBlur: () -> Unit,
    onSaveProfile: (displayName: String, upiVpa: String?, currency: CurrencyCode, darkMode: Boolean) -> Unit,
    onClearAllLedgerData: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onReplayCoachmarks: () -> Unit,
    onReplayLaunchAnimation: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    voiceSettings: VoiceSettingsState = VoiceSettingsState(),
    activeByokKeyCount: Int = 0,
    routingStrategy: RoutingStrategy = RoutingStrategy.BYOK_FIRST_THEN_FIREBASE,
    onSelectVoiceLocale: (SplitFlowLocale) -> Unit = {},
    onSelectVoicePersona: (SplitFlowVoicePersona) -> Unit = {},
    onToggleWakeWord: (Boolean) -> Unit = {},
    onOpenAiProviderSettings: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val profile = snapshot.profile

    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "You") }
    var upiVpa by remember(profile?.upiVpa) { mutableStateOf(profile?.upiVpa ?: "") }
    var currency by remember(profile?.defaultCurrency) { mutableStateOf(profile?.currencyEnum ?: CurrencyCode.INR) }
    var darkMode by remember(isDarkMode) { mutableStateOf(isDarkMode) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val customQrBitmap = remember(customUpiQrPath) {
        val file = ReceiptPhotoStore.resolveFile(context, customUpiQrPath)
        if (file != null) BitmapFactory.decodeFile(file.absolutePath) else null
    }
    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = UpiQrStore.saveCustomUpiQr(context, uri)
            if (savedPath != null) onCustomUpiQrChanged(savedPath)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ACCOUNT & PREFERENCES", style = MaterialTheme.typography.labelSmall, color = tokens.positive)
                    Text("Profile & Ledger", style = MaterialTheme.typography.headlineMedium)
                }
                if (onClose != null) {
                    SpringIconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_profile_sheet")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close profile")
                    }
                }
            }
        }

        // Identity Summary Card (Editorial Gradient Surface)
        item {
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MemberAvatar(member = snapshot.selfMember, size = 50.dp, selected = true, showGuestDot = false)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (isGuestMode) "Guest Session · Local Mode" else (profile?.email ?: "Google Cloud Synced"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${snapshot.groups.size} groups · ${snapshot.expenses.size} splits · ${snapshot.receiptExpenses.size} receipts",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.positive
                        )
                    }
                }
            }
        }

        // My Personal UPI QR Code Card (Upload / Preview / Replace / Remove)
        item {
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Icon(
                                imageVector = Icons.Rounded.QrCode2,
                                contentDescription = null,
                                tint = tokens.acidLime,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "My UPI QR Code",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.onHero
                                )
                                Text(
                                    text = if (customQrBitmap != null) {
                                        "Custom QR attached · shown in Home & Settle Up"
                                    } else {
                                        "Add your GPay, PhonePe or Paytm QR screenshot"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.onHero.copy(alpha = 0.68f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpringSurface(
                            onClick = onOpenMyUpiQr,
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(76.dp)
                                .testTag("profile_upi_qr_preview_thumb")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (customQrBitmap != null) {
                                    Image(
                                        bitmap = customQrBitmap.asImageBitmap(),
                                        contentDescription = "Uploaded personal UPI QR thumbnail",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode2,
                                        contentDescription = "Auto UPI QR preview",
                                        tint = Color(0xFF141513),
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                        )
                                    )
                                    .springClickable(pressedScale = 0.96f) {
                                        qrPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("profile_upload_upi_qr_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = tokens.onAcidLime,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = if (customQrBitmap != null) "Replace UPI QR" else "Add UPI QR Image",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.onAcidLime
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpringSurface(
                                    onClick = onOpenMyUpiQr,
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_show_full_qr_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Fullscreen,
                                            contentDescription = null,
                                            tint = tokens.onHero,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Show QR",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = tokens.onHero
                                        )
                                    }
                                }
                                if (customQrBitmap != null) {
                                    SpringSurface(
                                        onClick = {
                                            UpiQrStore.clearCustomUpiQr(context)
                                            onCustomUpiQrChanged(null)
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = tokens.negativeContainer,
                                        modifier = Modifier.testTag("profile_remove_upi_qr_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Remove custom UPI QR",
                                                tint = tokens.negative,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isGuestMode) {
            item {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = tokens.aiAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Guest Mode (On-Device)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Your splits are saved privately on this phone. Sign in with Google anytime—your existing local splits, groups, and friends will automatically migrate to Cloud Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SpringButton(
                            onClick = onSignInWithGoogle,
                            enabled = !isGoogleSigningIn,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_google_sign_in_button")
                        ) {
                            if (isGoogleSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.background,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google for Cloud Sync")
                            }
                        }
                    }
                }
            }
        }

        // Editable Profile & UPI VPA Form
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Identity & Payment Preferences", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = upiVpa,
                        onValueChange = { upiVpa = it },
                        label = { Text("UPI VPA (for Settle Up QR & Nudges)") },
                        placeholder = { Text("name@okaxis") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_upi_input")
                    )

                    Text("Default Base Currency", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CurrencyCode.entries.forEach { c ->
                            FilterChip(
                                selected = currency == c,
                                onClick = { currency = c },
                                label = { Text("${c.symbol} ${c.name}") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Warm Obsidian Dark Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Switch between Warm Obsidian & Sand dark and Warm Alabaster light",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = darkMode,
                            onCheckedChange = {
                                darkMode = it
                                onToggleDarkMode(it)
                                onSaveProfile(displayName, upiVpa.takeIf { v -> v.isNotBlank() }, currency, it)
                            },
                            modifier = Modifier.testTag("profile_dark_mode_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Blur Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Mask currency amounts when showing your screen in public",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPrivacyBlurEnabled,
                            onCheckedChange = { onTogglePrivacyBlur() },
                            modifier = Modifier.testTag("profile_privacy_blur_switch")
                        )
                    }

                    SpringButton(
                        onClick = {
                            onSaveProfile(displayName, upiVpa.takeIf { it.isNotBlank() }, currency, darkMode)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_save_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Preferences")
                    }
                }
            }
        }

        // Voice & Multi-Provider AI Settings Card
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = tokens.aiAccent,
                            modifier = Modifier.size(19.dp)
                        )
                        Text("Voice & AI Providers", style = MaterialTheme.typography.titleMedium)
                    }

                    // BYOK API Keys & Routing launcher
                    Surface(
                        onClick = onOpenAiProviderSettings,
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.aiContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_open_ai_keys_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = tokens.aiAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "AI Providers & API Keys",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (activeByokKeyCount > 0) {
                                            "$activeByokKeyCount custom key(s) in round-robin · ${routingStrategy.label}"
                                        } else {
                                            "Built-in Gemini 3 · Tap to add Groq / OpenRouter / Cerebras keys"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text("Voice Recognition Language", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SplitFlowLocale.entries.forEach { loc ->
                            FilterChip(
                                selected = voiceSettings.locale == loc,
                                onClick = { onSelectVoiceLocale(loc) },
                                label = { Text("${loc.shortBadge} · ${loc.displayName}") }
                            )
                        }
                    }

                    Text("Voice Assistant Persona", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SplitFlowVoicePersona.entries.forEach { p ->
                            FilterChip(
                                selected = voiceSettings.persona == p,
                                onClick = { onSelectVoicePersona(p) },
                                label = { Text("${p.displayName} (${p.styleLabel})") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hands-free \"Hey SplitFlow\"", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Listen for wake phrase while app is in the foreground",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = voiceSettings.wakeWordEnabled,
                            onCheckedChange = onToggleWakeWord,
                            modifier = Modifier.testTag("profile_wake_word_switch")
                        )
                    }
                }
            }
        }

        // Reference Multi-Currency FX Rates Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, tint = tokens.aiAccent, modifier = Modifier.size(17.dp))
                    Text("Trip Multi-Currency Reference Rates", style = MaterialTheme.typography.titleSmall)
                }
                HairlineSection {
                    val foreigns = CurrencyCode.entries.filter { it != currency }
                    foreigns.forEachIndexed { idx, fc ->
                        val rateMicros = FinanceEngine.getFxRateMicros(fc, currency)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1 ${fc.name} (${fc.symbol})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${currency.symbol}${FinanceEngine.formatFxRate(rateMicros)} ${currency.name}",
                                style = TabularSmallStyle,
                                color = tokens.positive
                            )
                        }
                        if (idx < foreigns.lastIndex) {
                            HorizontalDivider(color = tokens.hairline)
                        }
                    }
                }
            }
        }

        // Guides & Walkthroughs
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tutorials & Walkthrough", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Revisit how Receipt Lens, Voice Split, Veg/Non-Veg + Drinks Studio, and Debt Simplifier work anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpringOutlinedButton(
                        onClick = onReplayOnboarding,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_replay_onboarding_button")
                    ) {
                        Icon(Icons.Rounded.Slideshow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Replay Onboarding Slides")
                    }
                    SpringOutlinedButton(
                        onClick = onReplayCoachmarks,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_replay_tour_button")
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Replay Interactive Home Tour")
                    }
                    SpringOutlinedButton(
                        onClick = onReplayLaunchAnimation,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_replay_launch_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Replay Editorial Entry Animation")
                    }
                }
            }
        }

        // Data Portability & Database Controls
        item {
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Ledger Data & Session", style = MaterialTheme.typography.titleMedium)

                    SpringOutlinedButton(
                        onClick = {
                            val csv = FinanceEngine.exportLedgerToCsv(
                                scopeName = "SplitFlow Full Ledger",
                                members = snapshot.members,
                                expenses = snapshot.expenses,
                                settlements = snapshot.settlements
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SplitFlow Full Ledger CSV")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export SplitFlow CSV"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_full_csv_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Export Full Ledger to CSV")
                    }

                    SpringOutlinedButton(
                        onClick = { showClearConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.negative),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_all_ledger_data_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Ledger Data")
                    }

                    SpringButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.negativeContainer,
                            contentColor = tokens.negative
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isGuestMode) "Exit Guest Mode" else "Sign Out")
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Ledger Data?") },
            text = {
                Text("This permanently deletes all your groups, expenses, settlements, and added contacts from your ${if (isGuestMode) "device" else "Cloud Firestore account"}. Your profile, UPI QR, and preferences stay intact.")
            },
            confirmButton = {
                SpringButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAllLedgerData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.negative,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_clear_ledger_button")
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
