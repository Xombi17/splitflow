package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SplitFlowThemeTokens

private enum class CoachmarkAnchor {
    TOP_HERO,
    UPPER_MID_TOOLS,
    BOTTOM_CENTER_ADD,
    BOTTOM_TABS
}

private data class CoachmarkStep(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val anchor: CoachmarkAnchor
)

private val HOME_COACHMARK_STEPS = listOf(
    CoachmarkStep(
        title = "Net Position & My UPI QR",
        body = "Your live owed/owe balance updates in real time. Tap 'Add QR' on the hero card to attach your GPay, PhonePe, or Paytm QR screenshot, or tap the eye icon above to blur figures in public.",
        icon = Icons.Rounded.QrCode2,
        anchor = CoachmarkAnchor.TOP_HERO
    ),
    CoachmarkStep(
        title = "AI Quick Tools (Scan · Voice · Batch · Coach)",
        body = "Scan single restaurant bills with local Mirage OCR, dictate splits via Cactus Whistle, batch-import up to 8 trip receipts, or open your On-Device Spend Coach.",
        icon = Icons.Rounded.AutoAwesome,
        anchor = CoachmarkAnchor.UPPER_MID_TOOLS
    ),
    CoachmarkStep(
        title = "Quick Add (+) from Anywhere",
        body = "Tap the glowing centered '+' button anytime to open Table & Bar Split (Veg/Non-Veg & Drinks), AI Receipt Lens, Voice Split, or a Smart Manual Split.",
        icon = Icons.Rounded.AddCircle,
        anchor = CoachmarkAnchor.BOTTOM_CENTER_ADD
    ),
    CoachmarkStep(
        title = "Your 4 Organized Spaces",
        body = "Switch effortlessly between Home (overview & settles), Groups (trips & budgets), Activity (5-month chart & receipt gallery), and People (friends & UPI IDs).",
        icon = Icons.Rounded.Dashboard,
        anchor = CoachmarkAnchor.BOTTOM_TABS
    )
)

/**
 * Interactive Apple-caliber spotlight coachmark overlay shown on Home for first-time users
 * and replayable anytime from Profile.
 */
@Composable
fun FeatureCoachmarkOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = HOME_COACHMARK_STEPS[stepIndex]

    val pulseTransition = rememberInfiniteTransition(label = "coachmark_beacon")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beacon_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (tokens.isDark) 0.72f else 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Intercept touches behind overlay
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("feature_coachmark_overlay")
    ) {
        // Contextual pulsing Acid-Lime beacon near the highlighted Home region
        val beaconAlignment = when (step.anchor) {
            CoachmarkAnchor.TOP_HERO -> Alignment.TopEnd
            CoachmarkAnchor.UPPER_MID_TOOLS -> Alignment.TopStart
            CoachmarkAnchor.BOTTOM_CENTER_ADD -> Alignment.BottomCenter
            CoachmarkAnchor.BOTTOM_TABS -> Alignment.BottomStart
        }
        val beaconPadding = when (step.anchor) {
            CoachmarkAnchor.TOP_HERO -> Modifier.padding(top = 150.dp, end = 44.dp)
            CoachmarkAnchor.UPPER_MID_TOOLS -> Modifier.padding(top = 316.dp, start = 48.dp)
            CoachmarkAnchor.BOTTOM_CENTER_ADD -> Modifier.padding(bottom = 26.dp)
            CoachmarkAnchor.BOTTOM_TABS -> Modifier.padding(bottom = 26.dp, start = 104.dp)
        }

        Box(
            modifier = Modifier
                .align(beaconAlignment)
                .then(beaconPadding)
                .size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        alpha = pulseAlpha
                    }
                    .clip(CircleShape)
                    .background(tokens.acidLime)
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(tokens.acidLime)
            )
        }

        // Tooltip card positioned gracefully opposite the beacon
        val cardAlignment = when (step.anchor) {
            CoachmarkAnchor.TOP_HERO,
            CoachmarkAnchor.UPPER_MID_TOOLS -> Alignment.BottomCenter
            CoachmarkAnchor.BOTTOM_CENTER_ADD,
            CoachmarkAnchor.BOTTOM_TABS -> Alignment.Center
        }

        Box(
            modifier = Modifier
                .align(cardAlignment)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 20.dp, vertical = if (cardAlignment == Alignment.BottomCenter) 104.dp else 20.dp)
        ) {
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.95f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)))
                },
                label = "coachmark_step_card"
            ) { activeIdx ->
                val activeStep = HOME_COACHMARK_STEPS[activeIdx]
                val isLast = activeIdx == HOME_COACHMARK_STEPS.lastIndex

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = tokens.cardSurface,
                    border = BorderStroke(1.dp, tokens.acidLime.copy(alpha = 0.45f)),
                    shadowElevation = 18.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                                )
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(tokens.acidLime),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = activeStep.icon,
                                        contentDescription = null,
                                        tint = tokens.onAcidLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.elevatedSurface
                                ) {
                                    Text(
                                        text = "TOUR ${activeIdx + 1} OF ${HOME_COACHMARK_STEPS.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.positive,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Skip Tour",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = tokens.inkMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("coachmark_skip_button")
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = activeStep.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeStep.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Step Progress Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(HOME_COACHMARK_STEPS.size) { barIdx ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (barIdx <= activeIdx) tokens.acidLime else tokens.hairline
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeIdx > 0) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.elevatedSurface,
                                    border = BorderStroke(1.dp, tokens.hairline),
                                    modifier = Modifier
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .tactileClickable {
                                            stepIndex = (stepIndex - 1).coerceAtLeast(0)
                                        }
                                        .testTag("coachmark_prev_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Previous tip",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Back",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                        )
                                    )
                                    .tactileClickable {
                                        if (isLast) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDismiss()
                                        } else {
                                            stepIndex = (stepIndex + 1).coerceAtMost(HOME_COACHMARK_STEPS.lastIndex)
                                        }
                                    }
                                    .testTag("coachmark_next_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isLast) "Start Splitting" else "Next Tip",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.onAcidLime
                                    )
                                    Icon(
                                        imageVector = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = tokens.onAcidLime,
                                        modifier = Modifier.size(16.dp)
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
