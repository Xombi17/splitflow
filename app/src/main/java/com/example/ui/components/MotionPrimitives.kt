package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.CurrencyCode
import com.example.ui.motion.SpringPhysics
import com.example.ui.motion.SpringPreset
import com.example.ui.motion.SpringTransitionStyle
import com.example.ui.motion.SpringTransitions
import com.example.ui.motion.springClickable
import com.example.ui.theme.LocalPrivacyBlur
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Coordinates Home screen staggered entrances with the editorial launch logo reveal so
 * cards stay parked while the launch splash is opaque and cascade in the exact moment
 * the editorial veil lifts.
 */
val LocalEntryRevealReady = compositionLocalOf { true }
val LocalEntryRevealEpoch = compositionLocalOf { 0 }

/**
 * Centralized Apple-caliber spring motion specifications for SplitFlow.
 * Critically-damped and softly expressive springs give every tap, slide, and reveal physical weight.
 */
object SplitFlowMotion {
    val TactilePress = SpringPhysics.floatSpec(SpringPreset.TACTILE_PRESS)
    val SnappyPill = SpringPhysics.dpSpec(SpringPreset.DETENT)
    val FluidFloat = SpringPhysics.floatSpec(SpringPreset.SMOOTH)
    val SpatialIntOffset = SpringPhysics.intOffsetSpec(SpringPreset.SMOOTH)
    val CardEntrance = SpringPhysics.floatSpec(SpringPreset.GENTLE)

    fun <T> spatialTabSpec(forward: Boolean) = SpringTransitions.contentTransform(
        style = SpringTransitionStyle.SPATIAL_TAB,
        forward = forward
    )
}

/**
 * Applies smooth Apple-style spring press depression with rebound, subtle lift, and haptic feedback.
 * Delegates to the core [springClickable] wrapper in `com.example.ui.motion`.
 */
fun Modifier.tactileClickable(
    pressedScale: Float = 0.962f,
    enabled: Boolean = true,
    hapticFeedback: Boolean = true,
    onClick: () -> Unit
): Modifier = this.springClickable(
    enabled = enabled,
    pressedScale = pressedScale,
    hapticFeedback = hapticFeedback,
    onClick = onClick
)

/**
 * Staggered spring choreography for Home and onboarding cards so entrances feel alive and layered.
 * Automatically waits for [LocalEntryRevealReady] to become true (triggered as the editorial logo
 * reveal lifts) so the full waterfall plays visibly rather than hidden behind the splash overlay.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    stepDelayMs: Long = 58L,
    baseDelayMs: Long = 0L,
    initialOffsetDp: Dp = 22.dp,
    initialScale: Float = 0.964f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val revealReady = LocalEntryRevealReady.current
    val revealEpoch = LocalEntryRevealEpoch.current

    val alpha = remember(index, revealEpoch) { Animatable(0f) }
    val offsetY = remember(index, revealEpoch) { Animatable(initialOffsetDp.value) }
    val scale = remember(index, revealEpoch) { Animatable(initialScale) }

    LaunchedEffect(index, revealReady, revealEpoch) {
        if (!revealReady) {
            alpha.snapTo(0f)
            offsetY.snapTo(initialOffsetDp.value)
            scale.snapTo(initialScale)
            return@LaunchedEffect
        }
        val totalDelay = baseDelayMs + (index.coerceAtMost(10)) * stepDelayMs
        if (totalDelay > 0L) delay(totalDelay)
        launch { alpha.animateTo(1f, animationSpec = SpringPhysics.floatSpec(SpringPreset.GENTLE)) }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = SpringPhysics.floatSpec(SpringPreset.GENTLE)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = SpringPhysics.floatSpec(SpringPreset.DETENT)
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value * density
            this.scaleX = scale.value
            this.scaleY = scale.value
        }
    ) {
        content()
    }
}

/**
 * Plush bottom-up spring entrance for the floating frosted capsule navigation dock,
 * synchronized with the post-splash Home stagger.
 */
@Composable
fun DockStaggeredEntrance(
    delayMs: Long = 220L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val revealReady = LocalEntryRevealReady.current
    val revealEpoch = LocalEntryRevealEpoch.current

    val alpha = remember(revealEpoch) { Animatable(0f) }
    val offsetY = remember(revealEpoch) { Animatable(38f) }
    val scale = remember(revealEpoch) { Animatable(0.95f) }

    LaunchedEffect(revealReady, revealEpoch) {
        if (!revealReady) {
            alpha.snapTo(0f)
            offsetY.snapTo(38f)
            scale.snapTo(0.95f)
            return@LaunchedEffect
        }
        delay(delayMs)
        launch { alpha.animateTo(1f, animationSpec = SpringPhysics.floatSpec(SpringPreset.SMOOTH)) }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY)
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value * density
            this.scaleX = scale.value
            this.scaleY = scale.value
        }
    ) {
        content()
    }
}

/**
 * Rolling tabular currency text that smoothly interpolates balance changes with spring physics
 * and smoothly animates privacy blur when toggling public screen protection.
 */
@Composable
fun RollingTabularCurrencyText(
    minorUnits: Long,
    currency: CurrencyCode,
    showPlusSign: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = TabularAmountStyle,
    modifier: Modifier = Modifier
) {
    val isBlurred = LocalPrivacyBlur.current
    val animatedMinor by animateFloatAsState(
        targetValue = minorUnits.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 260f
        ),
        label = "rolling_currency"
    )
    val blurRadius by animateDpAsState(
        targetValue = if (isBlurred) 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "privacy_blur"
    )
    val displayMinor = animatedMinor.roundToLong()
    val formatted = FinanceEngine.formatMinorUnits(displayMinor, currency, showPlusSign)
    val displayText = if (isBlurred && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        "${currency.symbol}•••••"
    } else {
        formatted
    }
    val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }

    Text(
        text = displayText,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(blurMod)
    )
}

/**
 * Ultra-refined diagonal specular light glide for editorial hero cards.
 */
fun Modifier.editorialSpecularSheen(
    sheenColor: Color,
    durationMillis: Int = 5600
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "editorial_sheen")
    val progress by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sheen_progress"
    )
    this.drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        if (width > 0f && height > 0f) {
            val startX = width * progress
            val band = width * 0.42f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, sheenColor, Color.Transparent),
                    start = Offset(startX - band, 0f),
                    end = Offset(startX + band, height)
                )
            )
        }
    }
}

/**
 * Apple iOS-style fluid segmented control with a spring-gliding active pill indicator.
 */
@Composable
fun FluidSegmentedPillBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    testTags: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, labels.lastIndex)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        shape = RoundedCornerShape(999.dp),
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tokens.hairline)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
        ) {
            val segmentWidth = maxWidth / labels.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * safeIndex,
                animationSpec = SplitFlowMotion.SnappyPill,
                label = "segment_indicator_x"
            )

            // Gliding Obsidian / Sandstone active capsule
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(tokens.obsidian)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                labels.forEachIndexed { idx, label ->
                    val selected = idx == safeIndex
                    val textColor by animateColorAsState(
                        targetValue = if (selected) tokens.onObsidian else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "seg_text_color_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(idx)
                                }
                            }
                            .then(
                                testTags.getOrNull(idx)?.let { Modifier.testTag(it) } ?: Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Smooth crossfading status pill (e.g. "YOU ARE OWED" / "ALL SETTLED").
 */
@Composable
fun AnimatedStatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
        label = "pill_bg"
    )
    val inkColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
        label = "pill_ink"
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) +
                    scaleIn(initialScale = 0.90f, animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY))) togetherWith
                    (fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) +
                        scaleOut(targetScale = 0.90f, animationSpec = SpringPhysics.floatSpec(SpringPreset.SNAPPY)))
            },
            label = "pill_text_swap"
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = inkColor,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
            )
        }
    }
}
