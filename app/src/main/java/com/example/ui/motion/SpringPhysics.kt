package com.example.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI

/**
 * Physically calibrated spring presets inspired by Apple's SwiftUI
 * `Animation.spring(response:dampingFraction:)` model.
 *
 * Conversion from physical response (seconds) to Compose spring stiffness:
 *   stiffness = (2 * PI / response)^2
 */
enum class SpringPreset(
    val responseSec: Float,
    val dampingFraction: Float
) {
    /** Crisp tap depression and immediate rebound for buttons, pills, and chips. */
    TACTILE_PRESS(responseSec = 0.25f, dampingFraction = 0.66f),

    /** Fast, tight spring for switches, small toggles, and icon feedback. */
    SNAPPY(responseSec = 0.28f, dampingFraction = 0.78f),

    /** Gliding detent spring for segmented controls, nav indicators, and drawers. */
    DETENT(responseSec = 0.34f, dampingFraction = 0.80f),

    /** Critically smooth spring for screen navigation and tab spatial transitions. */
    SMOOTH(responseSec = 0.38f, dampingFraction = 0.88f),

    /** Expressive micro-bounce for active nav icons, FAB morphs, and success checks. */
    BOUNCY(responseSec = 0.40f, dampingFraction = 0.62f),

    /** Layered, graceful deceleration for staggered cards, sheets, and balance rolls. */
    GENTLE(responseSec = 0.48f, dampingFraction = 0.84f);

    val stiffness: Float
        get() {
            val omega = (2.0 * PI / responseSec.coerceAtLeast(0.08f)).toFloat()
            return omega * omega
        }
}

@Immutable
data class SpringMotionConfig(
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true
)

val LocalSpringMotionConfig = staticCompositionLocalOf { SpringMotionConfig() }

/**
 * Central factory for typed Compose [SpringSpec] instances backed by [SpringPreset].
 */
object SpringPhysics {

    fun floatSpec(
        preset: SpringPreset = SpringPreset.SMOOTH,
        visibilityThreshold: Float = 0.001f
    ): SpringSpec<Float> = spring(
        dampingRatio = preset.dampingFraction,
        stiffness = preset.stiffness,
        visibilityThreshold = visibilityThreshold
    )

    fun dpSpec(
        preset: SpringPreset = SpringPreset.DETENT
    ): SpringSpec<Dp> = spring(
        dampingRatio = preset.dampingFraction,
        stiffness = preset.stiffness
    )

    fun intOffsetSpec(
        preset: SpringPreset = SpringPreset.SMOOTH
    ): SpringSpec<IntOffset> = spring(
        dampingRatio = preset.dampingFraction,
        stiffness = preset.stiffness
    )

    fun intSizeSpec(
        preset: SpringPreset = SpringPreset.DETENT
    ): SpringSpec<IntSize> = spring(
        dampingRatio = preset.dampingFraction,
        stiffness = preset.stiffness
    )

    fun colorSpec(
        preset: SpringPreset = SpringPreset.SNAPPY
    ): SpringSpec<Color> = spring(
        dampingRatio = preset.dampingFraction.coerceAtLeast(0.85f),
        stiffness = preset.stiffness
    )

    /** Quick custom spring directly from Apple-style (response, dampingFraction) parameters. */
    fun <T> custom(
        responseSec: Float,
        dampingFraction: Float,
        visibilityThreshold: T? = null
    ): SpringSpec<T> {
        val omega = (2.0 * PI / responseSec.coerceAtLeast(0.08f)).toFloat()
        return spring(
            dampingRatio = dampingFraction,
            stiffness = omega * omega,
            visibilityThreshold = visibilityThreshold
        )
    }
}
