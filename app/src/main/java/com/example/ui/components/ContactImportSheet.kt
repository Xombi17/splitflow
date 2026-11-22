package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.DeviceContact
import com.example.data.DeviceContactsLoader
import com.example.model.LedgerMember
import com.example.ui.theme.SplitFlowThemeTokens

private val ContactAvatarPalette = listOf(
    "#0E6F4E",
    "#2C4C7E",
    "#B45333",
    "#C5962C",
    "#5B4B8A",
    "#1F6E78"
)

/**
 * Selective Phone Contacts Import Sheet.
 * Requests Android READ_CONTACTS permission at runtime and lets users search and pick
 * only the specific friends they want to import into SplitFlow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactImportSheet(
    existingMembers: List<LedgerMember>,
    onImportContacts: (List<DeviceContact>) -> Unit,
    onAddManuallyInstead: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            permissionDeniedOnce = true
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoading = true
            deviceContacts = DeviceContactsLoader.loadContacts(context)
            isLoading = false
        }
    }

    val existingNameSet = remember(existingMembers) {
        existingMembers.map { it.name.trim().lowercase() }.toSet()
    }
    val existingEmailSet = remember(existingMembers) {
        existingMembers.mapNotNull { it.email?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }.toSet()
    }

    val filteredContacts = remember(deviceContacts, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            deviceContacts
        } else {
            deviceContacts.filter { c ->
                c.displayName.lowercase().contains(q) ||
                    (c.phoneNumber?.lowercase()?.contains(q) == true) ||
                    (c.email?.lowercase()?.contains(q) == true)
            }
        }
    }

    val selectedContacts = remember(deviceContacts, selectedIds.toList()) {
        val idSet = selectedIds.toSet()
        deviceContacts.filter { it.id in idSet }
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
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag("contact_import_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Contacts,
                            contentDescription = null,
                            tint = tokens.onAcidLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Import from Contacts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Pick only the friends you want to split with",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_contact_import_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close contacts import")
                }
            }

            if (!hasPermission) {
                // Permission Primer / Denied Recovery Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HairlineSection(shape = RoundedCornerShape(24.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(tokens.elevatedSurface)
                                    .border(1.dp, tokens.hairline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Contacts,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Text(
                                text = if (permissionDeniedOnce) {
                                    "Contacts permission is turned off"
                                } else {
                                    "Pick friends straight from your phone"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = if (permissionDeniedOnce) {
                                    "Allow Contacts access to browse and select specific friends, or open Android Settings if you selected \"Don't allow\"."
                                } else {
                                    "Grant read-only access to browse your contacts here. SplitFlow never bulk-uploads your address book — only the friends you select are imported."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = tokens.positiveContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = tokens.positive,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Selective import only · Read-only on device",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.positive
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.acidLime,
                                    contentColor = tokens.onAcidLime
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("grant_contacts_permission_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Contacts,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (permissionDeniedOnce) "Allow Contacts Access" else "Continue & Allow Contacts",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (permissionDeniedOnce) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                        runCatching { context.startActivity(intent) }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_contacts_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open App Settings", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            OutlinedButton(
                                onClick = onAddManuallyInstead,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("contacts_fallback_manual_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Add Friend Manually Instead", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            } else {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, phone, or email...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search contacts",
                            tint = tokens.inkMuted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.cardSurface,
                        unfocusedContainerColor = tokens.cardSurface,
                        focusedBorderColor = tokens.obsidian,
                        unfocusedBorderColor = tokens.hairline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contacts_search_input")
                )

                // Selected Contacts Pill Strip
                AnimatedVisibility(
                    visible = selectedContacts.isNotEmpty(),
                    enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SELECTED (${selectedContacts.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.inkMuted
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.negative,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedIds.clear() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedContacts, key = { it.id }) { contact ->
                                Surface(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedIds.remove(contact.id)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.obsidian
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = contact.displayName.split(" ").first(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = tokens.onObsidian
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove ${contact.displayName}",
                                            tint = tokens.onObsidian.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Contacts List Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = tokens.obsidian,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(
                                    text = "Loading contacts...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }

                        deviceContacts.isEmpty() -> {
                            HairlineSection(
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Contacts,
                                        contentDescription = null,
                                        tint = tokens.inkMuted,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "No contacts found on this device",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Your phone's Contacts list is currently empty. You can still add friends manually in seconds.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = onAddManuallyInstead,
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = tokens.acidLime,
                                            contentColor = tokens.onAcidLime
                                        ),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Friend Manually", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        filteredContacts.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No matches for \"$searchQuery\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Try a different name or add them manually.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }

                        else -> {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = tokens.cardSurface,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LazyColumn(
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                    modifier = Modifier.testTag("device_contacts_list")
                                ) {
                                    itemsIndexed(
                                        items = filteredContacts,
                                        key = { _, item -> item.id }
                                    ) { index, contact ->
                                        val alreadyAdded = contact.displayName.trim().lowercase() in existingNameSet ||
                                            (contact.email != null && contact.email.trim().lowercase() in existingEmailSet)
                                        val isSelected = contact.id in selectedIds
                                        val colorHex = ContactAvatarPalette[kotlin.math.abs(contact.displayName.hashCode()) % ContactAvatarPalette.size]

                                        ContactSelectableRow(
                                            contact = contact,
                                            avatarColorHex = colorHex,
                                            isSelected = isSelected,
                                            alreadyAdded = alreadyAdded,
                                            onToggle = {
                                                if (!alreadyAdded) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (isSelected) {
                                                        selectedIds.remove(contact.id)
                                                    } else {
                                                        selectedIds.add(contact.id)
                                                    }
                                                }
                                            }
                                        )

                                        if (index < filteredContacts.lastIndex) {
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
                }

                // Sticky Bottom Import Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onAddManuallyInstead,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("contacts_sheet_manual_button")
                    ) {
                        Text(
                            text = "Manual",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = {
                            if (selectedContacts.isNotEmpty()) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onImportContacts(selectedContacts)
                            }
                        },
                        enabled = selectedContacts.isNotEmpty(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.acidLime,
                            contentColor = tokens.onAcidLime,
                            disabledContainerColor = tokens.elevatedSurface,
                            disabledContentColor = tokens.inkMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("confirm_import_contacts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (selectedContacts.size) {
                                0 -> "Select Friends to Import"
                                1 -> "Import 1 Friend"
                                else -> "Import ${selectedContacts.size} Friends"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSelectableRow(
    contact: DeviceContact,
    avatarColorHex: String,
    isSelected: Boolean,
    alreadyAdded: Boolean,
    onToggle: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.88f,
        animationSpec = SplitFlowMotion.TactilePress,
        label = "contact_check_scale"
    )
    val checkBg by animateColorAsState(
        targetValue = when {
            alreadyAdded -> tokens.positiveContainer
            isSelected -> tokens.acidLime
            else -> tokens.elevatedSurface
        },
        animationSpec = tween(180),
        label = "contact_check_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tactileClickable(enabled = !alreadyAdded, pressedScale = 0.985f, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("contact_row_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(parseHexColor(avatarColorHex)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.initials,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = androidx.compose.ui.graphics.Color.White
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (alreadyAdded) tokens.inkMuted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.subtitle.isNotBlank()) {
                Text(
                    text = contact.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (alreadyAdded) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = tokens.positiveContainer
            ) {
                Text(
                    text = "Added",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.positive,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(checkScale)
                    .clip(CircleShape)
                    .background(checkBg)
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = if (isSelected) tokens.acidLime else tokens.hairline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = tokens.onAcidLime,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
