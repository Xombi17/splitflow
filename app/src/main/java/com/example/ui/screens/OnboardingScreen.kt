package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DietPreference
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.DietMarkIcon
import com.example.ui.components.SplitFlowMotion
import com.example.ui.components.editorialSpecularSheen
import com.example.ui.components.tactileClickable
import com.example.ui.motion.SpringButton
import com.example.ui.motion.SpringPhysics
import com.example.ui.motion.SpringPreset
import com.example.ui.motion.springClickable
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingSlideData(
    val eyebrow: String,
    val title: String,
    val description: String
)

private val ONBOARDING_SLIDES = listOf(
    OnboardingSlideData(
        eyebrow = "MULTILINGUAL VOICE & RECEIPT LENS",
        title = "Scan or speak any bill in seconds",
        description = "Speak naturally in English, Hindi, Marathi, or Tamil, or snap any restaurant receipt. On-device ML Kit OCR and Gemini AI extract line items, prorate GST, and split every rupee accurately."
    ),
    OnboardingSlideData(
        eyebrow = "TABLE & BAR STUDIO",
        title = "Veg, Non-Veg & Drinks split fairly",
        description = "Tag dishes and drinks by glass or peg. Vegetarians never pay for seafood, and non-drinkers never subsidize the bar tab or alcohol VAT."
    ),
    OnboardingSlideData(
        eyebrow = "DEBT SIMPLIFIER & MY UPI QR",
        title = "Fewer transfers. Instant UPI settle.",
        description = "Our min-cash-flow engine collapses tangled group debts into the fewest payments possible—and lets friends scan your personal UPI QR directly."
    ),
    OnboardingSlideData(
        eyebrow = "CLOUD SYNC & PRIVACY",
        title = "Private cloud ledger, offline ready",
        description = "Sign in with Google for isolated Cloud Firestore sync across devices, bring your own free API keys if you want, or start in Guest Mode anytime."
    )
)

/**
 * Parallax-driven 4-slide interactive editorial onboarding walkthrough.
 */
@Composable
fun OnboardingScreen(
    isReplay: Boolean = false,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_SLIDES.size })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("onboarding_screen")
    ) {
        // Atmospheric editorial top wash
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(tokens.canvasGlowTop, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Brand mark + Skip pill
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
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(tokens.acidLime)
                    )
                    Text(
                        text = "SplitFlow",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.cardSurface,
                    border = BorderStroke(1.dp, tokens.hairline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .tactileClickable(onClick = onComplete)
                        .testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = if (isReplay) "Done" else "Skip",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.inkMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Parallax Horizontal Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                val slide = ONBOARDING_SLIDES[page]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Parallax Interactive Showcase Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val scale = 1f - (absOffset * 0.08f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (absOffset * 0.45f)
                                translationX = pageOffset * 42f
                            }
                    ) {
                        when (page) {
                            0 -> SlidePreviewReceiptVoice()
                            1 -> SlidePreviewTableBar()
                            2 -> SlidePreviewDebtUpi()
                            else -> SlidePreviewCloudPrivacy()
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Editorial Copy with Parallax Lag
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .graphicsLayer {
                                translationX = pageOffset * 90f
                                alpha = (1f - absOffset * 1.25f).coerceIn(0f, 1f)
                            }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.elevatedSurface,
                            border = BorderStroke(1.dp, tokens.hairline)
                        ) {
                            Text(
                                text = slide.eyebrow,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = tokens.positive,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = slide.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Morphing Pill Indicators + Primary CTA Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Morphing elastic progress pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ONBOARDING_SLIDES.size) { idx ->
                        val active = pagerState.currentPage == idx
                        val pillWidth by animateDpAsState(
                            targetValue = if (active) 28.dp else 8.dp,
                            animationSpec = SpringPhysics.dpSpec(SpringPreset.BOUNCY),
                            label = "dot_w_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(pillWidth)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (active) tokens.acidLime else tokens.hairline
                                )
                                .springClickable(pressedScale = 0.86f) {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                }
                        )
                    }
                }

                val isLastPage = pagerState.currentPage == ONBOARDING_SLIDES.lastIndex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        Surface(
                            shape = CircleShape,
                            color = tokens.cardSurface,
                            border = BorderStroke(1.dp, tokens.hairline),
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .tactileClickable(
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                )
                                .testTag("onboarding_back_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Previous slide",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isLastPage) {
                                    Brush.horizontalGradient(
                                        listOf(tokens.limeGradientStart, tokens.limeGradientEnd)
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(tokens.obsidian, tokens.obsidian.copy(alpha = 0.92f))
                                    )
                                }
                            )
                            .tactileClickable(
                                pressedScale = 0.97f,
                                onClick = {
                                    if (isLastPage) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onComplete()
                                    } else {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                }
                            )
                            .testTag(if (isLastPage) "onboarding_get_started_button" else "onboarding_next_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isLastPage && isReplay -> "Back to SplitFlow"
                                    isLastPage -> "Get Started"
                                    else -> "Continue"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isLastPage) tokens.onAcidLime else tokens.onObsidian
                            )
                            Icon(
                                imageVector = if (isLastPage) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = if (isLastPage) tokens.onAcidLime else tokens.onObsidian,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStageCard(
    content: @Composable () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, tokens.heroBorder),
        shadowElevation = if (tokens.isDark) 0.dp else 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            tokens.heroGradientStart,
                            tokens.heroGradientMid,
                            tokens.heroGradientEnd
                        )
                    )
                )
                .editorialSpecularSheen(
                    sheenColor = Color.White.copy(alpha = if (tokens.isDark) 0.05f else 0.08f)
                )
                .padding(20.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SlidePreviewReceiptVoice() {
    val tokens = SplitFlowThemeTokens.colors
    val infinite = rememberInfiniteTransition(label = "scan_laser")
    val laserY by infinite.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    OnboardingStageCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            imageVector = Icons.Rounded.DocumentScanner,
                            contentDescription = null,
                            tint = tokens.onAcidLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "AI Receipt Lens",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onHero
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.acidLime
                ) {
                    Text(
                        text = "GST AUTO-PRORATED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onAcidLime,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            // Animated receipt scan surface with Acid-Lime laser beam
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height * laserY
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, tokens.acidLime, Color.Transparent)
                            ),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    PreviewLineRow("Woodfire Margherita", "₹680", "All 4")
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    PreviewLineRow("Signature Cocktails ×2", "₹1,200", "2 claimed")
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    PreviewLineRow("Tax & Service (5%)", "₹94", "Proportional")
                }
            }

            // Voice Split Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.09f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = tokens.acidLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "\"Split dinner equally, Sam skipped drinks\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.onHero.copy(alpha = 0.88f),
                        modifier = Modifier.weight(1f)
                    )
                    AudioWaveformVisualizer(
                        isListening = true,
                        barColor = tokens.acidLime,
                        barCount = 7,
                        modifier = Modifier.height(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewLineRow(title: String, price: String, tag: String) {
    val tokens = SplitFlowThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.onHero
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.onHero.copy(alpha = 0.58f)
            )
        }
        Text(
            text = price,
            style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold),
            color = tokens.onHero
        )
    }
}

@Composable
private fun SlidePreviewTableBar() {
    val tokens = SplitFlowThemeTokens.colors
    OnboardingStageCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(tokens.positive.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalBar,
                        contentDescription = null,
                        tint = tokens.acidLime,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Table & Bar Split Studio",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onHero
                    )
                    Text(
                        text = "Food GST 5% · Bar VAT 18% separated",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onHero.copy(alpha = 0.62f)
                    )
                }
            }

            // Dietary Buckets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, tokens.positive.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DietMarkIcon(diet = DietPreference.VEG, size = 12.dp)
                            Text("Veg", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = tokens.onHero)
                        }
                        Text("₹1,240", style = TabularAmountStyle, color = tokens.acidLime)
                        Text("Shared by all", style = MaterialTheme.typography.labelSmall, color = tokens.onHero.copy(alpha = 0.60f))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, tokens.negative.copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DietMarkIcon(diet = DietPreference.NON_VEG, size = 12.dp)
                            Text("Non-Veg", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = tokens.onHero)
                        }
                        Text("₹1,860", style = TabularAmountStyle, color = tokens.onHero)
                        Text("2 diners only", style = MaterialTheme.typography.labelSmall, color = tokens.onHero.copy(alpha = 0.60f))
                    }
                }
            }

            // Drinks Bar Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Drinks by Glass / Peg",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = tokens.onHero
                        )
                        Text(
                            text = "Zero-proof friends automatically excluded",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onHero.copy(alpha = 0.62f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = tokens.acidLime
                    ) {
                        Text(
                            text = "100% FAIR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = tokens.onAcidLime,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidePreviewDebtUpi() {
    val tokens = SplitFlowThemeTokens.colors
    OnboardingStageCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = tokens.acidLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Min-Cash-Flow Engine",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onHero
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = tokens.positive.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "3 TRANSFERS SAVED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.acidLime,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("5 tangled IOUs", style = MaterialTheme.typography.labelSmall, color = tokens.onHero.copy(alpha = 0.55f))
                        Text("Collapsed into 2 clean moves", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = tokens.onHero)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = tokens.acidLime
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, tokens.acidLime.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = Color(0xFF141513),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Attach Your Personal UPI QR",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = tokens.onHero
                        )
                        Text(
                            text = "GPay, PhonePe & Paytm ready right on Home",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.onHero.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidePreviewCloudPrivacy() {
    val tokens = SplitFlowThemeTokens.colors
    OnboardingStageCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tokens.acidLime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = tokens.onAcidLime,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column {
                    Text(
                        text = "Your Private Cloud Vault",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.onHero
                    )
                    Text(
                        text = "Isolated per-user Firebase Firestore",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onHero.copy(alpha = 0.64f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = tokens.acidLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Works offline on flights & auto-syncs when online",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.onHero.copy(alpha = 0.90f)
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = tokens.acidLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "One-tap Privacy Blur masks every amount in public",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.onHero.copy(alpha = 0.90f)
                        )
                    }
                }
            }

            Text(
                text = "Start clean with zero fake data—your real friends, trips, and bills only.",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.acidLime.copy(alpha = 0.92f)
            )
        }
    }
}


