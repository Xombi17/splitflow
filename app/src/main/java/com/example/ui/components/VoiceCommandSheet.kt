package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import com.example.ai.ActiveEngineKind
import com.example.ai.LocalAiEngineState
import com.example.ai.LocalModelPhase
import com.example.ai.VoiceAudioRecorder
import com.example.engine.FinanceEngine
import com.example.model.AsyncUiState
import com.example.model.VoiceActionType
import com.example.model.VoiceExecutionResult
import com.example.ui.motion.SpringAnimatedVisibility
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringIconButton
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.motion.SpringSurface
import com.example.ui.motion.SpringVisibilityStyle
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.viewmodel.LedgerSnapshot
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceCommandSheet(
    snapshot: LedgerSnapshot,
    voiceState: AsyncUiState<VoiceExecutionResult>,
    localAiState: LocalAiEngineState,
    initialGroupId: String?,
    onInstallWhistle: (preferredSlug: String?) -> Unit,
    onProcessAudio: (wavBytes: ByteArray, targetGroupId: String?, autoApply: Boolean) -> Unit,
    onParseCommand: (transcript: String, targetGroupId: String?, autoApply: Boolean, fromDeviceSpeechFallback: Boolean) -> Unit,
    onCommitPending: (result: VoiceExecutionResult, overrideTitle: String?, overrideGroupId: String?) -> Unit,
    onUndoApplied: (result: VoiceExecutionResult) -> Unit,
    onNavigateToActivity: () -> Unit,
    onResetVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val uiScope = rememberCoroutineScope()

    val audioRecorder = remember { VoiceAudioRecorder() }
    val isWavRecording by audioRecorder.isRecording.collectAsStateWithLifecycle()
    val wavAmplitude by audioRecorder.amplitude.collectAsStateWithLifecycle()
    val elapsedSeconds by audioRecorder.elapsedSeconds.collectAsStateWithLifecycle()

    var isLiveSpeechListening by remember { mutableStateOf(false) }
    var liveSpeechAmplitude by remember { mutableStateOf(0.1f) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val isListeningActive = isWavRecording || isLiveSpeechListening
    val activeAmplitude = if (isWavRecording) wavAmplitude else liveSpeechAmplitude

    // Default to explicit initialGroupId or Personal (null) so "Tanmay owes me 500" stays 1-on-1 Personal
    var selectedGroupId by remember(initialGroupId) { mutableStateOf(initialGroupId) }
    var autoApplyToLedger by remember { mutableStateOf(true) }
    var speakRepliesAloud by remember { mutableStateOf(true) }
    var useCactusMicMode by remember { mutableStateOf(localAiState.isWhistleReady) }
    var typedCommand by remember { mutableStateOf("") }
    var micNotice by remember { mutableStateOf<String?>(null) }
    var editableTitle by remember { mutableStateOf("") }
    var showEditDetails by remember { mutableStateOf(false) }

    LaunchedEffect(localAiState.isWhistleReady) {
        if (localAiState.isWhistleReady) {
            useCactusMicMode = true
        }
    }

    // On-device TextToSpeech for spoken confirmations
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        ttsEngine = instance
        onDispose {
            audioRecorder.release()
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            instance.stop()
            instance.shutdown()
        }
    }

    // Sync editable fields, scroll result into view & speak reply when a new result arrives
    LaunchedEffect(voiceState) {
        if (voiceState is AsyncUiState.Success) {
            val res = voiceState.data
            if (res.parsed.transcript.isNotBlank()) {
                typedCommand = res.parsed.transcript
            }
            editableTitle = res.parsed.title
            showEditDetails = false
            if (speakRepliesAloud && ttsReady && res.parsed.assistantReply.isNotBlank()) {
                ttsEngine?.speak(
                    res.parsed.assistantReply,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "splitflow_ai_reply"
                )
            }
        }
    }

    val submitTypedCommand: () -> Unit = {
        val cleaned = typedCommand.trim()
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (cleaned.isBlank()) {
            micNotice = "Type a command like \"Tanmay owes me 500 rupees\" first."
        } else {
            micNotice = null
            ttsEngine?.stop()
            if (isWavRecording) audioRecorder.cancelRecording()
            if (isLiveSpeechListening) {
                runCatching { speechRecognizer?.stopListening() }
                isLiveSpeechListening = false
            }
            keyboardController?.hide()
            focusManager.clearFocus()
            onParseCommand(cleaned, selectedGroupId, autoApplyToLedger, false)
            uiScope.launch { scrollState.animateScrollTo(0) }
        }
    }

    val stopLiveSpeech: () -> Unit = {
        runCatching { speechRecognizer?.stopListening() }
        isLiveSpeechListening = false
        liveSpeechAmplitude = 0f
    }

    val startDeviceSpeechFallback: () -> Unit = {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            micNotice = "Install Cactus Whistle (~17 MB) above for offline voice, or type your command below."
        } else {
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                speechRecognizer = it
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isLiveSpeechListening = true
                    micNotice = null
                }
                override fun onBeginningOfSpeech() {
                    isLiveSpeechListening = true
                }
                override fun onRmsChanged(rmsdB: Float) {
                    liveSpeechAmplitude = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isLiveSpeechListening = false
                }
                override fun onError(error: Int) {
                    isLiveSpeechListening = false
                    liveSpeechAmplitude = 0f
                    micNotice = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        "Didn't catch that clearly — tap the mic again or type below."
                    } else {
                        "Device speech listener unavailable (code $error). Install Cactus Whistle above or type below."
                    }
                }
                override fun onResults(results: Bundle?) {
                    isLiveSpeechListening = false
                    liveSpeechAmplitude = 0f
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!heard.isNullOrBlank()) {
                        typedCommand = heard
                        onParseCommand(heard, selectedGroupId, autoApplyToLedger, true)
                    } else {
                        micNotice = "Heard nothing clearly — try speaking again or type below."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!partial.isNullOrBlank()) {
                        typedCommand = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            runCatching {
                isLiveSpeechListening = true
                recognizer.startListening(intent)
            }.onFailure {
                isLiveSpeechListening = false
                micNotice = "Could not start voice listener. Install Cactus Whistle or type below."
            }
        }
    }

    val startListeningNow: () -> Unit = {
        micNotice = null
        ttsEngine?.stop()
        onResetVoice()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (useCactusMicMode && localAiState.isWhistleReady) {
            audioRecorder.startRecording(
                onAutoStop = { wavBytes ->
                    onProcessAudio(wavBytes, selectedGroupId, autoApplyToLedger)
                },
                onError = { err ->
                    micNotice = err
                }
            )
        } else {
            startDeviceSpeechFallback()
        }
    }

    val stopAndSendAudio: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isWavRecording) {
            val wavBytes = audioRecorder.stopRecording()
            if (wavBytes != null && wavBytes.size > 44) {
                micNotice = null
                onProcessAudio(wavBytes, selectedGroupId, autoApplyToLedger)
            } else {
                micNotice = "Heard nothing clearly — speak for a second, then tap Stop."
            }
        } else if (isLiveSpeechListening) {
            stopLiveSpeech()
            if (typedCommand.isNotBlank()) {
                onParseCommand(typedCommand.trim(), selectedGroupId, autoApplyToLedger, true)
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningNow()
        } else {
            micNotice = "Microphone permission denied. You can still type any command below."
        }
    }

    val handleMicOrbTap: () -> Unit = {
        if (isListeningActive) {
            stopAndSendAudio()
        } else {
            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPermission) {
                startListeningNow()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val smartExamples = remember(snapshot.members) {
        val friendNames = snapshot.members.filter { !it.selfProfile }.map { it.name.split(" ").first() }
        val f1 = friendNames.getOrNull(0) ?: "Tanmay"
        val f2 = friendNames.getOrNull(1) ?: "Parth"
        listOf(
            "$f1 owes me 500 rupees",
            "I owe $f1 350 rupees for coffee",
            "Paid 1,800 for dinner with $f1 and $f2, split equally",
            "$f1 paid me back 500 rupees"
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "ai_voice_orb_pulse")
    val orbPulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    val focusedBorder = if (tokens.isDark) tokens.acidLime else tokens.obsidian

    ModalBottomSheet(
        onDismissRequest = {
            audioRecorder.cancelRecording()
            stopLiveSpeech()
            ttsEngine?.stop()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = tokens.cardSurface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
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
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("voice_command_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Local AI Voice Assistant + Spoken Reply Toggle + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = tokens.onAcidLime,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Local Voice Assistant",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "On-device Cactus Whistle — zero cloud rate limits",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpringIconButton(
                        onClick = {
                            speakRepliesAloud = !speakRepliesAloud
                            if (!speakRepliesAloud) ttsEngine?.stop()
                        },
                        modifier = Modifier.testTag("voice_tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (speakRepliesAloud) {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            },
                            contentDescription = if (speakRepliesAloud) "Mute voice reply" else "Unmute voice reply",
                            tint = if (speakRepliesAloud) tokens.acidLime else tokens.inkMuted
                        )
                    }
                    SpringIconButton(
                        onClick = {
                            audioRecorder.cancelRecording()
                            stopLiveSpeech()
                            ttsEngine?.stop()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close voice assistant",
                            tint = tokens.inkMuted
                        )
                    }
                }
            }

            // Cactus Whistle Local Model Control Strip + Live Engine Status Badge + Visible Error
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tokens.elevatedSurface,
                border = BorderStroke(
                    1.dp,
                    when {
                        localAiState.whistleError != null -> tokens.negative.copy(alpha = 0.45f)
                        localAiState.isWhistleReady -> tokens.positive.copy(alpha = 0.45f)
                        else -> tokens.hairline
                    }
                ),
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
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = when {
                                localAiState.whistleError != null -> tokens.negative
                                localAiState.isWhistleReady -> tokens.positive
                                else -> tokens.acidLime
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (localAiState.isWhistleReady) {
                                    "Cactus ${localAiState.whistleActiveSlug ?: "Whistle"} · Ready"
                                } else {
                                    "On-Device Speech Engine"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = localAiState.whistleNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkMuted
                            )
                        }
                        when (localAiState.whistlePhase) {
                            LocalModelPhase.READY -> {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.positiveContainer
                                ) {
                                    Text(
                                        text = "CACTUS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.positive,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            LocalModelPhase.DOWNLOADING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = tokens.acidLime
                                )
                            }
                            LocalModelPhase.NOT_DOWNLOADED, LocalModelPhase.UNAVAILABLE -> {
                                SpringButton(
                                    onClick = { onInstallWhistle(null) },
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.obsidian,
                                        contentColor = tokens.onObsidian
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 36.dp)
                                        .testTag("install_whistle_button")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (localAiState.whistlePhase == LocalModelPhase.UNAVAILABLE) "Retry" else "Whistle",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    // Selectable Cactus Voice Model Catalog Chips (switch between Whistle / Whisper Base / Small / Tiny)
                    if (localAiState.availableVoiceModels.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            localAiState.availableVoiceModels.take(4).forEach { model ->
                                val isActive = localAiState.isWhistleReady && localAiState.whistleActiveSlug == model.slug
                                val isBusy = localAiState.whistlePhase == LocalModelPhase.DOWNLOADING
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isActive) tokens.acidLime else tokens.cardSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive) tokens.acidLime else tokens.hairline
                                    ),
                                    modifier = Modifier
                                        .springClickable(enabled = !isBusy && !isActive) {
                                            onInstallWhistle(model.slug)
                                        }
                                        .testTag("voice_model_chip_${model.slug}")
                                ) {
                                    Text(
                                        text = buildString {
                                            append(model.slug)
                                            if (model.sizeMb > 0) append(" · ${model.sizeMb}MB")
                                            if (isActive) append(" ✓")
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isActive) tokens.onAcidLime else tokens.inkMuted,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Live Engine Status Badge (shows which engine actually ran on the latest voice/text command)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_live_engine_badge"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pillBg = when (localAiState.lastVoiceEngineKind) {
                            ActiveEngineKind.CACTUS_PRIMARY -> tokens.positiveContainer
                            ActiveEngineKind.LOCAL_FALLBACK -> tokens.warningContainer
                            else -> tokens.cardSurface
                        }
                        val pillInk = when (localAiState.lastVoiceEngineKind) {
                            ActiveEngineKind.CACTUS_PRIMARY -> tokens.positive
                            ActiveEngineKind.LOCAL_FALLBACK -> tokens.warning
                            else -> tokens.inkMuted
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = pillBg,
                            border = BorderStroke(1.dp, pillInk.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = localAiState.lastVoiceEngineLabel?.let { "Last run: $it" }
                                    ?: if (localAiState.isWhistleReady) "Engine ready: Cactus Whistle"
                                    else "Active fallback: Android SpeechRecognizer",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = pillInk,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        localAiState.lastVoiceDetail?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Visible Cactus Download / Init / Transcribe Error Box
                    localAiState.whistleError?.let { errText ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = tokens.negativeContainer,
                            border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("whistle_error_banner")
                        ) {
                            Text(
                                text = "Whistle error: $errText",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.negative,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // Hero AI Voice Orb & Live Microphone Waveform Stage
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = if (isListeningActive) 1.5.dp else 1.dp,
                    color = if (isListeningActive) tokens.acidLime else tokens.hairline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.voiceCapsuleGradientStart,
                                    tokens.voiceCapsuleGradientEnd
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mic Engine Switcher Pill: Cactus Offline STT vs Live Android SpeechRecognizer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cactusSelected = useCactusMicMode && localAiState.isWhistleReady
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (cactusSelected) tokens.acidLime else tokens.onHero.copy(alpha = 0.10f),
                            border = BorderStroke(
                                1.dp,
                                if (cactusSelected) tokens.acidLime else tokens.onHero.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier
                                .springClickable(enabled = !isListeningActive) {
                                    if (localAiState.isWhistleReady) {
                                        useCactusMicMode = true
                                        micNotice = null
                                    } else {
                                        onInstallWhistle(null)
                                    }
                                }
                                .testTag("mic_mode_cactus_pill")
                        ) {
                            Text(
                                text = if (localAiState.isWhistleReady) {
                                    "Cactus ${localAiState.whistleActiveSlug ?: "STT"}"
                                } else {
                                    "Download Cactus STT"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (cactusSelected) tokens.onAcidLime else tokens.onHero.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (!cactusSelected) tokens.acidLime else tokens.onHero.copy(alpha = 0.10f),
                            border = BorderStroke(
                                1.dp,
                                if (!cactusSelected) tokens.acidLime else tokens.onHero.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier
                                .springClickable(enabled = !isListeningActive) {
                                    useCactusMicMode = false
                                    micNotice = null
                                }
                                .testTag("mic_mode_device_pill")
                        ) {
                            Text(
                                text = "Live Device Mic",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (!cactusSelected) tokens.onAcidLime else tokens.onHero.copy(alpha = 0.85f),
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Pulsing AI Voice Orb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(88.dp)
                    ) {
                        if (isListeningActive) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .scale(orbPulse + activeAmplitude * 0.25f)
                                    .clip(CircleShape)
                                    .background(tokens.acidLime.copy(alpha = 0.22f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .scale(1f + activeAmplitude * 0.2f)
                                    .clip(CircleShape)
                                    .border(1.5.dp, tokens.acidLime.copy(alpha = 0.6f), CircleShape)
                            )
                        }
                        SpringSurface(
                            onClick = {
                                if (voiceState !is AsyncUiState.Loading) handleMicOrbTap()
                            },
                            shape = CircleShape,
                            color = if (isListeningActive) tokens.negative else tokens.acidLime,
                            pressedScale = 0.89f,
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("voice_dictate_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isListeningActive) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = if (isListeningActive) "Stop and transcribe" else "Tap to speak",
                                    tint = if (isListeningActive) Color.White else tokens.onAcidLime,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Live Mic-Reactive Acid-Lime Waveform
                    AudioWaveformVisualizer(
                        isListening = isListeningActive || voiceState is AsyncUiState.Loading,
                        barColor = tokens.acidLime,
                        barCount = 13,
                        micAmplitude = if (isListeningActive) activeAmplitude else null,
                        modifier = Modifier
                            .width(108.dp)
                            .height(32.dp)
                    )

                    Text(
                        text = when {
                            isWavRecording -> "Recording for Whistle... (${elapsedSeconds}s) — tap Stop"
                            isLiveSpeechListening -> "Listening locally... speak naturally"
                            voiceState is AsyncUiState.Loading -> "Transcribing locally & updating account..."
                            else -> "Tap mic or type \"Tanmay owes me 500 rupees\""
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = tokens.onHero,
                        textAlign = TextAlign.Center
                    )

                    // Active recording quick controls
                    SpringAnimatedVisibility(
                        visible = isListeningActive,
                        style = SpringVisibilityStyle.POP_SCALE,
                        label = "voice_recording_controls"
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpringOutlinedButton(
                                onClick = {
                                    audioRecorder.cancelRecording()
                                    stopLiveSpeech()
                                    micNotice = null
                                },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, tokens.onHero.copy(alpha = 0.35f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.onHero)
                            ) {
                                Text("Cancel")
                            }
                            SpringButton(
                                onClick = stopAndSendAudio,
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.acidLime,
                                    contentColor = tokens.onAcidLime
                                ),
                                modifier = Modifier.testTag("voice_stop_and_apply_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Done & Apply", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Primary Type & Send Command Bar (always visible right under the Voice Orb)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = typedCommand,
                    onValueChange = {
                        typedCommand = it
                        if (micNotice != null) micNotice = null
                    },
                    placeholder = {
                        Text(
                            text = "Type: \"Tanmay owes me 500 rupees\"",
                            color = tokens.inkMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { submitTypedCommand() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.elevatedSurface,
                        unfocusedContainerColor = tokens.elevatedSurface,
                        focusedBorderColor = focusedBorder,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = focusedBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_transcript_input")
                )
                SpringButton(
                    onClick = submitTypedCommand,
                    enabled = voiceState !is AsyncUiState.Loading,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (typedCommand.isNotBlank()) tokens.acidLime else tokens.elevatedSurface,
                        contentColor = if (typedCommand.isNotBlank()) tokens.onAcidLime else tokens.inkMuted
                    ),
                    border = if (typedCommand.isNotBlank()) null else BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("voice_parse_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send voice command",
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            if (micNotice != null) {
                Text(
                    text = micNotice.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.warning
                )
            }

            // Auto-Apply Toggle Strip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tokens.elevatedSurface,
                border = BorderStroke(1.dp, tokens.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-update account instantly",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Adds new friends & logs balances directly to Activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                    Switch(
                        checked = autoApplyToLedger,
                        onCheckedChange = { autoApplyToLedger = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = tokens.onAcidLime,
                            checkedTrackColor = tokens.acidLime
                        ),
                        modifier = Modifier.testTag("voice_auto_apply_switch")
                    )
                }
            }

            // AI Result / Loading / Error Section
            when (voiceState) {
                is AsyncUiState.Idle -> Unit

                is AsyncUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = tokens.elevatedSurface,
                        border = BorderStroke(1.dp, tokens.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AiShimmerSkeleton(
                            label = "Processing locally with Cactus Whistle...",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is AsyncUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = tokens.negativeContainer,
                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = voiceState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.negative
                            )
                            SpringOutlinedButton(
                                onClick = onResetVoice,
                                shape = RoundedCornerShape(999.dp)
                            ) { Text("Try Again") }
                        }
                    }
                }

                is AsyncUiState.Success -> {
                    val result = voiceState.data
                    val parsed = result.parsed
                    val memberMap = remember(snapshot.members) { snapshot.members.associateBy { it.id } }
                    val selfId = snapshot.selfMember?.id
                    val scopeLabel = result.resolvedGroupId?.let { id ->
                        snapshot.groups.firstOrNull { it.id == id }?.name
                    } ?: "Personal"

                    val isUserOwed = when (parsed.actionType) {
                        VoiceActionType.SETTLEMENT -> parsed.settlementToMemberId == selfId
                        VoiceActionType.DIRECT_IOU, VoiceActionType.SHARED_EXPENSE -> {
                            parsed.paidByMemberId == selfId
                        }
                    }

                    // Conversational AI Response & Applied Ledger Card
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = tokens.elevatedSurface,
                        border = BorderStroke(
                            1.dp,
                            if (result.applied) tokens.positive.copy(alpha = 0.5f) else tokens.hairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_result_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Status Pill + Scope
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (result.applied) tokens.positiveContainer else tokens.cardSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (result.applied) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (result.applied) tokens.positive else tokens.acidLime,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (result.applied) "Account Updated" else "Ready to Apply",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (result.applied) tokens.positive else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.cardSurface,
                                    border = BorderStroke(1.dp, tokens.hairline)
                                ) {
                                    Text(
                                        text = scopeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = tokens.inkMuted,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Verbatim Transcript
                            if (parsed.transcript.isNotBlank()) {
                                Text(
                                    text = "You said: \"${parsed.transcript}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted
                                )
                            }

                            // Crisp Account Impact Highlight (e.g. "Tanmay owes you ₹500")
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isUserOwed) tokens.positiveContainer else tokens.negativeContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = parsed.ledgerSummaryLine.ifBlank { parsed.title },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isUserOwed) tokens.positive else tokens.negative
                                        )
                                        Text(
                                            text = parsed.assistantReply.ifBlank { parsed.aiConfidenceNote },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }

                            // Auto-created friend notification pill (e.g. "Added Tanmay to Friends")
                            if (result.createdMemberNames.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = tokens.cardSurface,
                                    border = BorderStroke(1.dp, tokens.hairline)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonAdd,
                                            contentDescription = null,
                                            tint = tokens.acidLime,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Added ${result.createdMemberNames.joinToString(", ")} to your Friends automatically",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Breakdown details (when shared split or pending manual confirmation)
                            if (parsed.actionType == VoiceActionType.SHARED_EXPENSE && parsed.participants.size > 1) {
                                HorizontalDivider(color = tokens.hairline)
                                parsed.participants.forEach { p ->
                                    val displayName = if (p.memberId == selfId) {
                                        "You"
                                    } else {
                                        memberMap[p.memberId]?.name?.split(" ")?.firstOrNull() ?: p.memberName
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = FinanceEngine.formatMinorUnits(p.amountMinorUnits, parsed.currency),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (!result.applied && showEditDetails) {
                                OutlinedTextField(
                                    value = editableTitle,
                                    onValueChange = { editableTitle = it },
                                    label = { Text("Activity title") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = tokens.cardSurface,
                                        unfocusedContainerColor = tokens.cardSurface,
                                        focusedBorderColor = focusedBorder,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Action Row
                            if (result.applied) {
                                SpringButton(
                                    onClick = {
                                        ttsEngine?.stop()
                                        onNavigateToActivity()
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.acidLime,
                                        contentColor = tokens.onAcidLime
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("voice_view_in_activity_button")
                                ) {
                                    Text("View in Activity", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SpringOutlinedButton(
                                        onClick = {
                                            typedCommand = ""
                                            startListeningNow()
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, tokens.hairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 46.dp)
                                    ) {
                                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Speak Another")
                                    }

                                    if (result.savedExpenseId != null) {
                                        SpringOutlinedButton(
                                            onClick = {
                                                ttsEngine?.stop()
                                                onUndoApplied(result)
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.4f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.negative),
                                            modifier = Modifier
                                                .weight(0.8f)
                                                .heightIn(min = 46.dp)
                                                .testTag("voice_undo_button")
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("Undo")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SpringOutlinedButton(
                                        onClick = { showEditDetails = !showEditDetails },
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, tokens.hairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 50.dp)
                                    ) {
                                        Text(if (showEditDetails) "Hide Editor" else "Edit Title")
                                    }
                                    SpringButton(
                                        onClick = {
                                            onCommitPending(
                                                result,
                                                editableTitle.takeIf { it.isNotBlank() },
                                                result.resolvedGroupId ?: selectedGroupId
                                            )
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = tokens.acidLime,
                                            contentColor = tokens.onAcidLime
                                        ),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .heightIn(min = 50.dp)
                                            .testTag("voice_save_split_button")
                                    ) {
                                        Text("Confirm & Apply", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Target Scope Pills (Personal default + existing groups)
            if (snapshot.groups.isNotEmpty() && voiceState !is AsyncUiState.Success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEFAULT ACCOUNT SCOPE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.inkMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val personalSelected = selectedGroupId == null
                        SpringSurface(
                            onClick = { selectedGroupId = null },
                            shape = RoundedCornerShape(999.dp),
                            color = if (personalSelected) tokens.obsidian else tokens.elevatedSurface,
                            border = if (personalSelected) null else BorderStroke(1.dp, tokens.hairline)
                        ) {
                            Text(
                                text = "Personal (1-on-1)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (personalSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (personalSelected) tokens.onObsidian else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        snapshot.groups.forEach { grp ->
                            val isSelected = selectedGroupId == grp.id
                            SpringSurface(
                                onClick = { selectedGroupId = grp.id },
                                shape = RoundedCornerShape(999.dp),
                                color = if (isSelected) tokens.obsidian else tokens.elevatedSurface,
                                border = if (isSelected) null else BorderStroke(1.dp, tokens.hairline)
                            ) {
                                Text(
                                    text = grp.name,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) tokens.onObsidian else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick examples follow below (Type & Send bar is mounted right beneath the Voice Orb above)

            // Tap-to-Try Voice Command Examples
            if (voiceState !is AsyncUiState.Success) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TRY SAYING OR TAPPING",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.inkMuted
                    )
                    smartExamples.forEachIndexed { idx, sample ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.elevatedSurface,
                            border = BorderStroke(1.dp, tokens.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .springClickable(pressedScale = 0.975f) {
                                    typedCommand = sample
                                    onParseCommand(sample, selectedGroupId, autoApplyToLedger, false)
                                }
                                .testTag("voice_example_chip_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = tokens.acidLime,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "\"$sample\"",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
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
