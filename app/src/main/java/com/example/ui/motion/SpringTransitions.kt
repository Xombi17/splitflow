package com.example.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

enum class SpringTransitionStyle {
    /** Horizontal parallax slide + subtle depth scale for peer top-level tabs. */
    SPATIAL_TAB,

    /** iOS UINavigationController-style drill-in push & back pop with parallax backdrop recession. */
    PUSH_POP,

    /** Vertical spring rise + crisp scale for sheets, overlays, and modal workbenches. */
    MODAL_DEPTH,

    /** Smooth spring scale + fade for major root flow transitions (Auth -> Onboarding -> Shell). */
    SHARED_SCALE
}

enum class SpringVisibilityStyle {
    EXPAND_VERTICAL,
    POP_SCALE,
    SLIDE_UP_FADE
}

/**
 * Generates an Apple-calibrated spring [ContentTransform] for navigation transitions.
 */
object SpringTransitions {

    fun contentTransform(
        style: SpringTransitionStyle,
        forward: Boolean = true,
        reduceMotion: Boolean = false
    ): ContentTransform {
        if (reduceMotion) {
            return fadeIn(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) togetherWith
                fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY))
        }

        val offsetSpec = SpringPhysics.intOffsetSpec(SpringPreset.SMOOTH)
        val alphaSpec = SpringPhysics.floatSpec(SpringPreset.SMOOTH)
        val scaleSpec = SpringPhysics.floatSpec(SpringPreset.SMOOTH)

        return when (style) {
            SpringTransitionStyle.SPATIAL_TAB -> {
                val enter = slideInHorizontally(
                    animationSpec = offsetSpec,
                    initialOffsetX = { w -> if (forward) (w * 0.09f).roundToInt() else -(w * 0.09f).roundToInt() }
                ) + fadeIn(alphaSpec) + scaleIn(
                    initialScale = 0.982f,
                    animationSpec = scaleSpec
                )
                val exit = slideOutHorizontally(
                    animationSpec = offsetSpec,
                    targetOffsetX = { w -> if (forward) -(w * 0.06f).roundToInt() else (w * 0.06f).roundToInt() }
                ) + fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) + scaleOut(
                    targetScale = 0.986f,
                    animationSpec = scaleSpec
                )
                (enter togetherWith exit).apply {
                    targetContentZIndex = if (forward) 1f else 0f
                }
            }

            SpringTransitionStyle.PUSH_POP -> {
                if (forward) {
                    // Pushing detail view in from right while parent recedes subtly to left
                    val enter = slideInHorizontally(
                        animationSpec = offsetSpec,
                        initialOffsetX = { w -> (w * 0.28f).roundToInt() }
                    ) + fadeIn(alphaSpec) + scaleIn(
                        initialScale = 0.985f,
                        animationSpec = scaleSpec
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = offsetSpec,
                        targetOffsetX = { w -> -(w * 0.14f).roundToInt() }
                    ) + fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) + scaleOut(
                        targetScale = 0.955f,
                        animationSpec = scaleSpec
                    )
                    (enter togetherWith exit).apply { targetContentZIndex = 1f }
                } else {
                    // Popping back to parent list while detail slides cleanly out to right
                    val enter = slideInHorizontally(
                        animationSpec = offsetSpec,
                        initialOffsetX = { w -> -(w * 0.14f).roundToInt() }
                    ) + fadeIn(alphaSpec) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = scaleSpec
                    )
                    val exit = slideOutHorizontally(
                        animationSpec = offsetSpec,
                        targetOffsetX = { w -> (w * 0.28f).roundToInt() }
                    ) + fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) + scaleOut(
                        targetScale = 0.985f,
                        animationSpec = scaleSpec
                    )
                    (enter togetherWith exit).apply { targetContentZIndex = 0f }
                }
            }

            SpringTransitionStyle.MODAL_DEPTH -> {
                val enter = slideInVertically(
                    animationSpec = SpringPhysics.intOffsetSpec(SpringPreset.DETENT),
                    initialOffsetY = { h -> (h * 0.12f).roundToInt() }
                ) + fadeIn(alphaSpec) + scaleIn(
                    initialScale = 0.965f,
                    animationSpec = scaleSpec
                )
                val exit = slideOutVertically(
                    animationSpec = SpringPhysics.intOffsetSpec(SpringPreset.SNAPPY),
                    targetOffsetY = { h -> (h * 0.08f).roundToInt() }
                ) + fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) + scaleOut(
                    targetScale = 0.97f,
                    animationSpec = scaleSpec
                )
                enter togetherWith exit
            }

            SpringTransitionStyle.SHARED_SCALE -> {
                val enter = fadeIn(SpringPhysics.floatSpec(SpringPreset.GENTLE)) + scaleIn(
                    initialScale = 0.965f,
                    animationSpec = SpringPhysics.floatSpec(SpringPreset.GENTLE)
                )
                val exit = fadeOut(SpringPhysics.floatSpec(SpringPreset.SNAPPY)) + scaleOut(
                    targetScale = 1.02f,
                    animationSpec = SpringPhysics.floatSpec(SpringPreset.SNAPPY)
                )
                enter togetherWith exit
            }
        }
    }
}

/**
 * Spring-driven navigation host wrapper for switching tabs, drilling into detail screens,
 * or transitioning root app flows with physical spring momentum.
 */
@Composable
fun <T> SpringNavHostContainer(
    targetState: T,
    style: SpringTransitionStyle = SpringTransitionStyle.SPATIAL_TAB,
    isForward: (initial: T, target: T) -> Boolean = { _, _ -> true },
    contentKey: (T) -> Any? = { it },
    label: String = "spring_nav_transition",
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    val motionConfig = LocalSpringMotionConfig.current
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            SpringTransitions.contentTransform(
                style = style,
                forward = isForward(initialState, targetState),
                reduceMotion = motionConfig.reduceMotion
            )
        },
        contentKey = contentKey,
        label = label
    ) { state ->
        content(state)
    }
}

/**
 * Spring-backed visibility wrapper for expandable panels, floating badges, and contextual toolbars.
 */
@Composable
fun SpringAnimatedVisibility(
    visible: Boolean,
    style: SpringVisibilityStyle = SpringVisibilityStyle.EXPAND_VERTICAL,
    modifier: Modifier = Modifier,
    label: String = "spring_visibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    val sizeSpec = SpringPhysics.intSizeSpec(SpringPreset.DETENT)
    val offsetSpec = SpringPhysics.intOffsetSpec(SpringPreset.DETENT)
    val floatSpec = SpringPhysics.floatSpec(SpringPreset.SNAPPY)

    val enter = when (style) {
        SpringVisibilityStyle.EXPAND_VERTICAL ->
            expandVertically(animationSpec = sizeSpec, expandFrom = Alignment.Top) + fadeIn(floatSpec)
        SpringVisibilityStyle.POP_SCALE ->
            scaleIn(initialScale = 0.88f, animationSpec = SpringPhysics.floatSpec(SpringPreset.BOUNCY)) + fadeIn(floatSpec)
        SpringVisibilityStyle.SLIDE_UP_FADE ->
            slideInVertically(animationSpec = offsetSpec, initialOffsetY = { it / 3 }) + fadeIn(floatSpec)
    }
    val exit = when (style) {
        SpringVisibilityStyle.EXPAND_VERTICAL ->
            shrinkVertically(animationSpec = sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(floatSpec)
        SpringVisibilityStyle.POP_SCALE ->
            scaleOut(targetScale = 0.88f, animationSpec = floatSpec) + fadeOut(floatSpec)
        SpringVisibilityStyle.SLIDE_UP_FADE ->
            slideOutVertically(animationSpec = offsetSpec, targetOffsetY = { it / 4 }) + fadeOut(floatSpec)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        label = label,
        content = content
    )
}

/**
 * Convenience modifier applying spring-calibrated layout size morphing to cards and collapsible rows.
 */
fun Modifier.springAnimateContentSize(
    preset: SpringPreset = SpringPreset.DETENT
): Modifier = this.animateContentSize(
    animationSpec = SpringPhysics.intSizeSpec(preset)
)
