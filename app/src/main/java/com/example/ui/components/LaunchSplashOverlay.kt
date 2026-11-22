package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SplitFlowThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EditorialEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Cinematic Apple-caliber editorial logo reveal played on app launch (and replayable from Profile).
 *
 * Choreography:
 * 1. Architectural concentric rings breathe outward while the Matte Obsidian emblem springs into focus.
 * 2. Interlocking Electric Acid-Lime & Warm Sandstone SplitFlow arcs trace seamlessly into the center node.
 * 3. Masked editorial wordmark ("SplitFlow") glides up cleanly with an expanding Acid-Lime rule & tagline.
 * 4. Diagonal specular metallic sheen sweeps across the emblem.
 * 5. Fires [onStartRevealHandoff] right as the editorial veil lifts upward and dissolves, triggering the
 *    Home screen's synchronized staggered entrance in one uninterrupted motion.
 */
@Composable
fun LaunchSplashOverlay(
    onStartRevealHandoff: () -> Unit = {},
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current

    val ringExpand = remember { Animatable(0.74f) }
    val ringAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.70f) }
    val markAlpha = remember { Animatable(0f) }
    val markSweep = remember { Animatable(0f) }
    val nodeBloom = remember { Animatable(0f) }
    val sheenProgress = remember { Animatable(-0.55f) }
    val titleOffsetY = remember { Animatable(40f) }
    val titleAlpha = remember { Animatable(0f) }
    val ruleProgress = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(12f) }
    val overlayAlpha = remember { Animatable(1f) }
    val overlayScale = remember { Animatable(1f) }
    val overlayLiftY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Phase 1: Architectural rings + obsidian emblem spring entrance + arc trace
        launch {
            ringAlpha.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            ringExpand.animateTo(1.12f, animationSpec = tween(1150, easing = EditorialEaseOut))
        }
        launch {
            markAlpha.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        }
        launch {
            markScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.64f, stiffness = 270f)
            )
        }
        launch {
            markSweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = EditorialEaseOut)
            )
        }

        // Center split node bloom + subtle tactile haptic tick
        delay(220L)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch {
            nodeBloom.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.56f, stiffness = 360f)
            )
        }

        // Phase 2: Masked editorial wordmark glide + Acid-Lime rule expand + specular sheen
        delay(80L)
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, animationSpec = tween(460, easing = EditorialEaseOut))
        }
        launch {
            sheenProgress.animateTo(
                targetValue = 1.65f,
                animationSpec = tween(durationMillis = 660, easing = FastOutSlowInEasing)
            )
        }

        delay(130L)
        launch {
            ruleProgress.animateTo(1f, animationSpec = tween(380, easing = EditorialEaseOut))
        }
        launch {
            subtitleAlpha.animateTo(1f, animationSpec = tween(340, easing = FastOutSlowInEasing))
        }
        launch {
            subtitleOffsetY.animateTo(0f, animationSpec = tween(380, easing = EditorialEaseOut))
        }

        // Phase 3: Brief editorial hold at peak clarity, then hand off into Home stagger
        delay(560L)
        onStartRevealHandoff()

        launch {
            overlayScale.animateTo(1.055f, animationSpec = tween(380, easing = EditorialEaseOut))
        }
        launch {
            overlayLiftY.animateTo(-18f, animationSpec = tween(360, easing = EditorialEaseOut))
        }
        overlayAlpha.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        onFinished()
    }

    val canvasTop = if (tokens.isDark) Color(0xFF191A15) else Color(0xFFFAF9F4)
    val canvasBottom = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha.value
                scaleX = overlayScale.value
                scaleY = overlayScale.value
                translationY = overlayLiftY.value * density
            }
            .background(Brush.verticalGradient(listOf(canvasTop, canvasBottom)))
            .testTag("launch_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        // Ambient editorial radial glow + precision concentric hairline rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val baseRadius = size.minDimension * 0.48f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.acidLime.copy(alpha = (if (tokens.isDark) 0.16f else 0.22f) * ringAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ringExpand.value
                ),
                radius = baseRadius * ringExpand.value,
                center = center
            )

            // Architectural hairline rings expanding subtly
            val ringColor = if (tokens.isDark) {
                Color.White.copy(alpha = 0.055f * ringAlpha.value)
            } else {
                Color(0xFF141513).copy(alpha = 0.055f * ringAlpha.value)
            }
            drawCircle(
                color = ringColor,
                radius = 78.dp.toPx() * ringExpand.value,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = ringColor.copy(alpha = ringColor.alpha * 0.65f),
                radius = 122.dp.toPx() * ringExpand.value,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Precision Obsidian Emblem Tile with subtle Acid-Lime rim
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color.Transparent,
                shadowElevation = if (tokens.isDark) 0.dp else 18.dp,
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        alpha = markAlpha.value
                        scaleX = markScale.value
                        scaleY = markScale.value
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    tokens.heroGradientStart,
                                    tokens.heroGradientMid,
                                    tokens.heroGradientEnd
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    tokens.acidLime.copy(alpha = 0.42f * markSweep.value),
                                    tokens.heroBorder,
                                    tokens.onHero.copy(alpha = 0.14f)
                                )
                            ),
                            shape = RoundedCornerShape(30.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 5.8.dp.toPx()
                        val inset = 23.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        val topLeft = Offset(inset, inset)
                        val progress = markSweep.value

                        // Upper flowing arc (Electric Acid-Lime)
                        drawArc(
                            brush = Brush.linearGradient(
                                listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                            ),
                            startAngle = -148f,
                            sweepAngle = 158f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )

                        // Lower balanced arc (Warm Sandstone)
                        drawArc(
                            color = tokens.onHero.copy(alpha = 0.94f),
                            startAngle = 32f,
                            sweepAngle = 158f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )

                        // Center precision split node with spring bloom
                        val bloom = nodeBloom.value.coerceAtLeast(0f)
                        if (bloom > 0.02f) {
                            drawCircle(
                                color = tokens.acidLime.copy(alpha = 0.24f * bloom.coerceAtMost(1f)),
                                radius = 9.5.dp.toPx() * bloom,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                            drawCircle(
                                color = tokens.acidLime.copy(alpha = bloom.coerceAtMost(1f)),
                                radius = 5.2.dp.toPx() * bloom,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                        }

                        // Diagonal specular metallic sheen sweep
                        val sheenX = size.width * sheenProgress.value
                        val band = size.width * 0.42f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.24f),
                                    Color.Transparent
                                ),
                                start = Offset(sheenX - band, 0f),
                                end = Offset(sheenX + band, size.height)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Masked upward editorial wordmark reveal
            Box(
                modifier = Modifier.clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SplitFlow",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleOffsetY.value * density
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            // Expanding Acid-Lime editorial rule
            Box(
                modifier = Modifier
                    .width((46f * ruleProgress.value).dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                        )
                    )
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "PRECISION LEDGER · AI POWERED",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp
                ),
                color = tokens.inkMuted,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha.value
                    translationY = subtitleOffsetY.value * density
                }
            )
        }
    }
}
