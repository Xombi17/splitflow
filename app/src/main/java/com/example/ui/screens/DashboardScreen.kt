package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.DebtTransfer
import com.example.model.DietPreference
import com.example.model.ExpenseGroup
import com.example.model.ExpenseRecord
import com.example.ui.components.AiBadge
import com.example.ui.components.AnimatedStatusPill
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.DietMarkIcon
import com.example.ui.components.EditorialCard
import com.example.ui.components.HairlineSection
import com.example.ui.components.MemberAvatar
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.TabularCurrencyText
import com.example.ui.components.editorialSpecularSheen
import com.example.ui.components.tactileClickable
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularHeroStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snapshot: LedgerSnapshot,
    isOffline: Boolean,
    isGuestMode: Boolean,
    isDarkMode: Boolean,
    isPrivacyBlurEnabled: Boolean,
    hasCustomUpiQr: Boolean,
    todayIso: String,
    onToggleDarkMode: () -> Unit,
    onTogglePrivacyBlur: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMyUpiQr: () -> Unit,
    onOpenAiLens: () -> Unit,
    onOpenBatchImporter: () -> Unit,
    onOpenVoiceSplitter: () -> Unit,
    onOpenSpendCoach: () -> Unit,
    onOpenDiningStudio: () -> Unit,
    onOpenManualSplit: () -> Unit,
    onOpenGroup: (ExpenseGroup) -> Unit,
    onOpenSettleUp: (DebtTransfer) -> Unit,
    onOpenNudge: (DebtTransfer) -> Unit,
    onLogRecurringCycle: (ExpenseRecord) -> Unit,
    onOpenSharedSplit: (ExpenseRecord) -> Unit,
    onViewReceipt: (ExpenseRecord) -> Unit,
    onViewAllExpenses: () -> Unit,
    onOpenGroupsTab: () -> Unit,
    onOpenPeopleTab: () -> Unit,
    onOpenImportContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }
    val debtReport = remember(snapshot.members, snapshot.expenses, snapshot.settlements, snapshot.defaultCurrency) {
        snapshot.globalDebtReport
    }
    var showSimplified by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Editorial Top Header (Stagger 0)
        item {
            StaggeredEntrance(index = 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                Text(
                    text = "Hey, ${snapshot.selfMember?.name?.split(" ")?.firstOrNull() ?: "there"}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SpringIconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (isDarkMode) "Switch to Warm Alabaster Light Mode" else "Switch to Warm Obsidian Dark Mode",
                            tint = tokens.inkMuted
                        )
                    }
                    SpringIconButton(
                        onClick = onTogglePrivacyBlur,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("privacy_blur_toggle")
                    ) {
                        Icon(
                            imageVector = if (isPrivacyBlurEnabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isPrivacyBlurEnabled) "Disable privacy blur" else "Enable privacy blur",
                            tint = if (isPrivacyBlurEnabled) MaterialTheme.colorScheme.onBackground else tokens.inkMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .springClickable(pressedScale = 0.90f, onClick = onOpenProfile)
                            .testTag("open_profile_avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            member = snapshot.selfMember,
                            size = 38.dp,
                            selected = true,
                            showGuestDot = false
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isGuestMode) tokens.acidLime else tokens.positive)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
                }
            }
        }

        if (isOffline) {
            item { OfflineStatusBanner() }
        }

        // Elevated Hero Net Position Card (Stagger 1 + Specular Sheen + Rolling Counter)
        item {
            StaggeredEntrance(
                index = 1,
                stepDelayMs = 64L,
                initialOffsetDp = 26.dp,
                initialScale = 0.955f
            ) {
            val netMinor = snapshot.selfNetMinorUnits
            val badgeBg = when {
                netMinor > 0L -> tokens.acidLime
                netMinor < 0L -> tokens.negative
                else -> tokens.elevatedSurface
            }
            val badgeInk = when {
                netMinor > 0L -> tokens.onAcidLime
                netMinor < 0L -> Color.White
                else -> tokens.onHero.copy(alpha = 0.85f)
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, tokens.heroBorder),
                shadowElevation = if (tokens.isDark) 0.dp else 5.dp,
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
                            // Subtle editorial top-right ambient Acid-Lime radial sheen + soft top highlight
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(tokens.heroAccentGlow, Color.Transparent),
                                    center = Offset(size.width * 0.9f, size.height * 0.14f),
                                    radius = size.width * 0.62f
                                ),
                                radius = size.width * 0.62f,
                                center = Offset(size.width * 0.9f, size.height * 0.14f)
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (tokens.isDark) 0.05f else 0.06f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.42f
                                )
                            )
                        }
                        .editorialSpecularSheen(
                            sheenColor = Color.White.copy(alpha = if (tokens.isDark) 0.045f else 0.07f)
                        )
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NET POSITION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tokens.onHero.copy(alpha = 0.62f)
                        )
                        AnimatedStatusPill(
                            text = when {
                                netMinor > 0L -> "YOU ARE OWED"
                                netMinor < 0L -> "YOU OWE"
                                snapshot.expenses.isEmpty() -> "READY TO SPLIT"
                                else -> "ALL SETTLED"
                            },
                            containerColor = badgeBg,
                            contentColor = badgeInk
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabularCurrencyText(
                            minorUnits = netMinor,
                            currency = snapshot.defaultCurrency,
                            showPlusSign = true,
                            color = tokens.onHero,
                            style = TabularHeroStyle,
                            modifier = Modifier.weight(1f)
                        )
                        SpringSurface(
                            onClick = onOpenMyUpiQr,
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = if (tokens.isDark) 0.08f else 0.11f),
                            border = BorderStroke(
                                1.dp,
                                if (hasCustomUpiQr) tokens.acidLime.copy(alpha = 0.55f)
                                else Color.White.copy(alpha = 0.16f)
                            ),
                            pressedScale = 0.94f,
                            modifier = Modifier.testTag("home_my_upi_qr_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Open my UPI QR code",
                                    tint = if (hasCustomUpiQr) tokens.acidLime else tokens.onHero,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (hasCustomUpiQr) "My QR" else "Add QR",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.onHero
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = tokens.heroDivider)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Owed to you",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onHero.copy(alpha = 0.58f)
                            )
                            TabularCurrencyText(
                                minorUnits = snapshot.totalOwedToSelfMinor,
                                currency = snapshot.defaultCurrency,
                                color = tokens.onHero,
                                style = TabularAmountStyle
                            )
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 16.dp),
                            color = tokens.heroDivider
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "You owe",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onHero.copy(alpha = 0.58f)
                            )
                            TabularCurrencyText(
                                minorUnits = snapshot.totalSelfOwesMinor,
                                currency = snapshot.defaultCurrency,
                                color = if (snapshot.totalSelfOwesMinor > 0L) tokens.negative else tokens.onHero,
                                style = TabularAmountStyle
                            )
                        }
                    }
                }
            }
            }
        }

        // Single Horizontal 4-Column Tactile Circular Action Row with Per-Button Ripple Stagger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                StaggeredEntrance(
                    index = 0,
                    baseDelayMs = 128L,
                    stepDelayMs = 42L,
                    initialOffsetDp = 20.dp,
                    initialScale = 0.88f,
                    modifier = Modifier.weight(1f)
                ) {
                    TactileCircularAction(
                        label = "Scan",
                        icon = Icons.Rounded.QrCodeScanner,
                        isHighlighted = true,
                        onClick = onOpenAiLens,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_ai_lens_button")
                    )
                }
                StaggeredEntrance(
                    index = 1,
                    baseDelayMs = 128L,
                    stepDelayMs = 42L,
                    initialOffsetDp = 20.dp,
                    initialScale = 0.88f,
                    modifier = Modifier.weight(1f)
                ) {
                    TactileCircularAction(
                        label = "Voice",
                        icon = Icons.Rounded.Mic,
                        isHighlighted = false,
                        onClick = onOpenVoiceSplitter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_voice_split_button")
                    )
                }
                StaggeredEntrance(
                    index = 2,
                    baseDelayMs = 128L,
                    stepDelayMs = 42L,
                    initialOffsetDp = 20.dp,
                    initialScale = 0.88f,
                    modifier = Modifier.weight(1f)
                ) {
                    TactileCircularAction(
                        label = "Batch",
                        icon = Icons.Rounded.FolderOpen,
                        isHighlighted = false,
                        onClick = onOpenBatchImporter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_batch_scan_button")
                    )
                }
                StaggeredEntrance(
                    index = 3,
                    baseDelayMs = 128L,
                    stepDelayMs = 42L,
                    initialOffsetDp = 20.dp,
                    initialScale = 0.88f,
                    modifier = Modifier.weight(1f)
                ) {
                    TactileCircularAction(
                        label = "Coach",
                        icon = Icons.Rounded.ChatBubbleOutline,
                        isHighlighted = false,
                        onClick = onOpenSpendCoach,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_spend_coach_button")
                    )
                }
            }
        }

        // Guided First-Split Card for brand-new / freshly cleared accounts (Stagger 3)
        val hasFriends = snapshot.members.any { !it.selfProfile }
        val isFreshWorkspace = snapshot.expenses.isEmpty() && snapshot.groups.isEmpty() && !hasFriends
        if (isFreshWorkspace) {
            item {
                StaggeredEntrance(index = 3) {
                    GuidedFirstSplitCard(
                        hasCustomUpiQr = hasCustomUpiQr,
                        onAddFriend = onOpenPeopleTab,
                        onImportContacts = onOpenImportContacts,
                        onCreateGroup = onOpenGroupsTab,
                        onScanReceipt = onOpenAiLens,
                        onLogManualSplit = onOpenManualSplit,
                        onOpenMyUpiQr = onOpenMyUpiQr
                    )
                }
            }
        }

        // Settle Up Crisp Grouped Card (hidden when workspace is brand new so guided start takes focus)
        if (!isFreshWorkspace) {
        item {
            StaggeredEntrance(index = 3) {
            val activeTransfers = if (showSimplified) debtReport.simplifiedTransfers else debtReport.rawTransfers
            HairlineSection(shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settle Up",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (debtReport.transfersSaved > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.positiveContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { showSimplified = !showSimplified }
                                        .testTag(if (showSimplified) "debt_toggle_simplified" else "debt_toggle_raw")
                                ) {
                                    Text(
                                        text = if (showSimplified) "${debtReport.transfersSaved} saved" else "Raw (${debtReport.rawTransfers.size})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.positive,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = tokens.hairline)

                    if (activeTransfers.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = tokens.positive)
                            Text(
                                text = "Every member balance is squared up.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val visibleTransfers = activeTransfers.take(4)
                        visibleTransfers.forEachIndexed { idx, transfer ->
                            val from = memberMap[transfer.fromMemberId]
                            val to = memberMap[transfer.toMemberId]
                            val fromLabel = if (transfer.fromMemberId == snapshot.selfMember?.id) "You" else from?.name?.split(" ")?.firstOrNull() ?: "Member"
                            val toLabel = if (transfer.toMemberId == snapshot.selfMember?.id) "You" else to?.name?.split(" ")?.firstOrNull() ?: "Member"
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.StartToEnd) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenSettleUp(transfer)
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromEndToStart = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(tokens.positiveContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Rounded.Payments, contentDescription = null, tint = tokens.positive)
                                            Text("Settle Up", style = MaterialTheme.typography.labelLarge, color = tokens.positive)
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(tokens.cardSurface)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        MemberAvatar(member = from, size = 40.dp, selected = true, showGuestDot = false)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = fromLabel,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                    contentDescription = "pays",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = tokens.inkMuted
                                                )
                                                Text(
                                                    text = toLabel,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            TabularCurrencyText(
                                                minorUnits = transfer.amountMinorUnits,
                                                currency = transfer.currency,
                                                style = TabularAmountStyle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        SpringIconButton(
                                            onClick = { onOpenNudge(transfer) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("nudge_transfer_${transfer.fromMemberId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.NotificationsActive,
                                                contentDescription = "Send payment nudge",
                                                tint = tokens.inkMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        SpringButton(
                                            onClick = { onOpenSettleUp(transfer) },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = tokens.obsidian,
                                                contentColor = tokens.onObsidian
                                            ),
                                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                            modifier = Modifier.testTag("settle_up_${transfer.fromMemberId}")
                                        ) {
                                            Text(
                                                text = "Settle",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }
                            if (idx < visibleTransfers.lastIndex) {
                                HorizontalDivider(
                                    color = tokens.hairline,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        }
        }

        // Table & Bar Split Studio Quick Banner (Stagger 4)
        item {
            StaggeredEntrance(index = 4) {
            EditorialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_dining_studio_button"),
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenDiningStudio()
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(tokens.elevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalBar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Table & Bar Split",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                DietMarkIcon(diet = DietPreference.VEG, size = 10.dp)
                                DietMarkIcon(diet = DietPreference.NON_VEG, size = 10.dp)
                            }
                            Text(
                                text = "Veg/Non-Veg dishes, drinks by glass & Bar VAT",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Open Table and Bar Split",
                        tint = tokens.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            }
        }

        // Upcoming Recurring Bills Strip (Stagger 5)
        if (snapshot.recurringExpenses.isNotEmpty()) {
            item {
                StaggeredEntrance(index = 5) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recurring Bills",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${snapshot.recurringExpenses.size} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.recurringExpenses, key = { it.id }) { recurring ->
                            val daysLeft = FinanceEngine.daysUntilIso(recurring.nextDueDateLabel, todayIso)
                            val dueLabel = when {
                                daysLeft == null -> recurring.nextDueDateLabel ?: "Scheduled"
                                daysLeft < 0 -> "Overdue ${-daysLeft}d"
                                daysLeft == 0 -> "Due today"
                                daysLeft <= 7 -> "In ${daysLeft}d"
                                else -> "Due ${recurring.nextDueDateLabel}"
                            }
                            val dueColor = when {
                                daysLeft != null && daysLeft <= 0 -> tokens.negative
                                daysLeft != null && daysLeft <= 5 -> tokens.warning
                                else -> tokens.positive
                            }
                            EditorialCard(
                                modifier = Modifier
                                    .width(228.dp)
                                    .testTag("recurring_bill_card_${recurring.id}"),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Repeat,
                                                contentDescription = null,
                                                tint = tokens.inkMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = recurring.recurringEnum.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.inkMuted
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = dueColor.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = dueLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = dueColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = recurring.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TabularCurrencyText(
                                            minorUnits = recurring.totalMinorUnits,
                                            currency = recurring.currencyEnum,
                                            style = TabularAmountStyle
                                        )
                                        SpringSurface(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onLogRecurringCycle(recurring)
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = tokens.obsidian,
                                            hapticFeedback = false,
                                            modifier = Modifier.testTag("log_cycle_${recurring.id}")
                                        ) {
                                            Text(
                                                text = "Log",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = tokens.onObsidian,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
        }

        // Active Groups Strip (Stagger 6)
        if (snapshot.groups.isNotEmpty()) {
            item {
                StaggeredEntrance(index = 6) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Active Groups",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.groups, key = { it.id }) { group ->
                            val grpExpenses = snapshot.expenses.filter { it.groupId == group.id }
                            val budgetStatus = FinanceEngine.computeGroupBudgetStatus(group, grpExpenses)
                            EditorialCard(
                                modifier = Modifier
                                    .width(232.dp)
                                    .testTag("dashboard_group_card_${group.id}"),
                                shape = RoundedCornerShape(22.dp),
                                onClick = { onOpenGroup(group) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (budgetStatus.budgetMinorUnits != null) {
                                        BudgetProgressBar(
                                            status = budgetStatus,
                                            currency = group.currencyEnum,
                                            compact = true
                                        )
                                    } else {
                                        TabularCurrencyText(
                                            minorUnits = budgetStatus.spentMinorUnits,
                                            currency = group.currencyEnum,
                                            style = TabularAmountStyle,
                                            color = MaterialTheme.colorScheme.onSurface
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

        // Recent Activity Compact Grouped Card (Stagger 7)
        item {
            StaggeredEntrance(index = 7) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.positive,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .springClickable(pressedScale = 0.94f, onClick = onViewAllExpenses)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (snapshot.expenses.isEmpty()) {
                    HairlineSection {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = tokens.inkMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("No splits recorded yet", style = MaterialTheme.typography.titleMedium)
                            SpringButton(
                                onClick = onOpenManualSplit,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.obsidian,
                                    contentColor = tokens.onObsidian
                                )
                            ) {
                                Text("Create First Split")
                            }
                        }
                    }
                } else {
                    val recent = snapshot.expenses.take(4)
                    HairlineSection {
                        recent.forEachIndexed { idx, expense ->
                            val payerNames = expense.payers
                                .mapNotNull { memberMap[it.memberId]?.name?.split(" ")?.firstOrNull() }
                                .joinToString(" & ").ifBlank { "Someone" }
                            val groupName = snapshot.groups.firstOrNull { it.id == expense.groupId }?.name ?: "Personal"
                            val hasReceipt = !expense.receiptPhotoPath.isNullOrBlank()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .springClickable(pressedScale = 0.985f) { onOpenSharedSplit(expense) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .testTag("recent_expense_${expense.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(tokens.elevatedSurface)
                                        .then(
                                            if (hasReceipt) Modifier.clickable { onViewReceipt(expense) } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            hasReceipt -> Icons.Rounded.PhotoLibrary
                                            expense.aiExtracted -> Icons.Rounded.AutoAwesome
                                            else -> Icons.Rounded.ReceiptLong
                                        },
                                        contentDescription = if (hasReceipt) "View receipt photo" else null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                                            text = expense.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (expense.aiExtracted) AiBadge("AI Scan")
                                    }
                                    Text(
                                        text = "$groupName • $payerNames",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TabularCurrencyText(
                                    minorUnits = expense.totalMinorUnits,
                                    currency = expense.currencyEnum,
                                    style = TabularAmountStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (idx < recent.lastIndex) {
                                HorizontalDivider(
                                    color = tokens.hairline,
                                    modifier = Modifier.padding(horizontal = 16.dp)
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

@Composable
private fun GuidedFirstSplitCard(
    hasCustomUpiQr: Boolean,
    onAddFriend: () -> Unit,
    onImportContacts: () -> Unit,
    onCreateGroup: () -> Unit,
    onScanReceipt: () -> Unit,
    onLogManualSplit: () -> Unit,
    onOpenMyUpiQr: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    HairlineSection(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("guided_first_split_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.positiveContainer
                ) {
                    Text(
                        text = "FRESH WORKSPACE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.positive,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Start your first split",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your ledger starts completely clean. Pick any step below to begin tracking real trips, bills, or dinners.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = tokens.hairline)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tokens.elevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Add your first friend",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pick specific friends from your phone contacts or add manually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringSurface(
                        onClick = onImportContacts,
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.acidLime,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("guided_step_import_contacts")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Import Contacts",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = tokens.onAcidLime
                            )
                        }
                    }
                    SpringSurface(
                        onClick = onAddFriend,
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.obsidian,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("guided_step_add_friend")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Add Manually",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = tokens.onObsidian
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = tokens.hairline, modifier = Modifier.padding(horizontal = 16.dp))

            GuidedStepRow(
                icon = Icons.Rounded.GroupAdd,
                title = "Create a trip or flatmates group",
                subtitle = "Track shared budgets, multi-currency FX & smart split memory",
                actionLabel = "Create",
                highlighted = false,
                onClick = onCreateGroup,
                testTag = "guided_step_create_group"
            )
            HorizontalDivider(color = tokens.hairline, modifier = Modifier.padding(horizontal = 16.dp))

            GuidedStepRow(
                icon = Icons.Rounded.QrCodeScanner,
                title = "Scan a bill or log an expense",
                subtitle = "Snap a receipt photo for ML Kit & Gemini OCR itemization or enter manually",
                actionLabel = "Scan",
                highlighted = true,
                onClick = onScanReceipt,
                testTag = "guided_step_scan_receipt"
            )
            HorizontalDivider(color = tokens.hairline, modifier = Modifier.padding(horizontal = 16.dp))

            GuidedStepRow(
                icon = Icons.Rounded.QrCode2,
                title = if (hasCustomUpiQr) "Personal UPI QR attached" else "Attach your UPI QR code",
                subtitle = if (hasCustomUpiQr) "Ready for instant scan-to-pay settlements" else "Upload your GPay, PhonePe, or Paytm QR image",
                actionLabel = if (hasCustomUpiQr) "View" else "Add QR",
                highlighted = false,
                onClick = onOpenMyUpiQr,
                testTag = "guided_step_upi_qr"
            )
        }
    }
}

@Composable
private fun GuidedStepRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tokens = SplitFlowThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tactileClickable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (highlighted) tokens.acidLime else tokens.elevatedSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) tokens.onAcidLime else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (highlighted) tokens.acidLime else tokens.obsidian
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (highlighted) tokens.onAcidLime else tokens.onObsidian,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TactileCircularAction(
    label: String,
    icon: ImageVector,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .tactileClickable(pressedScale = 0.92f, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = if (isHighlighted) null else BorderStroke(1.dp, tokens.hairline),
            shadowElevation = if (tokens.isDark) 0.dp else if (isHighlighted) 3.dp else 1.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isHighlighted) {
                            Brush.linearGradient(
                                colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isHighlighted) tokens.onAcidLime else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
fun ToggleSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    SpringSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) tokens.obsidian else Color.Transparent,
        pressedScale = 0.96f
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) tokens.onObsidian else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
