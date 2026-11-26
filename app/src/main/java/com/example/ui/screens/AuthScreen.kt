package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.onGoogleSignInClicked
import com.example.ui.components.AiBadge
import com.example.ui.components.ContinueAsGuestButton
import com.example.ui.components.StaggeredEntrance
import com.example.ui.components.tactileClickable
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.theme.SplitFlowThemeTokens

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onReplayOnboarding: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val tokens = SplitFlowThemeTokens.colors

    var isLoading by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isGoogleConfigured = remember {
        try {
            val id = context.getString(R.string.default_web_client_id)
            id.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }
    var showCloudSetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isCheckingSilent = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isCheckingSilent = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
        ) {
            // Scrollable editorial overview
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brand Header + Replay Guide Pill
                StaggeredEntrance(index = 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_splitflow_logo_1795291447995),
                                contentDescription = "SplitFlow emblem",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text(
                                    text = "SplitFlow",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "AI-NATIVE LEDGER OS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.positive
                                )
                            }
                        }
                        if (onReplayOnboarding != null) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.cardSurface,
                                border = BorderStroke(1.dp, tokens.hairline),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .tactileClickable(onClick = onReplayOnboarding)
                                    .testTag("auth_replay_guide_button")
                            ) {
                                Text(
                                    text = "How it works",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.inkMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Hero Editorial Artwork
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, tokens.hairline),
                    color = tokens.cardSurface
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_auth_hero_1795291448961),
                            contentDescription = "SplitFlow financial flow visualization",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp),
                            contentScale = ContentScale.Crop
                        )
                        AiBadge(
                            label = "MULTILINGUAL VOICE & GEMINI AI",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Split bills in seconds. Settle in fewer moves.",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Scan itemized bills with AI Receipt Lens, dictate complex splits naturally, and minimize group transfers with deterministic math.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Core Capabilities Strip
                StaggeredEntrance(index = 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuthFeatureRow(
                            icon = Icons.Rounded.DocumentScanner,
                            title = "Multimodal AI Receipt Lens",
                            subtitle = "Tap-to-claim items with automatic proportional GST, tax & tip",
                            accentColor = tokens.aiAccent
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.Mic,
                            title = "Voice & Natural Language Splitter",
                            subtitle = "Speak exclusions naturally like \"Sam skipped drinks\" for instant math",
                            accentColor = tokens.aiAccent
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.AccountTree,
                            title = "Min-Cash-Flow Debt Simplifier",
                            subtitle = "Cloud-synced groups, Veg/Non-Veg + Drinks splits & personal UPI QR",
                            accentColor = tokens.positive
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = tokens.negativeContainer,
                        border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.negative,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Pinned Authentication Action Dock — always visible above the fold
            Surface(
                color = tokens.cardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = tokens.hairline)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isGoogleConfigured) {
                            // Primary: Google Sign-In (Cloud Sync)
                            SpringButton(
                                onClick = {
                                    errorMessage = null
                                    isLoading = true
                                    onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onAuthSuccess = {
                                            isLoading = false
                                            onAuthSuccess()
                                        },
                                        onAuthError = { msg ->
                                            isLoading = false
                                            errorMessage = msg
                                        },
                                        scope = scope,
                                        onAuthCancelled = {
                                            isLoading = false
                                        }
                                    )
                                },
                                enabled = !isLoading && !isCheckingSilent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.obsidian,
                                    contentColor = tokens.onObsidian
                                )
                            ) {
                                if (isLoading || isCheckingSilent) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = tokens.onObsidian,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Sign in with Google",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            // Secondary: Continue as Guest
                            ContinueAsGuestButton(
                                onClick = onContinueAsGuest,
                                enabled = !isLoading
                            )
                        } else {
                            // Offline-First Mode: Elevate Continue as Guest as Primary Action
                            SpringButton(
                                onClick = onContinueAsGuest,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("continue_as_guest_button"),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.obsidian,
                                    contentColor = tokens.onObsidian
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue as Guest (100% Offline)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Secondary: Sign in with Google (explains Cloud Sync setup)
                            SpringOutlinedButton(
                                onClick = { showCloudSetupDialog = true },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_sign_in_button"),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, tokens.hairline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = tokens.cardSurface,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = tokens.inkMuted
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Sign in with Google (Setup Cloud Sync)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.inkMuted
                                )
                            }
                        }

                        Text(
                            text = if (isGoogleConfigured) {
                                "Sign in with Google for isolated Cloud Firestore sync across devices, or start privately in Guest Mode—your local splits migrate automatically if you sign in later."
                            } else {
                                "SplitFlow is ready to use 100% offline in Guest Mode. All voice recognition, AI receipt OCR, and expense splits work locally without a Google account."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showCloudSetupDialog) {
        AlertDialog(
            onDismissRequest = { showCloudSetupDialog = false },
            title = {
                Text(
                    text = "Google Cloud Sync Setup",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SplitFlow is fully functional offline right now! All receipt scanning, voice splits, and local ledger storage work immediately in Guest Mode.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "To enable Google Sign-In & multi-device Cloud Firestore sync:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "1. Register package: com.aistudio.splitflow.kvnxqr in Firebase Console\n" +
                               "2. Add SHA-1 Fingerprint:\n32:0D:0E:FA:1E:3B:08:70:88:E2:F7:41:C4:8E:33:AB:63:79:16:DD\n" +
                               "3. Add your Web Client ID to strings.xml (default_web_client_id)",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.inkMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudSetupDialog = false
                        onContinueAsGuest()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.obsidian,
                        contentColor = tokens.onObsidian
                    )
                ) {
                    Text("Continue Offline as Guest")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudSetupDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tokens.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
