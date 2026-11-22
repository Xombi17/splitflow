package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.model.GroupCategory
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringPhysics
import com.example.ui.motion.SpringPreset
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.SpringVisibilityStyle
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.viewmodel.LedgerSnapshot

private data class GroupQuickPreset(
    val title: String,
    val category: GroupCategory,
    val accentHex: String
)

private val GroupQuickPresets = listOf(
    GroupQuickPreset("Weekend Trip", GroupCategory.TRIP, "#0E6F4E"),
    GroupQuickPreset("Flatmates", GroupCategory.HOME, "#2C4C7E"),
    GroupQuickPreset("Dinner Squad", GroupCategory.EVENT, "#B45333"),
    GroupQuickPreset("Road Trip", GroupCategory.TRIP, "#0284C7"),
    GroupQuickPreset("House Party", GroupCategory.EVENT, "#5B4B8A"),
    GroupQuickPreset("Couple Ledger", GroupCategory.COUPLE, "#C5962C")
)

private fun categoryIcon(category: GroupCategory): ImageVector = when (category) {
    GroupCategory.TRIP -> Icons.Rounded.Flight
    GroupCategory.HOME -> Icons.Rounded.Home
    GroupCategory.EVENT -> Icons.Rounded.Celebration
    GroupCategory.COUPLE -> Icons.Rounded.Favorite
    GroupCategory.PERSONAL -> Icons.Rounded.Person
}

private fun categoryDefaultAccent(category: GroupCategory): String = when (category) {
    GroupCategory.TRIP -> "#0E6F4E"
    GroupCategory.HOME -> "#2C4C7E"
    GroupCategory.EVENT -> "#B45333"
    GroupCategory.COUPLE -> "#C5962C"
    GroupCategory.PERSONAL -> "#5B4B8A"
}

/**
 * Tactile Warm Obsidian & Sand Group Creator Bottom Sheet:
 * - Live group preview banner reflecting chosen name, category, currency & selected members
 * - 1-tap quick templates (Weekend Trip, Flatmates, Dinner Squad, etc.)
 * - Crisp high-contrast inputs with clear borders & keyboard-safe layout
 * - All-friends avatar chip selector + inline quick-add friend + direct Phone Contacts import
 * - Collapsible Currency (non-overflowing 5-pill selector) & optional Budget Cap
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupSheet(
    snapshot: LedgerSnapshot,
    isGuestMode: Boolean,
    initialCategory: GroupCategory = GroupCategory.TRIP,
    initialSuggestedName: String? = null,
    onQuickAddMember: (name: String, onAdded: (String) -> Unit) -> Unit,
    onOpenImportContacts: () -> Unit,
    onCreateGroup: (
        name: String,
        category: GroupCategory,
        currency: CurrencyCode,
        memberIds: List<String>,
        accentColorHex: String,
        budgetMinorUnits: Long?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(initialSuggestedName) { mutableStateOf(initialSuggestedName.orEmpty()) }
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var accentHex by remember(initialCategory) { mutableStateOf(categoryDefaultAccent(initialCategory)) }
    var currency by remember { mutableStateOf(snapshot.defaultCurrency) }
    var budgetText by remember { mutableStateOf("") }
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var inlineFriendName by remember { mutableStateOf("") }

    val selectedMembers = remember {
        mutableStateListOf<String>().apply {
            snapshot.selfMember?.id?.let { add(it) }
        }
    }

    // Auto-select any friends newly imported via Contacts while this sheet is open
    val knownMemberIds = remember { mutableStateListOf<String>().apply { addAll(snapshot.members.map { it.id }) } }
    LaunchedEffect(snapshot.members) {
        val newlyAdded = snapshot.members.filter { it.id !in knownMemberIds }
        newlyAdded.forEach { m ->
            knownMemberIds.add(m.id)
            if (m.id !in selectedMembers) {
                selectedMembers.add(m.id)
            }
        }
        snapshot.selfMember?.id?.let { selfId ->
            if (selfId !in selectedMembers) selectedMembers.add(0, selfId)
        }
    }

    val otherFriends = remember(snapshot.members) { snapshot.members.filter { !it.selfProfile } }
    val allSelected = otherFriends.isNotEmpty() && otherFriends.all { it.id in selectedMembers }
    val selectedMemberModels = remember(snapshot.members, selectedMembers.toList()) {
        snapshot.members.filter { it.id in selectedMembers }
    }

    val submitInlineFriend: () -> Unit = {
        val trimmed = inlineFriendName.trim()
        if (trimmed.isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            inlineFriendName = ""
            onQuickAddMember(trimmed) { newId ->
                if (newId.isNotBlank() && newId !in selectedMembers) {
                    selectedMembers.add(newId)
                }
            }
        }
    }

    val accentColor = remember(accentHex) { parseHexColor(accentHex, Color(0xFF0E6F4E)) }
    val focusedBorder = if (tokens.isDark) tokens.acidLime else tokens.obsidian

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.cardSurface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .testTag("create_group_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Create Group",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pick a template or name it, then tap who's in",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                }
                SpringIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_create_group_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close create group sheet",
                        tint = tokens.inkMuted
                    )
                }
            }

            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Live Group Card Preview Strip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tokens.elevatedSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accentColor.copy(alpha = if (tokens.isDark) 0.22f else 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accentColor.copy(alpha = if (tokens.isDark) 0.28f else 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = categoryIcon(category),
                                contentDescription = null,
                                tint = if (tokens.isDark) tokens.acidLime else accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = name.trim().ifEmpty { "Your new ${category.label.lowercase()} group" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (name.isBlank()) tokens.inkMuted else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val capMinor = FinanceEngine.parseMajorToMinor(budgetText)
                            Text(
                                text = buildString {
                                    append("${category.label} · ${currency.symbol} ${currency.name}")
                                    if (capMinor > 0L) {
                                        append(" · Cap ${FinanceEngine.formatMinorUnits(capMinor, currency)}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Live compact avatar stack
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            selectedMemberModels.take(3).forEach { member ->
                                MemberAvatar(
                                    member = member,
                                    size = 28.dp,
                                    selected = false,
                                    showGuestDot = false
                                )
                            }
                            if (selectedMemberModels.size > 3) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(tokens.obsidian),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${selectedMemberModels.size - 3}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.onObsidian
                                    )
                                }
                            }
                        }
                    }
                }

                // 1-Tap Quick Templates (full-bleed edge-to-edge LazyRow)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUICK TEMPLATES",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.inkMuted,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GroupQuickPresets) { preset ->
                            val isChosen = name.equals(preset.title, ignoreCase = true)
                            val pillBg by animateColorAsState(
                                targetValue = if (isChosen) tokens.obsidian else tokens.elevatedSurface,
                                animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
                                label = "presetBg"
                            )
                            val pillFg by animateColorAsState(
                                targetValue = if (isChosen) tokens.onObsidian else MaterialTheme.colorScheme.onSurface,
                                animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
                                label = "presetFg"
                            )
                            SpringSurface(
                                onClick = {
                                    name = preset.title
                                    category = preset.category
                                    accentHex = preset.accentHex
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = pillBg,
                                border = if (isChosen) null else BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("preset_${preset.title.lowercase().replace(" ", "_")}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(preset.category),
                                        contentDescription = null,
                                        tint = if (isChosen) tokens.onObsidian else tokens.inkMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = pillFg
                                    )
                                }
                            }
                        }
                    }
                }

                // Group Name Input
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "GROUP NAME",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.inkMuted
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = {
                            Text(
                                text = "e.g., Goa Weekend, Flat 402, Friday Dinner",
                                color = tokens.inkMuted
                            )
                        },
                        trailingIcon = if (name.isNotEmpty()) {
                            {
                                IconButton(onClick = { name = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear group name",
                                        tint = tokens.inkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = tokens.elevatedSurface,
                            unfocusedContainerColor = tokens.elevatedSurface,
                            focusedBorderColor = focusedBorder,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = focusedBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_group_name_input")
                    )
                }

                // Category Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CATEGORY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.inkMuted,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GroupCategory.entries) { cat ->
                            val selected = category == cat
                            SpringSurface(
                                onClick = {
                                    category = cat
                                    accentHex = categoryDefaultAccent(cat)
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) tokens.obsidian else tokens.elevatedSurface,
                                border = if (selected) null else BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (selected) tokens.onObsidian else tokens.inkMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (selected) tokens.onObsidian else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // People Picker + Inline Instant Add + Contacts Import
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)
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
                            Text(
                                text = "PEOPLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.inkMuted
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.elevatedSurface,
                                border = BorderStroke(1.dp, tokens.hairline)
                            ) {
                                Text(
                                    text = "${selectedMembers.size} selected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (otherFriends.isNotEmpty()) {
                                Text(
                                    text = if (allSelected) "Only me" else "Select all",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.positive,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (allSelected) {
                                                selectedMembers.removeAll { it != snapshot.selfMember?.id }
                                            } else {
                                                otherFriends.forEach { f ->
                                                    if (f.id !in selectedMembers) selectedMembers.add(f.id)
                                                }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }
                            SpringSurface(
                                onClick = onOpenImportContacts,
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.elevatedSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.testTag("create_group_import_contacts_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Contacts,
                                        contentDescription = null,
                                        tint = if (tokens.isDark) tokens.acidLime else tokens.obsidian,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Contacts",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Selectable Avatar Pills for ALL members (zero clipping, high contrast)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        snapshot.members.forEach { member ->
                            val isSelected = member.id in selectedMembers
                            val label = if (member.selfProfile) {
                                "You"
                            } else {
                                member.name.split(" ").first() + if (isGuestMode && member.guestProfile) " (Guest)" else ""
                            }
                            SpringSurface(
                                onClick = {
                                    if (!member.selfProfile) {
                                        if (isSelected) selectedMembers.remove(member.id)
                                        else selectedMembers.add(member.id)
                                    }
                                },
                                enabled = !member.selfProfile,
                                shape = RoundedCornerShape(999.dp),
                                color = if (isSelected) tokens.obsidian else tokens.elevatedSurface,
                                border = if (isSelected) null else BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("group_member_chip_${member.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    MemberAvatar(
                                        member = member,
                                        size = 26.dp,
                                        selected = false,
                                        showGuestDot = false
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) tokens.onObsidian else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = tokens.onObsidian,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Instant Inline Quick-Add Friend (matched height & crisp outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inlineFriendName,
                            onValueChange = { inlineFriendName = it },
                            placeholder = {
                                Text(
                                    text = "Add friend by name...",
                                    color = tokens.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitInlineFriend() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = tokens.elevatedSurface,
                                unfocusedContainerColor = tokens.elevatedSurface,
                                focusedBorderColor = focusedBorder,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = focusedBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_group_inline_friend_input")
                        )
                        SpringSurface(
                            onClick = submitInlineFriend,
                            enabled = inlineFriendName.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (inlineFriendName.isNotBlank()) tokens.obsidian else tokens.elevatedSurface,
                            border = if (inlineFriendName.isNotBlank()) null else BorderStroke(1.dp, tokens.hairline),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("create_group_inline_friend_add_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add friend to group",
                                    tint = if (inlineFriendName.isNotBlank()) tokens.onObsidian else tokens.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (inlineFriendName.isNotBlank()) tokens.onObsidian else tokens.inkMuted
                                )
                            }
                        }
                    }
                }

                // Collapsible Currency & Budget Cap (zero overflow on all screen widths)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = tokens.elevatedSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedOptions = !showAdvancedOptions }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("create_group_advanced_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = tokens.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = "Currency & Budget Cap",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append("${currency.symbol} ${currency.name}")
                                            val capMinor = FinanceEngine.parseMajorToMinor(budgetText)
                                            if (capMinor > 0L) {
                                                append(" · Cap ${FinanceEngine.formatMinorUnits(capMinor, currency)}")
                                            } else {
                                                append(" · Optional cap")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkMuted
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (showAdvancedOptions) "Hide options" else "Show options",
                                tint = tokens.inkMuted
                            )
                        }

                        SpringAnimatedVisibility(
                            visible = showAdvancedOptions,
                            style = SpringVisibilityStyle.EXPAND_VERTICAL,
                            label = "create_group_advanced_spring"
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HorizontalDivider(color = tokens.hairline)

                                // Equal-width 5-currency segmented row (guaranteed zero clipping)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CurrencyCode.entries.forEach { c ->
                                        val isChosen = currency == c
                                        SpringSurface(
                                            onClick = { currency = c },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isChosen) tokens.obsidian else tokens.cardSurface,
                                            border = if (isChosen) null else BorderStroke(1.dp, tokens.hairline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 7.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = c.symbol,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isChosen) tokens.onObsidian else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = c.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isChosen) tokens.onObsidian.copy(alpha = 0.78f) else tokens.inkMuted
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = budgetText,
                                    onValueChange = { budgetText = it },
                                    label = { Text("Budget cap (${currency.symbol}, optional)") },
                                    placeholder = { Text("e.g., 40000", color = tokens.inkMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = tokens.cardSurface,
                                        unfocusedContainerColor = tokens.cardSurface,
                                        focusedBorderColor = focusedBorder,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        cursorColor = focusedBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("new_group_budget_input")
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Bottom CTA Bar
            HorizontalDivider(color = tokens.hairline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardSurface)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                SpringButton(
                    onClick = {
                        if (name.isNotBlank() && selectedMembers.isNotEmpty()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val cap = FinanceEngine.parseMajorToMinor(budgetText).takeIf { it > 0L }
                            onCreateGroup(
                                name.trim(),
                                category,
                                currency,
                                selectedMembers.toList(),
                                accentHex,
                                cap
                            )
                        }
                    },
                    enabled = name.isNotBlank() && selectedMembers.isNotEmpty(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.acidLime,
                        contentColor = tokens.onAcidLime,
                        disabledContainerColor = tokens.elevatedSurface,
                        disabledContentColor = tokens.inkMuted
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    hapticFeedback = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .testTag("confirm_create_group_button")
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selectedMembers.size <= 1) {
                            "Create Group"
                        } else {
                            "Create Group · ${selectedMembers.size} People"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
