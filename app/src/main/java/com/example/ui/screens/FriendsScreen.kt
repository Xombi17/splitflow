package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.NoDrinks
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DebtTransfer
import com.example.model.DietPreference
import com.example.model.LedgerMember
import com.example.ui.components.DietMarkIcon
import com.example.ui.components.FluidSegmentedPillBar
import com.example.ui.components.GuestBadge
import com.example.ui.components.HairlineSection
import com.example.ui.components.MemberAvatar
import com.example.ui.components.TabularCurrencyText
import com.example.ui.components.parseHexColor
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

private enum class PeopleFilter {
    ALL,
    OWES_YOU,
    GUESTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    onOpenProfile: () -> Unit,
    onAddMember: (name: String, email: String?, upiVpa: String?, isGuest: Boolean, colorHex: String, diet: DietPreference, drinksAlcohol: Boolean) -> Unit,
    onUpdateMemberPreferences: (memberId: String, diet: DietPreference, drinksAlcohol: Boolean) -> Unit,
    onClaimGuestProfile: (memberId: String, name: String, email: String, upiVpa: String?) -> Unit,
    onOpenSettleUp: (DebtTransfer) -> Unit,
    onOpenNudge: (DebtTransfer) -> Unit,
    onOpenPersonalSplit: (LedgerMember) -> Unit,
    onOpenImportContacts: () -> Unit,
    openManualAddSignal: Int = 0,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val selfId = snapshot.selfMember?.id
    val friends = remember(snapshot.members) { snapshot.members.filter { !it.selfProfile } }
    val debtReport = remember(snapshot.members, snapshot.expenses, snapshot.settlements, snapshot.defaultCurrency) {
        snapshot.globalDebtReport
    }

    var activeFilter by remember { mutableStateOf(PeopleFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }
    var guestToClaim by remember { mutableStateOf<LedgerMember?>(null) }
    var selectedFriendForDetail by remember { mutableStateOf<LedgerMember?>(null) }

    LaunchedEffect(openManualAddSignal) {
        if (openManualAddSignal > 0) {
            showAddDialog = true
        }
    }

    val netByFriendId = remember(friends, debtReport, selfId) {
        friends.associate { friend ->
            val owedToSelf = debtReport.simplifiedTransfers
                .filter { it.fromMemberId == friend.id && it.toMemberId == selfId }
                .sumOf { it.amountMinorUnits }
            val selfOwesFriend = debtReport.simplifiedTransfers
                .filter { it.fromMemberId == selfId && it.toMemberId == friend.id }
                .sumOf { it.amountMinorUnits }
            friend.id to (owedToSelf - selfOwesFriend)
        }
    }

    val showGuestClaimUi = isGuestMode
    val guestCount = remember(friends, showGuestClaimUi) {
        if (showGuestClaimUi) friends.count { it.guestProfile } else 0
    }

    LaunchedEffect(showGuestClaimUi) {
        if (!showGuestClaimUi && activeFilter == PeopleFilter.GUESTS) {
            activeFilter = PeopleFilter.ALL
        }
    }

    val filteredFriends = remember(friends, activeFilter, netByFriendId, showGuestClaimUi) {
        when (activeFilter) {
            PeopleFilter.ALL -> friends
            PeopleFilter.OWES_YOU -> friends.filter { (netByFriendId[it.id] ?: 0L) > 0L }
            PeopleFilter.GUESTS -> if (showGuestClaimUi) friends.filter { it.guestProfile } else friends
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("friends_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Editorial Header: People + Matte Obsidian "+ Add" pill + compact profile dot
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpringOutlinedButton(
                        onClick = onOpenImportContacts,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = tokens.cardSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("import_contacts_header_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contacts,
                            contentDescription = "Import from phone contacts",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Contacts",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    SpringButton(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.obsidian,
                            contentColor = tokens.onObsidian
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("add_friend_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .springClickable(pressedScale = 0.90f, onClick = onOpenProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        MemberAvatar(
                            member = snapshot.selfMember,
                            size = 34.dp,
                            selected = true,
                            showGuestDot = false
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isGuestMode) tokens.warning else tokens.positive)
                                .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }
                }
            }
        }

        // Compact Single-Line Soft-Amber Shadow Guest Notice Banner (only in Guest Mode)
        if (showGuestClaimUi && guestCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.warningContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { activeFilter = PeopleFilter.GUESTS }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HowToReg,
                            contentDescription = null,
                            tint = tokens.warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$guestCount shadow guest ${if (guestCount == 1) "profile" else "profiles"} ready to claim",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Apple-Style Fluid Sliding Segmented Filter Bar
        item {
            if (showGuestClaimUi) {
                FluidSegmentedPillBar(
                    labels = listOf("All (${friends.size})", "Owes you", "Guests ($guestCount)"),
                    selectedIndex = activeFilter.ordinal,
                    onSelect = { idx -> activeFilter = PeopleFilter.entries[idx] },
                    testTags = listOf("people_filter_all", "people_filter_owes_you", "people_filter_guests")
                )
            } else {
                FluidSegmentedPillBar(
                    labels = listOf("All (${friends.size})", "Owes you"),
                    selectedIndex = if (activeFilter == PeopleFilter.OWES_YOU) 1 else 0,
                    onSelect = { idx ->
                        activeFilter = if (idx == 1) PeopleFilter.OWES_YOU else PeopleFilter.ALL
                    },
                    testTags = listOf("people_filter_all", "people_filter_owes_you")
                )
            }
        }

        // Single-Container Compact Hairline-Divided People List
        if (filteredFriends.isEmpty()) {
            item {
                HairlineSection {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = tokens.inkMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = when (activeFilter) {
                                PeopleFilter.OWES_YOU -> "Everyone is all settled up"
                                PeopleFilter.GUESTS -> "No pending shadow guests"
                                PeopleFilter.ALL -> "No contacts yet"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Import just the friends you want from your phone contacts, or add someone manually with optional UPI & Veg/Drinks preferences.",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                        if (activeFilter == PeopleFilter.ALL) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SpringButton(
                                    onClick = onOpenImportContacts,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.acidLime,
                                        contentColor = tokens.onAcidLime
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("empty_import_contacts_button")
                                ) {
                                    Icon(Icons.Rounded.Contacts, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Import from Phone Contacts",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                SpringOutlinedButton(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("empty_add_first_friend_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Add Manually",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                HairlineSection(shape = RoundedCornerShape(24.dp)) {
                    filteredFriends.forEachIndexed { idx, friend ->
                        val netWithSelf = netByFriendId[friend.id] ?: 0L
                        val sharedCount = snapshot.expenses.count { exp ->
                            exp.payers.any { it.memberId == friend.id } || exp.allocations.any { it.memberId == friend.id }
                        }
                        val transfer = if (netWithSelf != 0L && selfId != null) {
                            if (netWithSelf > 0L) {
                                DebtTransfer(friend.id, selfId, netWithSelf, snapshot.defaultCurrency, null)
                            } else {
                                DebtTransfer(selfId, friend.id, -netWithSelf, snapshot.defaultCurrency, null)
                            }
                        } else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .springClickable(pressedScale = 0.985f) { selectedFriendForDetail = friend }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("friend_card_${friend.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MemberAvatar(
                                member = friend,
                                size = 42.dp,
                                selected = true,
                                showGuestDot = false
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = friend.name,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (showGuestClaimUi && friend.guestProfile) {
                                        GuestBadge(isGuest = true)
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append("$sharedCount ${if (sharedCount == 1) "split" else "splits"}")
                                        if (netWithSelf == 0L && (!showGuestClaimUi || !friend.guestProfile)) {
                                            append(" · Settled")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right-aligned balance + ONE compact action pill
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (!showGuestClaimUi || !friend.guestProfile || netWithSelf != 0L) {
                                    TabularCurrencyText(
                                        minorUnits = netWithSelf,
                                        currency = snapshot.defaultCurrency,
                                        showPlusSign = netWithSelf != 0L,
                                        style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                                        color = when {
                                            netWithSelf > 0L -> tokens.positive
                                            netWithSelf < 0L -> tokens.negative
                                            else -> tokens.inkMuted
                                        }
                                    )
                                }
                                when {
                                    showGuestClaimUi && friend.guestProfile -> {
                                        SpringSurface(
                                            onClick = { guestToClaim = friend },
                                            shape = RoundedCornerShape(999.dp),
                                            color = tokens.acidLime,
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("claim_guest_${friend.id}")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Claim",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = tokens.onAcidLime
                                                )
                                            }
                                        }
                                    }
                                    transfer != null -> {
                                        SpringSurface(
                                            onClick = { onOpenSettleUp(transfer) },
                                            shape = RoundedCornerShape(999.dp),
                                            color = tokens.obsidian,
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Settle",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = tokens.onObsidian
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        SpringSurface(
                                            onClick = { onOpenPersonalSplit(friend) },
                                            shape = RoundedCornerShape(999.dp),
                                            color = tokens.elevatedSurface,
                                            border = BorderStroke(1.dp, tokens.hairline),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "1-on-1",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.SemiBold
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (idx < filteredFriends.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = tokens.hairline
                            )
                        }
                    }
                }
            }
        }
    }

    // Contact Detail, 1-on-1 Split, Nudge & Dining Preferences Sheet (opened by tapping any row)
    selectedFriendForDetail?.let { tappedFriend ->
        val liveFriend = snapshot.members.firstOrNull { it.id == tappedFriend.id } ?: tappedFriend
        val netWithSelf = netByFriendId[liveFriend.id] ?: 0L
        val sharedCount = snapshot.expenses.count { exp ->
            exp.payers.any { it.memberId == liveFriend.id } || exp.allocations.any { it.memberId == liveFriend.id }
        }
        val transfer = if (netWithSelf != 0L && selfId != null) {
            if (netWithSelf > 0L) {
                DebtTransfer(liveFriend.id, selfId, netWithSelf, snapshot.defaultCurrency, null)
            } else {
                DebtTransfer(selfId, liveFriend.id, -netWithSelf, snapshot.defaultCurrency, null)
            }
        } else null

        ModalBottomSheet(
            onDismissRequest = { selectedFriendForDetail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        MemberAvatar(member = liveFriend, size = 48.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = liveFriend.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                if (showGuestClaimUi) {
                                    GuestBadge(liveFriend.guestProfile)
                                }
                            }
                            Text(
                                text = buildString {
                                    append("$sharedCount split(s)")
                                    if (!liveFriend.upiVpa.isNullOrBlank()) append(" · ${liveFriend.upiVpa}")
                                    else if (!liveFriend.email.isNullOrBlank()) append(" · ${liveFriend.email}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkMuted
                            )
                        }
                    }
                    SpringIconButton(onClick = { selectedFriendForDetail = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close contact sheet")
                    }
                }

                // Net Position Summary Tile
                HairlineSection(shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                netWithSelf > 0L -> "${liveFriend.name.split(" ").first()} owes you"
                                netWithSelf < 0L -> "You owe ${liveFriend.name.split(" ").first()}"
                                else -> "All settled up"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TabularCurrencyText(
                            minorUnits = netWithSelf,
                            currency = snapshot.defaultCurrency,
                            showPlusSign = netWithSelf != 0L,
                            style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                netWithSelf > 0L -> tokens.positive
                                netWithSelf < 0L -> tokens.negative
                                else -> tokens.inkMuted
                            }
                        )
                    }
                }

                // Dining & Bar Preferences (Auto-saved for Table & Bar / AI Lens splits)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DINING & BAR PREFERENCES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.inkMuted
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val diet = liveFriend.dietEnum
                        val dietTint = parseHexColor(diet.colorHex)
                        SpringSurface(
                            onClick = {
                                val allDiets = DietPreference.entries
                                val nextDiet = allDiets[(allDiets.indexOf(diet) + 1) % allDiets.size]
                                onUpdateMemberPreferences(liveFriend.id, nextDiet, liveFriend.isAlcoholDrinker)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.cardSurface,
                            border = BorderStroke(1.dp, dietTint.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("friend_diet_toggle_${liveFriend.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DietMarkIcon(diet = diet, size = 10.dp)
                                Text(
                                    text = diet.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = dietTint
                                )
                            }
                        }

                        val drinks = liveFriend.isAlcoholDrinker
                        SpringSurface(
                            onClick = {
                                onUpdateMemberPreferences(liveFriend.id, diet, !drinks)
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = if (drinks) tokens.obsidian else tokens.cardSurface,
                            border = if (drinks) null else BorderStroke(1.dp, tokens.hairline),
                            modifier = Modifier.testTag("friend_drinks_toggle_${liveFriend.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (drinks) Icons.Rounded.LocalBar else Icons.Rounded.NoDrinks,
                                    contentDescription = null,
                                    tint = if (drinks) tokens.onObsidian else tokens.inkMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (drinks) "Drinks Alcohol" else "0% Alcohol",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (drinks) tokens.onObsidian else tokens.inkMuted
                                )
                            }
                        }
                    }
                }

                // Actions Row
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpringOutlinedButton(
                            onClick = {
                                selectedFriendForDetail = null
                                onOpenPersonalSplit(liveFriend)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, tokens.hairline)
                        ) {
                            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("1-on-1 Split", color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (transfer != null) {
                            SpringButton(
                                onClick = {
                                    selectedFriendForDetail = null
                                    onOpenSettleUp(transfer)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.obsidian,
                                    contentColor = tokens.onObsidian
                                )
                            ) {
                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Settle Up", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (transfer != null && netWithSelf > 0L) {
                        SpringOutlinedButton(
                            onClick = {
                                selectedFriendForDetail = null
                                onOpenNudge(transfer)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("nudge_friend_${liveFriend.id}"),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, tokens.positive.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = tokens.positive,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Send Payment Nudge",
                                color = tokens.positive,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showGuestClaimUi && liveFriend.guestProfile) {
                        SpringButton(
                            onClick = {
                                selectedFriendForDetail = null
                                guestToClaim = liveFriend
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.acidLime,
                                contentColor = tokens.onAcidLime
                            )
                        ) {
                            Icon(Icons.Rounded.HowToReg, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Claim & Verify Guest Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemberDialog(
            allowGuestToggle = showGuestClaimUi,
            onDismiss = { showAddDialog = false },
            onImportFromContacts = {
                showAddDialog = false
                onOpenImportContacts()
            },
            onAdd = { name, email, upi, isGuest, colorHex, diet, drinks ->
                onAddMember(name, email, upi, isGuest, colorHex, diet, drinks)
                showAddDialog = false
            }
        )
    }

    if (showGuestClaimUi) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMemberDialog(
    allowGuestToggle: Boolean,
    onDismiss: () -> Unit,
    onImportFromContacts: () -> Unit,
    onAdd: (name: String, email: String?, upiVpa: String?, isGuest: Boolean, colorHex: String, diet: DietPreference, drinksAlcohol: Boolean) -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    var name by remember { mutableStateOf("") }
    var isGuest by remember(allowGuestToggle) { mutableStateOf(allowGuestToggle) }
    var email by remember { mutableStateOf("") }
    var upiVpa by remember { mutableStateOf("") }
    var dietPreference by remember { mutableStateOf(DietPreference.NON_VEG) }
    var drinksAlcohol by remember { mutableStateOf(true) }
    val palette = listOf("#0E6F4E", "#2C4C7E", "#B45333", "#C5962C", "#5B4B8A", "#141513")
    val pickedColor = remember { palette.random() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = tokens.cardSurface,
        title = {
            Text(
                text = if (allowGuestToggle) "Add Friend or Shadow Guest" else "Add Friend",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            val focusedBorder = if (tokens.isDark) tokens.acidLime else tokens.obsidian
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = tokens.elevatedSurface,
                unfocusedContainerColor = tokens.elevatedSurface,
                focusedBorderColor = focusedBorder,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onImportFromContacts,
                    shape = RoundedCornerShape(14.dp),
                    color = tokens.elevatedSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_import_contacts_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(tokens.acidLime),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Contacts,
                                contentDescription = null,
                                tint = tokens.onAcidLime,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import from Phone Contacts",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select specific friends directly from your address book",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkMuted
                            )
                        }
                    }
                }
                if (allowGuestToggle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Zero-Friction Guest Profile", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Name-only member. Can claim their profile later via split link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = isGuest, onCheckedChange = { isGuest = it })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    placeholder = { Text("e.g., Vikram Rao") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_member_name_input")
                )
                if (!isGuest) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = upiVpa,
                    onValueChange = { upiVpa = it },
                    label = { Text("UPI VPA (optional)") },
                    placeholder = { Text("e.g., vikram@okaxis") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dining Preference (for Veg/Non-Veg splits)", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DietPreference.entries.forEach { d ->
                            FilterChip(
                                selected = dietPreference == d,
                                onClick = { dietPreference = d },
                                leadingIcon = { DietMarkIcon(diet = d, size = 10.dp) },
                                label = { Text(d.shortLabel) }
                            )
                        }
                    }
                }
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
                            imageVector = if (drinksAlcohol) Icons.Rounded.LocalBar else Icons.Rounded.NoDrinks,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Drinks Alcohol", style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(checked = drinksAlcohol, onCheckedChange = { drinksAlcohol = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            name.trim(),
                            email.takeIf { it.isNotBlank() },
                            upiVpa.takeIf { it.isNotBlank() },
                            isGuest,
                            pickedColor,
                            dietPreference,
                            drinksAlcohol
                        )
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.obsidian,
                    contentColor = tokens.onObsidian
                ),
                modifier = Modifier.testTag("confirm_add_member_button")
            ) {
                Text(if (isGuest) "Add Guest" else "Add Friend", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ClaimGuestProfileDialog(
    guest: LedgerMember,
    sharedExpenseCount: Int,
    onDismiss: () -> Unit,
    onConfirmClaim: (verifiedName: String, verifiedEmail: String, upiVpa: String?) -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    var name by remember(guest) { mutableStateOf(guest.name) }
    var email by remember(guest) { mutableStateOf(guest.email ?: "${guest.name.lowercase().replace(" ", ".")}@example.com") }
    var upiVpa by remember(guest) { mutableStateOf(guest.upiVpa ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = tokens.cardSurface,
        title = { Text("Claim Guest Profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Convert \"${guest.name}\" into a verified SplitFlow member. All $sharedExpenseCount historical splits and balances merge seamlessly with zero duplicates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Verified display name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Verified email") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = upiVpa,
                    onValueChange = { upiVpa = it },
                    label = { Text("UPI VPA (for settlements)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && email.isNotBlank()) {
                        onConfirmClaim(name.trim(), email.trim(), upiVpa.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && email.isNotBlank(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.acidLime,
                    contentColor = tokens.onAcidLime
                ),
                modifier = Modifier.testTag("confirm_claim_guest_button")
            ) {
                Text("Verify & Merge", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
