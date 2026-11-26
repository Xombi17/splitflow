package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.heightIn
import com.example.R
import com.example.ai.AiRouterStatus
import com.example.ai.EngineTier
import com.example.data.ReceiptPhotoStore
import com.example.engine.FinanceEngine
import com.example.model.AsyncUiState
import com.example.model.ExpenseRecord
import com.example.model.LineItemTag
import com.example.model.ParsedReceipt
import com.example.model.PayerEntry
import com.example.model.ReceiptLineItem
import com.example.model.SplitType
import com.example.ui.motion.SpringButton
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.ui.theme.TabularTitleStyle
import com.example.viewmodel.LedgerSnapshot
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseLensSheet(
    snapshot: LedgerSnapshot,
    lensState: AsyncUiState<ParsedReceipt>,
    aiRouterStatus: AiRouterStatus,
    initialGroupId: String?,
    onAnalyzeBitmap: (Bitmap, Boolean) -> Unit,
    onResetLens: () -> Unit,
    onSaveExpense: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId ?: snapshot.groups.firstOrNull()?.id) }
    var paidByMemberId by remember { mutableStateOf(snapshot.selfMember?.id ?: snapshot.members.firstOrNull()?.id.orEmpty()) }
    var showAssignmentOptions by remember { mutableStateOf(false) }

    val availableMembers = remember(selectedGroupId, snapshot.groups, snapshot.members) {
        val grp = snapshot.groups.firstOrNull { it.id == selectedGroupId }
        if (grp != null) {
            val ids = grp.memberIds.toSet()
            snapshot.members.filter { it.id in ids }.ifEmpty { snapshot.members }
        } else {
            snapshot.members
        }
    }

    val editableItems = remember { mutableStateListOf<ReceiptLineItem>() }
    LaunchedEffect(lensState) {
        if (lensState is AsyncUiState.Success) {
            editableItems.clear()
            editableItems.addAll(lensState.data.lineItems)
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            previewBitmap = bmp
            onAnalyzeBitmap(bmp, false)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val loaded = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()
            if (loaded != null) {
                previewBitmap = loaded
                onAnalyzeBitmap(loaded, false)
            }
        }
    }

    val parsedReceipt = (lensState as? AsyncUiState.Success)?.data
    val breakdown = remember(parsedReceipt, editableItems.toList()) {
        parsedReceipt?.let { parsed ->
            FinanceEngine.computeItemizedBreakdown(
                lineItems = editableItems,
                taxMinor = parsed.taxMinor,
                serviceChargeMinor = parsed.serviceChargeMinor,
                discountMinor = parsed.discountMinor,
                expectedGrandTotalMinor = parsed.grandTotalMinor
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("expense_lens_sheet")
        ) {
            // Top Bar: Back Arrow + Centered/Clean "AI Receipt Lens" Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close receipt lens",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "AI Receipt Lens",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Scrollable Content Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Bar: Left Avatar + Right Emerald "Matched" Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        MemberAvatar(
                            member = snapshot.selfMember,
                            size = 44.dp,
                            selected = true,
                            showGuestDot = false
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(tokens.positive)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )
                    }

                    if (parsedReceipt != null && breakdown != null) {
                        val isMatched = breakdown.isReconciled && breakdown.unassignedItemsCount == 0
                        val matchedBg = when {
                            !isMatched -> tokens.warningContainer
                            tokens.isDark -> tokens.positiveContainer
                            else -> tokens.positive
                        }
                        val matchedInk = when {
                            !isMatched -> tokens.warning
                            tokens.isDark -> tokens.positive
                            else -> Color.White
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = matchedBg,
                            border = if (isMatched && tokens.isDark) {
                                BorderStroke(1.dp, tokens.positive.copy(alpha = 0.30f))
                            } else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isMatched) "Matched" else "${breakdown.unassignedItemsCount} unassigned",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = matchedInk
                                )
                                TabularCurrencyText(
                                    minorUnits = parsedReceipt.grandTotalMinor,
                                    currency = parsedReceipt.currency,
                                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                                    color = if (!isMatched) MaterialTheme.colorScheme.onSurface else matchedInk
                                )
                            }
                        }
                    } else {
                        AiBadge(label = "ML Kit + Gemini")
                    }
                }

                // Hybrid Edge ML Kit + Gemini Vision Status Strip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.elevatedSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val badgeColor = when (aiRouterStatus.lastOcrTier) {
                                EngineTier.ON_DEVICE_EDGE -> tokens.positive
                                EngineTier.BYOK_ROUND_ROBIN, EngineTier.FIREBASE_GEMINI_PRIMARY -> tokens.aiAccent
                                EngineTier.FIREBASE_GEMINI_LITE -> tokens.warning
                            }
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = aiRouterStatus.lastOcrEngineLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = aiRouterStatus.lastOcrDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            previewBitmap?.let { bmp ->
                                SpringButton(
                                    onClick = { onAnalyzeBitmap(bmp, true) },
                                    enabled = lensState !is AsyncUiState.Loading,
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.obsidian,
                                        contentColor = tokens.onObsidian
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("lens_cloud_ai_enhance_button")
                                ) {
                                    Text(
                                        text = "Cloud AI Enhance",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                // Capture / Sample Triggers (Matte Obsidian + Hairline White pills)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.obsidian,
                            contentColor = tokens.onObsidian
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lens_pick_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Receipt", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching { cameraCaptureLauncher.launch(null) }
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = tokens.cardSurface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lens_take_photo_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Take Photo", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Compact Receipt Thumbnail Preview
                previewBitmap?.let { bmp ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, tokens.hairline),
                        color = tokens.cardSurface
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected receipt preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // State Machine
                when (lensState) {
                    is AsyncUiState.Idle -> {
                        EditorialCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ready to scan any restaurant or store bill",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Pick a receipt from your gallery or snap a photo to extract line items with proportional tax & service charge, then tap avatars to assign each item.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is AsyncUiState.Loading -> {
                        EditorialCard(modifier = Modifier.fillMaxWidth()) {
                            AiShimmerSkeleton(
                                label = "Reading line items, GST & service charge...",
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }

                    is AsyncUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = tokens.negativeContainer,
                            border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = lensState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.negative
                                )
                                OutlinedButton(
                                    onClick = onResetLens,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }

                    is AsyncUiState.Success -> {
                        val parsed = lensState.data
                        val safeBreakdown = breakdown ?: return@Column
                        val taxAndServiceMinor = parsed.taxMinor + parsed.serviceChargeMinor

                        // Single Crisp Editorial White Receipt Card (matching Mockup 3 Left)
                        HairlineSection(shape = RoundedCornerShape(24.dp)) {
                            // Merchant Header Row + Tune Options Trigger
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${parsed.merchantName} •",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (taxAndServiceMinor > 0L) {
                                            "GST & service auto-prorated · ${parsed.date}"
                                        } else {
                                            "${parsed.category.label} · ${parsed.date}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { showAssignmentOptions = !showAssignmentOptions },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Receipt scope and payer options",
                                        tint = if (showAssignmentOptions) tokens.positive else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Collapsible Group / Payer / Memory Drawer inside card
                            if (showAssignmentOptions) {
                                HorizontalDivider(color = tokens.hairline)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(tokens.elevatedSurface.copy(alpha = 0.55f))
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Assign to Group", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = selectedGroupId == null,
                                                onClick = { selectedGroupId = null },
                                                label = { Text("Personal") }
                                            )
                                            snapshot.groups.forEach { grp ->
                                                FilterChip(
                                                    selected = selectedGroupId == grp.id,
                                                    onClick = { selectedGroupId = grp.id },
                                                    label = { Text(grp.name) }
                                                )
                                            }
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Paid By", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            availableMembers.forEach { m ->
                                                FilterChip(
                                                    selected = paidByMemberId == m.id,
                                                    onClick = { paidByMemberId = m.id },
                                                    label = { Text(m.name.split(" ").first()) }
                                                )
                                            }
                                        }
                                    }
                                    val activeGroupMemory = snapshot.groups.firstOrNull { it.id == selectedGroupId }?.splitMemoryNotes
                                    GroupMemoryChip(memoryNotes = activeGroupMemory)
                                }
                            }

                            HorizontalDivider(color = tokens.hairline)

                            // Compact Quick Action Strip: Auto-Split Veg & Bar + Assign All
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        for (i in editableItems.indices) {
                                            val item = editableItems[i]
                                            editableItems[i] = item.copy(
                                                assignedMemberIds = autoAssignMembersForTag(
                                                    tag = item.tagEnum,
                                                    activeMembers = availableMembers
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.positiveContainer,
                                    modifier = Modifier.testTag("lens_auto_diet_split_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = tokens.positive,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Auto-Split Veg & Bar",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = tokens.positive
                                        )
                                    }
                                }
                                Text(
                                    text = "Assign All",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.inkMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val allIds = availableMembers.map { it.id }
                                            for (i in editableItems.indices) {
                                                editableItems[i] = editableItems[i].copy(assignedMemberIds = allIds)
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }

                            HorizontalDivider(color = tokens.hairline)

                            // Hairline-Divided Line Items with Circular Tap-to-Assign Avatar Pills
                            editableItems.forEachIndexed { idx, item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (item.quantity > 1) "${item.name} x${item.quantity}" else item.name,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            LineItemTagPill(
                                                tag = item.tagEnum,
                                                onClick = {
                                                    val allTags = LineItemTag.entries
                                                    val nextTag = allTags[(allTags.indexOf(item.tagEnum) + 1) % allTags.size]
                                                    editableItems[idx] = item.copy(itemTag = nextTag.name)
                                                }
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        TabularCurrencyText(
                                            minorUnits = item.totalPriceMinor,
                                            currency = parsed.currency,
                                            style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Circular Avatar Claim Pills + "All N" Rounded Pill
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val allAssigned = availableMembers.isNotEmpty() &&
                                            availableMembers.all { it.id in item.assignedMemberIds }

                                        availableMembers.forEach { member ->
                                            val isClaimed = member.id in item.assignedMemberIds
                                            MemberAvatar(
                                                member = member,
                                                size = 32.dp,
                                                selected = isClaimed,
                                                showGuestDot = false,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        val next = item.assignedMemberIds.toMutableList()
                                                        if (isClaimed) next.remove(member.id) else next.add(member.id)
                                                        editableItems[idx] = item.copy(assignedMemberIds = next)
                                                    }
                                            )
                                        }

                                        Surface(
                                            onClick = {
                                                editableItems[idx] = item.copy(
                                                    assignedMemberIds = if (allAssigned) emptyList() else availableMembers.map { it.id }
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (allAssigned) tokens.obsidian else tokens.elevatedSurface,
                                            border = if (allAssigned) null else BorderStroke(1.dp, tokens.hairline),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "All ${availableMembers.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (allAssigned) tokens.onObsidian else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                if (idx < editableItems.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = tokens.hairline
                                    )
                                }
                            }

                            // Recessed Proportional Tax & Service Strip at Bottom of Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.elevatedSurface)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                val taxLabel = buildString {
                                    append("Tax & Service ")
                                    append(FinanceEngine.formatMinorUnits(taxAndServiceMinor, parsed.currency))
                                    append(" • split proportionally")
                                    if (parsed.discountMinor > 0L) {
                                        append(" (")
                                        append(FinanceEngine.formatMinorUnits(-parsed.discountMinor, parsed.currency))
                                        append(" disc.)")
                                    }
                                }
                                Text(
                                    text = taxLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Compact Per-Person Prorated Breakdown Card
                        if (safeBreakdown.allocations.isNotEmpty()) {
                            HairlineSection(shape = RoundedCornerShape(20.dp)) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "PER-PERSON SHARE (INCL. TAX)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.inkMuted
                                    )
                                    safeBreakdown.allocations.forEachIndexed { idx, alloc ->
                                        val m = availableMembers.firstOrNull { it.id == alloc.memberId }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                MemberAvatar(member = m, size = 24.dp)
                                                Text(
                                                    text = m?.name ?: "Member",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            TabularCurrencyText(
                                                minorUnits = alloc.amountMinorUnits,
                                                currency = parsed.currency,
                                                style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                                                color = tokens.positive
                                            )
                                        }
                                        if (idx < safeBreakdown.allocations.lastIndex) {
                                            HorizontalDivider(color = tokens.hairline)
                                        }
                                    }
                                }
                            }
                        }

                        // Anomaly Guard (Duplicate receipt + Tax/Tip spike >25%)
                        val receiptAnomalies = remember(parsed, selectedGroupId, snapshot.expenses) {
                            FinanceEngine.detectExpenseAnomalies(
                                candidateTitle = parsed.merchantName,
                                candidateMinor = parsed.grandTotalMinor,
                                candidateCurrency = parsed.currency,
                                candidateDate = parsed.date,
                                candidateTaxMinor = parsed.taxMinor,
                                candidateServiceMinor = parsed.serviceChargeMinor,
                                existingExpenses = snapshot.expenses,
                                groupId = selectedGroupId
                            )
                        }
                        AnomalyGuardBanner(anomalies = receiptAnomalies)
                    }
                }
            }

            // Sticky Footer Bar with subtle editorial dark gradient + Acid-Lime gradient Save Split CTA
            if (parsedReceipt != null && breakdown != null) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    border = if (tokens.isDark) BorderStroke(1.dp, tokens.hairline) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(tokens.heroGradientStart, tokens.heroGradientEnd)
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.onHero.copy(alpha = 0.65f)
                            )
                            TabularCurrencyText(
                                minorUnits = parsedReceipt.grandTotalMinor,
                                currency = parsedReceipt.currency,
                                style = TabularTitleStyle.copy(fontWeight = FontWeight.Bold),
                                color = tokens.onHero
                            )
                        }

                        val canSaveSplit = breakdown.allocations.isNotEmpty() && breakdown.unassignedItemsCount == 0
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (canSaveSplit) {
                                        Brush.horizontalGradient(
                                            colors = listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                        )
                                    } else {
                                        Brush.horizontalGradient(
                                            colors = listOf(tokens.elevatedSurface, tokens.elevatedSurface)
                                        )
                                    }
                                )
                                .clickable(enabled = canSaveSplit) {
                                    val finalAllocations = if (breakdown.allocations.isNotEmpty()) {
                                        breakdown.allocations
                                    } else {
                                        FinanceEngine.distributeEqual(parsedReceipt.grandTotalMinor, availableMembers.map { it.id })
                                    }
                                    val shareCode = "lens-${UUID.randomUUID().toString().take(8)}"
                                    val savedReceiptPath = previewBitmap?.let { bmp ->
                                        ReceiptPhotoStore.saveBitmap(context, bmp)
                                    }
                                    val targetBaseCurrency = snapshot.groups
                                        .firstOrNull { it.id == selectedGroupId }?.currencyEnum
                                        ?: snapshot.defaultCurrency
                                    val fxMicros = if (parsedReceipt.currency != targetBaseCurrency) {
                                        FinanceEngine.getFxRateMicros(parsedReceipt.currency, targetBaseCurrency)
                                    } else null
                                    val record = ExpenseRecord(
                                        groupId = selectedGroupId,
                                        title = parsedReceipt.merchantName,
                                        notes = "Itemized via AI Expense Lens (${editableItems.size} items)",
                                        category = parsedReceipt.category.name,
                                        currency = parsedReceipt.currency.name,
                                        totalMinorUnits = parsedReceipt.grandTotalMinor,
                                        splitType = SplitType.ITEMIZED.name,
                                        dateLabel = parsedReceipt.date,
                                        shareCode = shareCode,
                                        aiExtracted = true,
                                        aiConfidenceNote = "AI Expense Lens extracted ${editableItems.size} items with proportional tax & service charge",
                                        settled = false,
                                        payers = listOf(PayerEntry(paidByMemberId, parsedReceipt.grandTotalMinor)),
                                        allocations = finalAllocations,
                                        lineItems = editableItems.toList(),
                                        taxMinorUnits = parsedReceipt.taxMinor,
                                        serviceChargeMinorUnits = parsedReceipt.serviceChargeMinor,
                                        discountMinorUnits = parsedReceipt.discountMinor,
                                        receiptPhotoPath = savedReceiptPath,
                                        fxRateToBaseMicros = fxMicros
                                    )
                                    onSaveExpense(record)
                                }
                                .padding(horizontal = 24.dp)
                                .testTag("lens_save_split_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Save Split",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (canSaveSplit) tokens.onAcidLime else tokens.onHero.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
