package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiProviderKeyStore
import com.example.ai.AiProviderType
import com.example.ai.ApiKeyEntry
import com.example.ai.ApiKeyHealth
import com.example.ai.ApiKeyTestResult
import com.example.ai.RoutingStrategy
import com.example.ui.theme.SplitFlowThemeTokens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiProviderSettingsSheet(
    keyStore: AiProviderKeyStore,
    onSetRoutingStrategy: (RoutingStrategy) -> Unit,
    onAddOrUpdateKey: (ApiKeyEntry, (Result<ApiKeyEntry>) -> Unit) -> Unit,
    onToggleKey: (String, Boolean) -> Unit,
    onRemoveKey: (String) -> Unit,
    onResetCooldowns: () -> Unit,
    onTestKey: suspend (ApiKeyEntry) -> ApiKeyTestResult,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val keys by keyStore.keys.collectAsStateWithLifecycle()
    val routingStrategy by keyStore.routingStrategy.collectAsStateWithLifecycle()

    // Composer state for adding a new BYOK key
    var selectedProvider by remember { mutableStateOf(AiProviderType.GROQ) }
    var labelInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf(AiProviderType.GROQ.defaultBaseUrl) }
    var modelInput by remember { mutableStateOf(AiProviderType.GROQ.defaultModel) }
    var showRawKey by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    // Per-key live test state
    val testingKeyIds = remember { mutableStateMapOf<String, Boolean>() }
    val testMessages = remember { mutableStateMapOf<String, ApiKeyTestResult>() }

    val hasAnyCooldownOrError = keys.any {
        it.health == ApiKeyHealth.COOLDOWN || it.health == ApiKeyHealth.ERROR
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
                .testTag("ai_provider_settings_sheet"),
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
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = tokens.aiAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "AI Providers & API Keys",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Multi-provider round-robin + Gemini 3 failover",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.inkMuted
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ai_provider_settings_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close AI provider settings")
                }
            }

            // Built-in Firebase AI Logic status card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.aiContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = tokens.aiAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Built-in Gemini 3 Active (No Key Needed)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Auto-failover: Gemini 3.8 Flash → Gemini 3.5 Flash-Lite on rate limits or high demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Routing Strategy Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Routing Strategy",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                RoutingStrategy.entries.forEach { strategy ->
                    val selected = routingStrategy == strategy
                    Surface(
                        onClick = { onSetRoutingStrategy(strategy) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) tokens.positiveContainer.copy(alpha = 0.5f) else tokens.cardSurface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) tokens.positive else tokens.hairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routing_strategy_${strategy.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = strategy.label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strategy.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.inkMuted
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected routing strategy",
                                    tint = tokens.positive,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Configured BYOK Keys Pool
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Round-Robin Key Pool (${keys.count { it.enabled }}/${keys.size} active)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (hasAnyCooldownOrError) {
                        OutlinedButton(
                            onClick = onResetCooldowns,
                            modifier = Modifier.testTag("reset_byok_cooldowns_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset Cooldowns")
                        }
                    }
                }

                if (keys.isEmpty()) {
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No custom API keys added yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Add free-tier keys from Groq, OpenRouter, Cerebras, Mistral, or Google AI Studio below. SplitFlow rotates across all enabled keys in round-robin and skips any key hitting 429/503 limits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.inkMuted
                            )
                        }
                    }
                } else {
                    keys.forEach { entry ->
                        val isTesting = testingKeyIds[entry.id] == true
                        val lastTest = testMessages[entry.id]
                        val healthBadgeText = when {
                            !entry.enabled -> "Paused"
                            entry.health == ApiKeyHealth.ACTIVE && entry.lastLatencyMs != null ->
                                "Active · ${entry.lastLatencyMs}ms"
                            entry.health == ApiKeyHealth.COOLDOWN && entry.isInCooldown() ->
                                "Cooldown ${entry.cooldownRemainingSeconds()}s"
                            entry.health == ApiKeyHealth.AUTH_ERROR -> "Auth Error"
                            entry.health == ApiKeyHealth.ERROR -> "Error"
                            else -> "Ready"
                        }
                        val healthColor = when {
                            !entry.enabled -> tokens.inkMuted
                            entry.health == ApiKeyHealth.ACTIVE || entry.health == ApiKeyHealth.READY -> tokens.positive
                            entry.health == ApiKeyHealth.COOLDOWN -> tokens.warning
                            else -> tokens.negative
                        }

                        EditorialCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("byok_key_card_${entry.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.label,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = healthColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = healthBadgeText,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = healthColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${entry.provider.displayName} · ${entry.modelName} · ${entry.maskedKey}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Switch(
                                        checked = entry.enabled,
                                        onCheckedChange = { onToggleKey(entry.id, it) },
                                        modifier = Modifier.testTag("toggle_byok_key_${entry.id}")
                                    )
                                }

                                if (lastTest != null) {
                                    Text(
                                        text = lastTest.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lastTest.success) tokens.positive else tokens.negative
                                    )
                                } else if (!entry.lastError.isNullOrBlank()) {
                                    Text(
                                        text = entry.lastError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.warning
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                testingKeyIds[entry.id] = true
                                                val res = onTestKey(entry)
                                                testMessages[entry.id] = res
                                                testingKeyIds[entry.id] = false
                                            }
                                        },
                                        enabled = !isTesting,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("test_byok_key_${entry.id}")
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Testing...")
                                        } else {
                                            Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Test Key")
                                        }
                                    }

                                    IconButton(
                                        onClick = { onRemoveKey(entry.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_byok_key_${entry.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete ${entry.label} key",
                                            tint = tokens.negative
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add New API Key Composer
            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add API Key (Free-Tier Presets)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiProviderType.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedProvider == preset,
                                onClick = {
                                    selectedProvider = preset
                                    baseUrlInput = preset.defaultBaseUrl
                                    modelInput = preset.defaultModel
                                    formError = null
                                },
                                label = { Text(preset.displayName) },
                                modifier = Modifier.testTag("byok_preset_${preset.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = selectedProvider.freeTierHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.aiAccent
                    )

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Nickname (optional)") },
                        placeholder = { Text("e.g., ${selectedProvider.displayName} Personal #1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_label_input")
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            formError = null
                        },
                        label = { Text("API Key") },
                        placeholder = { Text(selectedProvider.keyPrefixHint) },
                        singleLine = true,
                        visualTransformation = if (showRawKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRawKey = !showRawKey }) {
                                Icon(
                                    imageVector = if (showRawKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showRawKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_api_key_input")
                    )

                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = {
                            modelInput = it
                            formError = null
                        },
                        label = { Text("Model ID") },
                        placeholder = { Text(selectedProvider.defaultModel.ifBlank { "llama-3.3-70b-versatile" }) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_model_input")
                    )

                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = {
                            baseUrlInput = it
                            formError = null
                        },
                        label = { Text("HTTPS Endpoint URL") },
                        placeholder = { Text("https://api.example.com/v1/chat/completions") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("byok_base_url_input")
                    )

                    if (formError != null) {
                        Text(
                            text = formError.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.negative
                        )
                    }

                    Button(
                        onClick = {
                            val candidate = ApiKeyEntry(
                                provider = selectedProvider,
                                label = labelInput,
                                apiKey = apiKeyInput,
                                baseUrl = baseUrlInput,
                                modelName = modelInput,
                                enabled = true
                            )
                            onAddOrUpdateKey(candidate) { res ->
                                res.onSuccess {
                                    labelInput = ""
                                    apiKeyInput = ""
                                    formError = null
                                }.onFailure { err ->
                                    formError = err.localizedMessage ?: "Invalid key configuration"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_byok_key_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Key to Round-Robin Pool")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = tokens.inkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Encrypted locally with Android Keystore AES-GCM. Keys never sync to cloud or logs.",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted
                        )
                    }
                }
            }
        }
    }
}
