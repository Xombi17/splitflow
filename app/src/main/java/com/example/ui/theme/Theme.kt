package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class SplitFlowExtendedColors(
    val isDark: Boolean,
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val aiAccent: Color,
    val aiContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val hairline: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val inkMuted: Color,
    // Primary CTA / active pill pair (Light: Matte Obsidian #141513 + White; Dark: Warm Sandstone #F2EFE9 + #111110)
    val obsidian: Color,
    val obsidianElevated: Color,
    val obsidianHairline: Color,
    val onObsidian: Color,
    // High-contrast dark anchor surfaces (Hero card, Receipt Lens footer, Voice waveform capsule)
    val heroSurface: Color,
    val heroBorder: Color,
    val heroDivider: Color,
    val onHero: Color,
    val voiceCapsuleBg: Color,
    // Signature Electric Acid-Lime highlight
    val acidLime: Color,
    val onAcidLime: Color,
    // Adaptive quick-add dock + button & Voice Confirm CTA (Light: Obsidian; Dark: Electric Acid-Lime)
    val dockAddBg: Color,
    val dockAddIcon: Color,
    val voiceConfirmBg: Color,
    val onVoiceConfirm: Color,
    // Bars & tracks
    val barTrack: Color,
    val budgetBarFill: Color,
    // Editorial Gradients
    val canvasGlowTop: Color,
    val heroGradientStart: Color,
    val heroGradientMid: Color,
    val heroGradientEnd: Color,
    val heroAccentGlow: Color,
    val limeGradientStart: Color,
    val limeGradientEnd: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val dockAddGradientStart: Color,
    val dockAddGradientEnd: Color,
    val voiceCapsuleGradientStart: Color,
    val voiceCapsuleGradientEnd: Color,
    val budgetBarGradientStart: Color,
    val budgetBarGradientEnd: Color
)

private val LightExtendedColors = SplitFlowExtendedColors(
    isDark = false,
    positive = BotanicalEmerald,
    positiveContainer = BotanicalEmeraldContainerLight,
    negative = Terracotta,
    negativeContainer = TerracottaContainerLight,
    aiAccent = EditorialAiAccentLight,
    aiContainer = EditorialAiContainerLight,
    warning = AmberWarning,
    warningContainer = AmberContainerLight,
    hairline = HairlineLight,
    cardSurface = PureSurfaceLight,
    elevatedSurface = ElevatedSurfaceLight,
    inkMuted = InkMutedLight,
    obsidian = MatteObsidian,
    obsidianElevated = MatteObsidianElevated,
    obsidianHairline = MatteObsidianHairline,
    onObsidian = Color.White,
    heroSurface = MatteObsidian,
    heroBorder = MatteObsidianHairline,
    heroDivider = MatteObsidianHairline,
    onHero = Color.White,
    voiceCapsuleBg = MatteObsidian,
    acidLime = AcidLime,
    onAcidLime = OnAcidLime,
    dockAddBg = MatteObsidian,
    dockAddIcon = Color.White,
    voiceConfirmBg = MatteObsidian,
    onVoiceConfirm = Color.White,
    barTrack = WarmBarTrackLight,
    budgetBarFill = MatteObsidian,
    canvasGlowTop = CanvasGlowTopLight,
    heroGradientStart = HeroGradStartLight,
    heroGradientMid = HeroGradMidLight,
    heroGradientEnd = HeroGradEndLight,
    heroAccentGlow = HeroGlowLight,
    limeGradientStart = AcidLimeGradStart,
    limeGradientEnd = AcidLimeGradEnd,
    cardGradientStart = CardGradStartLight,
    cardGradientEnd = CardGradEndLight,
    dockAddGradientStart = ObsidianGradStartLight,
    dockAddGradientEnd = ObsidianGradEndLight,
    voiceCapsuleGradientStart = VoiceGradStartLight,
    voiceCapsuleGradientEnd = VoiceGradEndLight,
    budgetBarGradientStart = BudgetGradStartLight,
    budgetBarGradientEnd = BudgetGradEndLight
)

private val DarkExtendedColors = SplitFlowExtendedColors(
    isDark = true,
    positive = BotanicalEmeraldBright,
    positiveContainer = BotanicalEmeraldContainerDark,
    negative = TerracottaBright,
    negativeContainer = TerracottaContainerDark,
    aiAccent = EditorialAiAccentDark,
    aiContainer = EditorialAiContainerDark,
    warning = AmberWarningDark,
    warningContainer = AmberContainerDark,
    hairline = HairlineDark,
    cardSurface = ZincSurfaceDark,
    elevatedSurface = ZincElevatedDark,
    inkMuted = InkMutedDark,
    obsidian = WarmSandstone,
    obsidianElevated = ZincElevatedDark,
    obsidianHairline = HairlineDark,
    onObsidian = ObsidianCanvasDark,
    heroSurface = ZincElevatedDark,
    heroBorder = WarmHeroBorderDark,
    heroDivider = WarmHeroBorderDark,
    onHero = WarmSandstone,
    voiceCapsuleBg = VoiceCapsuleDark,
    acidLime = AcidLime,
    onAcidLime = OnAcidLime,
    dockAddBg = AcidLime,
    dockAddIcon = OnAcidLime,
    voiceConfirmBg = AcidLime,
    onVoiceConfirm = OnAcidLime,
    barTrack = WarmBarTrackDark,
    budgetBarFill = WarmSandBarFillDark,
    canvasGlowTop = CanvasGlowTopDark,
    heroGradientStart = HeroGradStartDark,
    heroGradientMid = HeroGradMidDark,
    heroGradientEnd = HeroGradEndDark,
    heroAccentGlow = HeroGlowDark,
    limeGradientStart = AcidLimeGradStart,
    limeGradientEnd = AcidLimeGradEnd,
    cardGradientStart = CardGradStartDark,
    cardGradientEnd = CardGradEndDark,
    dockAddGradientStart = AcidLimeGradStart,
    dockAddGradientEnd = AcidLimeGradEnd,
    voiceCapsuleGradientStart = VoiceGradStartDark,
    voiceCapsuleGradientEnd = VoiceGradEndDark,
    budgetBarGradientStart = BudgetGradStartDark,
    budgetBarGradientEnd = BudgetGradEndDark
)

val LocalSplitFlowColors = staticCompositionLocalOf { DarkExtendedColors }
val LocalPrivacyBlur = staticCompositionLocalOf { false }

private val SplitFlowLightScheme = lightColorScheme(
    primary = MatteObsidian,
    onPrimary = Color.White,
    primaryContainer = ElevatedSurfaceLight,
    onPrimaryContainer = MatteObsidian,
    secondary = BotanicalEmerald,
    onSecondary = Color.White,
    secondaryContainer = BotanicalEmeraldContainerLight,
    onSecondaryContainer = BotanicalEmerald,
    tertiary = Terracotta,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaContainerLight,
    onTertiaryContainer = Terracotta,
    background = AlabasterCanvas,
    onBackground = InkPrimaryLight,
    surface = PureSurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = ElevatedSurfaceLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerLowest = PureSurfaceLight,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = PureSurfaceLight,
    surfaceContainerHigh = PureSurfaceLight,
    surfaceContainerHighest = ElevatedSurfaceLight,
    outline = InputBorderLight,
    outlineVariant = HairlineLight,
    error = Terracotta,
    onError = Color.White
)

private val SplitFlowDarkScheme = darkColorScheme(
    primary = WarmSandstone,
    onPrimary = ObsidianCanvasDark,
    primaryContainer = ZincElevatedDark,
    onPrimaryContainer = InkPrimaryDark,
    secondary = BotanicalEmeraldBright,
    onSecondary = ObsidianCanvasDark,
    secondaryContainer = BotanicalEmeraldContainerDark,
    onSecondaryContainer = BotanicalEmeraldBright,
    tertiary = TerracottaBright,
    onTertiary = ObsidianCanvasDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = TerracottaBright,
    background = ObsidianCanvasDark,
    onBackground = InkPrimaryDark,
    surface = ZincSurfaceDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = ZincElevatedDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerLowest = ObsidianCanvasDark,
    surfaceContainerLow = ZincSurfaceDark,
    surfaceContainer = ZincSurfaceDark,
    surfaceContainerHigh = ZincElevatedDark,
    surfaceContainerHighest = WarmBarTrackDark,
    outline = InputBorderDark,
    outlineVariant = HairlineDark,
    error = TerracottaBright,
    onError = ObsidianCanvasDark
)

private val SplitFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SplitFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SplitFlowDarkScheme else SplitFlowLightScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSplitFlowColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SplitFlowTypography,
            shapes = SplitFlowShapes,
            content = content
        )
    }
}

object SplitFlowThemeTokens {
    val colors: SplitFlowExtendedColors
        @Composable get() = LocalSplitFlowColors.current
}
