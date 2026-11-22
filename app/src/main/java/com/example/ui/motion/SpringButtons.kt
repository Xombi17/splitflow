package com.example.ui.motion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Attaches Apple-style spring press physics (scale compression + subtle vertical depression +
 * micro-opacity dim + haptic feedback) to ANY interactive component sharing [interactionSource].
 */
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.958f,
    pressedAlpha: Float = 0.92f,
    pressedTranslationY: Dp = 1.2.dp,
    preset: SpringPreset = SpringPreset.TACTILE_PRESS,
    hapticOnPress: Boolean = true
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionConfig = LocalSpringMotionConfig.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && hapticOnPress && motionConfig.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val targetScale = if (isPressed && enabled && !motionConfig.reduceMotion) pressedScale else 1f
    val targetAlpha = if (isPressed && enabled) pressedAlpha else 1f
    val targetY = if (isPressed && enabled && !motionConfig.reduceMotion) pressedTranslationY else 0.dp

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = SpringPhysics.floatSpec(preset),
        label = "spring_press_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = SpringPhysics.floatSpec(SpringPreset.SNAPPY),
        label = "spring_press_alpha"
    )
    val offsetY by animateDpAsState(
        targetValue = targetY,
        animationSpec = SpringPhysics.dpSpec(preset),
        label = "spring_press_y"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
        translationY = offsetY.toPx()
    }
}

/**
 * Self-contained spring-clickable modifier with press compression, ripple indication, and haptics.
 */
fun Modifier.springClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.962f,
    pressedAlpha: Float = 0.93f,
    pressedTranslationY: Dp = 1.dp,
    preset: SpringPreset = SpringPreset.TACTILE_PRESS,
    hapticFeedback: Boolean = true,
    showRipple: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            pressedAlpha = pressedAlpha,
            pressedTranslationY = pressedTranslationY,
            preset = preset,
            hapticOnPress = hapticFeedback
        )
        .clickable(
            interactionSource = interactionSource,
            indication = if (showRipple) LocalIndication.current else null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Tactile spring-physics drop-in wrapper for Material 3 [Button].
 */
@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedScale: Float = 0.955f,
    hapticFeedback: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            hapticOnPress = hapticFeedback
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Tactile spring-physics drop-in wrapper for Material 3 [OutlinedButton].
 */
@Composable
fun SpringOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedScale: Float = 0.958f,
    hapticFeedback: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            hapticOnPress = hapticFeedback
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Tactile spring-physics drop-in wrapper for Material 3 [FilledTonalButton].
 */
@Composable
fun SpringFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    pressedScale: Float = 0.958f,
    hapticFeedback: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            hapticOnPress = hapticFeedback
        ),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Tactile spring-physics drop-in wrapper for Material 3 [IconButton] with crisp icon squash & rebound.
 */
@Composable
fun SpringIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    pressedScale: Float = 0.87f,
    hapticFeedback: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            pressedTranslationY = 0.dp,
            preset = SpringPreset.BOUNCY,
            hapticOnPress = hapticFeedback
        ),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Tactile clickable [Surface] wrapper with spring press compression and smooth color/border interpolation.
 */
@Composable
fun SpringSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    pressedScale: Float = 0.962f,
    hapticFeedback: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = SpringPhysics.colorSpec(SpringPreset.SNAPPY),
        label = "spring_surface_color"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.springPress(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedScale = pressedScale,
            hapticOnPress = hapticFeedback
        ),
        enabled = enabled,
        shape = shape,
        color = animatedColor,
        contentColor = contentColor,
        border = border,
        shadowElevation = shadowElevation,
        tonalElevation = 0.dp,
        interactionSource = interactionSource,
        content = content
    )
}
