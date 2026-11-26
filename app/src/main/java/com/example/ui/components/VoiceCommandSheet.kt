package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiRouterStatus
import com.example.ai.EngineTier
import com.example.ai.LocalVoiceParser
import com.example.ai.SplitFlowLocale
import com.example.ai.SplitFlowVoiceController
import com.example.ai.SplitFlowVoicePersona
import com.example.ai.VoicePreferenceStore
import com.example.engine.FinanceEngine
import com.example.model.AsyncUiState
import com.example.model.VoiceActionType
import com.example.model.VoiceExecutionResult
import com.example.ui.motion.SpringSurface
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandSheet(
    snapshot: LedgerSnapshot,
    voiceState: AsyncUiState<VoiceExecutionResult>,
    aiRouterStatus: AiRouterStatus,
    activeByokKeyCount: Int,
    voiceController: SplitFlowVoiceController,
    voicePreferenceStore: VoicePreferenceStore,
    initialGroupId: String?,
    autoStartListening: Boolean = false,
    onSubmitTranscript: (transcript: String, groupId: String?, autoApply: Boolean, fromSpeech: Boolean, locale: SplitFlowLocale) -> Unit,
    onConfirmPending: (result: VoiceExecutionResult, overrideTitle: String?, overrideGroupId: String?) -> Unit,
    onUndoApplied: (VoiceExecutionResult) -> Unit,
    onReset: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val voiceSettings by voicePreferenceStore.settings.collectAsStateWithLifecycle()
    val controllerState by voiceController.state.collectAsStateWithLifecycle()

    var selectedGroupId by remember(initialGroupId) { mutableStateOf(initialGroupId) }
    var autoApply by remember { mutableStateOf(true) }
    var typedInput by remember { mutableStateOf("") }
    var lastSpokenResultId by remember { mutableStateOf<String?>(null) }
    var showPersonaRow by remember { mutableStateOf(false) }

    val fallbackSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            val best = LocalVoiceParser.pickBestTranscript(matches, snapshot.members)
            if (best.isNotBlank()) {
                typedInput = best
                onSubmitTranscript(best, selectedGroupId, autoApply, true, voiceSettings.locale)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startCommandListening()
        }
    }

    fun startMicWithPermissionCheck() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.startCommandListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Bind active sheet command callback to controller while open
    DisposableEffect(voiceController, selectedGroupId, autoApply, voiceSettings.locale) {
        voiceController.updateKnownMembers(snapshot.members)
        voiceController.setCallbacks(
            onCommand = { spoken ->
                typedInput = spoken
                onSubmitTranscript(spoken, selectedGroupId, autoApply, true, voiceSettings.locale)
            },
            onFallbackDialog = { intent ->
                runCatching { fallbackSpeechLauncher.launch(intent) }
            }
        )
        onDispose {
            voiceController.stopCommandListening()
        }
    }

    // Auto-start active dictation if launched via "Hey SplitFlow" wake trigger
    LaunchedEffect(autoStartListening) {
        if (autoStartListening) {
            startMicWithPermissionCheck()
        }
    }

    // Speak natural confirmation via selected persona (Diya, Aarav, Riya, Kabir)
    LaunchedEffect(voiceState) {
        if (voiceState is AsyncUiState.Success) {
            val res = voiceState.data
            val key = "${res.savedExpenseId}_${res.savedSettlementId}_${res.parsed.transcript}"
            if (key != lastSpokenResultId) {
                lastSpokenResultId = key
                val spokenText = res.parsed.assistantReply.ifBlank { res.parsed.ledgerSummaryLine }
                if (spokenText.isNotBlank()) {
                    voiceController.speak(spokenText)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            voiceController.stopCommandListening()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("voice_command_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title + Soundbox + API Keys + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice Split Assistant",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Multilingual Native Speech · ${voiceSettings.persona.displayName} (${voiceSettings.locale.shortBadge})",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = !voiceSettings.soundboxEnabled
                            voicePreferenceStore.setSoundboxEnabled(next)
                            if (!next) voiceController.stopSpeaking()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("voice_soundbox_toggle")
                    ) {
                        Icon(
                            imageVector = if (voiceSettings.soundboxEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = if (voiceSettings.soundboxEnabled) "Mute spoken replies" else "Unmute spoken replies",
                            tint = if (voiceSettings.soundboxEnabled) tokens.positive else tokens.inkMuted
                        )
                    }
                    Surface(
                        onClick = onOpenApiKeySettings,
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.aiContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("voice_open_api_keys_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = "Configure API Keys",
                                tint = tokens.aiAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (activeByokKeyCount > 0) "Keys ($activeByokKeyCount)" else "API Keys",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.aiAccent
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            voiceController.stopCommandListening()
                            onDismiss()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close voice assistant")
                    }
                }
            }

            // Locale Selector Chips (EN, हिन्दी, मराठी, தமிழ்) + Persona Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SplitFlowLocale.entries.forEach { loc ->
                        FilterChip(
                            selected = voiceSettings.locale == loc,
                            onClick = { voicePreferenceStore.setLocale(loc) },
                            label = { Text(loc.shortBadge) },
                            modifier = Modifier.testTag("voice_locale_${loc.name.lowercase()}")
                        )
                    }
                }
                FilterChip(
                    selected = showPersonaRow,
                    onClick = { showPersonaRow = !showPersonaRow },
                    leadingIcon = {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(voiceSettings.persona.displayName) },
                    modifier = Modifier.testTag("voice_persona_toggle")
                )
            }

            // Expandable Persona Picker (Diya, Aarav, Riya, Kabir)
            AnimatedVisibility(visible = showPersonaRow) {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice Persona (tap to preview)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (controllerState.ttsOnDeviceVoice) "On-device TTS" else "System TTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.positive
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SplitFlowVoicePersona.entries.forEach { persona ->
                                FilterChip(
                                    selected = voiceSettings.persona == persona,
                                    onClick = {
                                        voicePreferenceStore.setPersona(persona)
                                        voiceController.updateSettings(voiceSettings.copy(persona = persona))
                                        voiceController.previewCurrentPersona()
                                    },
                                    label = { Text("${persona.displayName} · ${persona.styleLabel}") },
                                    modifier = Modifier.testTag("voice_persona_${persona.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // Hands-Free "Hey SplitFlow" Standby Toggle + Engine Status Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.elevatedSurface,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hearing,
                                contentDescription = null,
                                tint = if (voiceSettings.wakeWordEnabled) tokens.positive else tokens.inkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Hands-free \"Hey SplitFlow\"",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = if (voiceSettings.wakeWordEnabled) "Active in foreground · pauses while speaking"
                                    else "Wake hands-free while app is open",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.inkMuted
                                )
                            }
                        }
                        Switch(
                            checked = voiceSettings.wakeWordEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        voicePreferenceStore.setWakeWordEnabled(true)
                                        voiceController.setWakeStandbyEnabled(true)
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    voicePreferenceStore.setWakeWordEnabled(false)
                                    voiceController.setWakeStandbyEnabled(false)
                                }
                            },
                            modifier = Modifier.testTag("voice_wake_word_switch")
                        )
                    }

                    // Active AI Engine Tier Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeColor = when (aiRouterStatus.lastVoiceTier) {
                            EngineTier.ON_DEVICE_EDGE -> tokens.positive
                            EngineTier.BYOK_ROUND_ROBIN -> tokens.aiAccent
                            EngineTier.FIREBASE_GEMINI_PRIMARY -> tokens.aiAccent
                            EngineTier.FIREBASE_GEMINI_LITE -> tokens.warning
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = aiRouterStatus.lastVoiceEngineLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = aiRouterStatus.lastVoiceDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Target Scope Chips (Personal / Groups) + Auto-Apply toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("Personal") }
                    )
                    snapshot.groups.take(3).forEach { grp ->
                        FilterChip(
                            selected = selectedGroupId == grp.id,
                            onClick = { selectedGroupId = grp.id },
                            label = { Text(grp.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Auto-save", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    Switch(
                        checked = autoApply,
                        onCheckedChange = { autoApply = it },
                        modifier = Modifier.testTag("voice_auto_apply_switch")
                    )
                }
            }

            // Central Mic Orb + Live Waveform + Live Partial Transcript
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpringSurface(
                        onClick = {
                            if (controllerState.isListening) {
                                voiceController.stopCommandListening()
                            } else {
                                onReset()
                                startMicWithPermissionCheck()
                            }
                        },
                        shape = CircleShape,
                        color = if (controllerState.isListening) tokens.negative else tokens.obsidian,
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("voice_mic_orb_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (controllerState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = if (controllerState.isListening) "Stop listening" else "Start voice dictation",
                                tint = if (controllerState.isListening) MaterialTheme.colorScheme.onError else tokens.onObsidian,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    AudioWaveformVisualizer(
                        isListening = controllerState.isListening || controllerState.isSpeaking,
                        micAmplitude = if (controllerState.isListening) controllerState.audioLevel.coerceAtLeast(0.2f) else 0.35f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )

                    Text(
                        text = when {
                            controllerState.partialTranscript.isNotBlank() -> "\"${controllerState.partialTranscript}\""
                            controllerState.isListening -> controllerState.statusNotice ?: "Listening (${voiceSettings.locale.displayName})..."
                            controllerState.isSpeaking -> "${voiceSettings.persona.displayName} is speaking..."
                            else -> voiceSettings.locale.promptHint
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (controllerState.partialTranscript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (controllerState.partialTranscript.isNotBlank()) MaterialTheme.colorScheme.onSurface else tokens.inkMuted
                    )

                    if (controllerState.errorMessage != null) {
                        Text(
                            text = controllerState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.negative
                        )
                    }
                    if (controllerState.isMediaVolumeMuted) {
                        Text(
                            text = "Media volume is muted — turn up volume to hear ${voiceSettings.persona.displayName}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.warning
                        )
                    }
                }
            }

            // Quick Example Chips
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Tanmay owes me 500 rupees",
                    "Split 2000 with Tanmay 70 30",
                    "Dinner 1800 with Riya and Kabir",
                    "Tanmay paid me back 500"
                ).forEach { sample ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.cardSurface,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                typedInput = sample
                                onSubmitTranscript(sample, selectedGroupId, autoApply, false, voiceSettings.locale)
                            }
                    ) {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Manual Typed Command Input + Send Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = typedInput,
                    onValueChange = { typedInput = it },
                    placeholder = { Text("Or type: Tanmay owes me 500...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_command_text_input")
                )
                Button(
                    onClick = {
                        if (typedInput.isNotBlank()) {
                            onSubmitTranscript(typedInput, selectedGroupId, autoApply, false, voiceSettings.locale)
                        }
                    },
                    enabled = typedInput.isNotBlank() && voiceState !is AsyncUiState.Loading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_command_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send command")
                }
            }

            // Result / Loading / Error Card
            when (voiceState) {
                is AsyncUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.aiContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Parsing intent via Edge / Multi-Provider AI...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is AsyncUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.negativeContainer,
                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = voiceState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.negative
                            )
                            OutlinedButton(onClick = onReset) {
                                Text("Try Again")
                            }
                        }
                    }
                }

                is AsyncUiState.Success -> {
                    val result = voiceState.data
                    val parsed = result.parsed
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.positiveContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, tokens.positive.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_result_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (result.applied) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = tokens.positive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = when (parsed.actionType) {
                                            VoiceActionType.DIRECT_IOU -> "Direct IOU (100%)"
                                            VoiceActionType.SETTLEMENT -> "Settlement"
                                            VoiceActionType.SHARED_EXPENSE -> "${parsed.splitType.label} Split"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.positive
                                    )
                                }
                                TabularCurrencyText(
                                    minorUnits = parsed.totalMinorUnits,
                                    currency = parsed.currency,
                                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Text(
                                text = parsed.ledgerSummaryLine.ifBlank { parsed.title },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = parsed.aiConfidenceNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted
                            )

                            parsed.participants.forEach { p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = p.memberName,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "${FinanceEngine.formatMinorUnits(p.amountMinorUnits, parsed.currency)} (${p.percentageBps / 100}%)",
                                        style = TabularSmallStyle
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (result.applied) {
                                    OutlinedButton(
                                        onClick = { onUndoApplied(result) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("voice_undo_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Undo")
                                    }
                                    Button(
                                        onClick = {
                                            typedInput = ""
                                            onReset()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Next Command")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onReset,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Discard")
                                    }
                                    Button(
                                        onClick = { onConfirmPending(result, null, result.resolvedGroupId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.positive),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("voice_confirm_button")
                                    ) {
                                        Text("Confirm & Save")
                                    }
                                }
                            }
                        }
                    }
                }

                AsyncUiState.Idle -> Unit
            }
        }
    }
}
