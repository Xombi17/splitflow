package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.AnomalySeverity
import com.example.model.CategorySpend
import com.example.model.CurrencyCode
import com.example.model.DailyBurnStats
import com.example.model.ExpenseAnomaly
import com.example.model.LedgerMember
import com.example.model.MonthlyCashFlowPoint
import com.example.ui.theme.LocalPrivacyBlur
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularSmallStyle
import kotlin.math.sin

fun parseHexColor(hex: String?, fallback: Color = Color(0xFF0D9488)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .tactileClickable(pressedScale = 0.975f, onClick = onClick)
                } else Modifier
            ),
        shape = shape,
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tokens.hairline),
        shadowElevation = if (tokens.isDark) 0.dp else 1.dp,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

@Composable
fun MemberAvatar(
    member: LedgerMember?,
    size: Dp = 36.dp,
    selected: Boolean = true,
    showGuestDot: Boolean = false,
    ringColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val baseColor = parseHexColor(member?.avatarColorHex, Color(0xFF0E6F4E))
    val tokens = SplitFlowThemeTokens.colors
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (selected) baseColor else tokens.elevatedSurface)
                .border(
                    width = if (ringColor != null) 2.dp else if (selected) 0.dp else 1.dp,
                    color = ringColor ?: if (selected) Color.Transparent else tokens.hairline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member?.initials ?: "?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (selected) Color.White else tokens.inkMuted
            )
        }
        if (showGuestDot && member?.guestProfile == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(tokens.warning)
                    .border(1.5.dp, tokens.cardSurface, CircleShape)
            )
        }
    }
}

@Composable
fun GuestBadge(
    isGuest: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    if (isGuest) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(6.dp),
            color = tokens.warningContainer
        ) {
            Text(
                text = "GUEST",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = tokens.warning,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    } else {
        Icon(
            imageVector = Icons.Rounded.Verified,
            contentDescription = "Verified member",
            tint = tokens.positive,
            modifier = modifier.size(15.dp)
        )
    }
}

@Composable
fun AiBadge(
    label: String = "AI Scan",
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = tokens.elevatedSurface,
        border = BorderStroke(1.dp, tokens.hairline)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun OfflineStatusBanner(modifier: Modifier = Modifier) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tokens.warningContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = "Offline mode",
                tint = tokens.warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Offline mode — edits cached locally and will sync automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CategoryDonutChart(
    breakdown: List<CategorySpend>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    val total = breakdown.sumOf { it.totalMinorUnits }
    val trackColor = SplitFlowThemeTokens.colors.elevatedSurface
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(116.dp)) {
                val strokeWidth = 18.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                if (breakdown.isEmpty()) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else {
                    var startAngle = -90f
                    breakdown.forEach { item ->
                        val sweep = (item.percentage / 100f) * 360f
                        drawArc(
                            color = parseHexColor(item.category.colorHex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - 3f).coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = SplitFlowThemeTokens.colors.inkMuted
                )
                Text(
                    text = FinanceEngine.formatMinorUnits(total, currency),
                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            breakdown.take(4).forEach { slice ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(slice.category.colorHex))
                        )
                        Text(
                            text = slice.category.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${slice.percentage.toInt()}%",
                        style = TabularSmallStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    isListening: Boolean,
    barColor: Color = SplitFlowThemeTokens.colors.acidLime,
    barCount: Int = 9,
    micAmplitude: Float? = null,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(
        modifier = modifier
            .width(46.dp)
            .height(38.dp)
    ) {
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2.5f)
        val centerY = size.height / 2f
        val liveGain = micAmplitude?.coerceIn(0.12f, 1f)

        for (i in 0 until barCount) {
            val normalizedPos = (i.toFloat() + 0.5f) / barCount.toFloat()
            val envelope = sin(normalizedPos * Math.PI).toFloat().coerceAtLeast(0.22f)
            val amplitude = if (isListening) {
                val wave = (sin(phase + i * 0.78f) + 1f) / 2f
                if (liveGain != null) {
                    (0.18f + 0.82f * liveGain * (0.45f + 0.55f * wave)) * envelope
                } else {
                    (0.25f + 0.75f * wave) * envelope
                }
            } else {
                (0.32f + 0.52f * ((sin(i * 1.1f) + 1f) / 2f)) * envelope
            }
            val barH = (size.height * amplitude).coerceAtLeast(5.dp.toPx())
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = if (isListening) barColor else barColor.copy(alpha = 0.85f),
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun UpiQrCanvas(
    payload: String,
    modifier: Modifier = Modifier
) {
    val matrix = androidx.compose.runtime.remember(payload) {
        FinanceEngine.generateDeterministicQrMatrix(payload, size = 21)
    }
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SplitFlowThemeTokens.colors.hairline)
    ) {
        Canvas(
            modifier = Modifier
                .size(168.dp)
                .padding(14.dp)
        ) {
            val cellSize = size.minDimension / matrix.size
            for (r in matrix.indices) {
                for (c in matrix[r].indices) {
                    if (matrix[r][c]) {
                        drawRoundRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(c * cellSize, r * cellSize),
                            size = Size(cellSize * 0.92f, cellSize * 0.92f),
                            cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiShimmerSkeleton(
    label: String,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            tokens.elevatedSurface,
            tokens.aiContainer.copy(alpha = 0.75f),
            tokens.elevatedSurface
        ),
        start = Offset(offset - 300f, 0f),
        end = Offset(offset, 200f)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiBadge("GEMINI 3.8 FLASH")
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        repeat(4) { idx ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (idx == 3) 0.68f else 1f)
                    .height(if (idx == 0) 56.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun TabularCurrencyText(
    minorUnits: Long,
    currency: CurrencyCode,
    showPlusSign: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = TabularAmountStyle,
    modifier: Modifier = Modifier
) {
    RollingTabularCurrencyText(
        minorUnits = minorUnits,
        currency = currency,
        showPlusSign = showPlusSign,
        color = color,
        style = style,
        modifier = modifier
    )
}

@Composable
fun HairlineSection(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = tokens.cardSurface,
        border = BorderStroke(1.dp, tokens.hairline),
        shadowElevation = if (tokens.isDark) 0.dp else 1.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun BudgetProgressBar(
    status: com.example.model.GroupBudgetStatus,
    currency: CurrencyCode,
    compact: Boolean = false,
    showStatusLabel: Boolean = !compact,
    modifier: Modifier = Modifier
) {
    val capMinor = status.budgetMinorUnits ?: return
    if (status.health == com.example.model.BudgetHealth.NONE) return
    val tokens = SplitFlowThemeTokens.colors
    val percentUsed = (status.progressRatio * 100f).toInt()
    val targetFraction = status.progressRatio.coerceIn(0f, 1f)
    val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.82f,
            stiffness = 380f
        ),
        label = "budget_progress"
    )
    val barColor = when (status.health) {
        com.example.model.BudgetHealth.HEALTHY -> tokens.budgetBarFill
        com.example.model.BudgetHealth.WARNING -> tokens.budgetBarFill
        com.example.model.BudgetHealth.EXCEEDED -> tokens.negative
        com.example.model.BudgetHealth.NONE -> tokens.inkMuted
    }
    val statusBadge = when (status.health) {
        com.example.model.BudgetHealth.HEALTHY -> "$percentUsed% used"
        com.example.model.BudgetHealth.WARNING -> "$percentUsed% • Near limit"
        com.example.model.BudgetHealth.EXCEEDED -> "$percentUsed% • Over cap"
        com.example.model.BudgetHealth.NONE -> ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FinanceEngine.formatMinorUnits(status.spentMinorUnits, currency),
                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = " / ${FinanceEngine.formatMinorUnits(capMinor, currency)}",
                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Normal),
                    color = tokens.inkMuted
                )
            }
            if (showStatusLabel) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (status.health == com.example.model.BudgetHealth.EXCEEDED) tokens.negative else tokens.inkMuted
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tokens.barTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(0.03f))
                    .height(if (compact) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (status.health == com.example.model.BudgetHealth.EXCEEDED) {
                            Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.8f)))
                        } else {
                            Brush.horizontalGradient(
                                listOf(tokens.budgetBarGradientStart, tokens.budgetBarGradientEnd)
                            )
                        }
                    )
            )
        }
    }
}

@Composable
fun AnomalyGuardBanner(
    anomalies: List<ExpenseAnomaly>,
    modifier: Modifier = Modifier
) {
    if (anomalies.isEmpty()) return
    val tokens = SplitFlowThemeTokens.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        anomalies.forEach { alert ->
            val accent = when (alert.severity) {
                AnomalySeverity.HIGH -> tokens.negative
                AnomalySeverity.WARNING -> tokens.warning
                AnomalySeverity.INFO -> tokens.aiAccent
            }
            val bg = when (alert.severity) {
                AnomalySeverity.HIGH -> tokens.negativeContainer
                AnomalySeverity.WARNING -> tokens.warningContainer
                AnomalySeverity.INFO -> tokens.aiContainer
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Anomaly warning",
                        tint = accent,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(16.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Anomaly Guard • ${alert.title}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = accent
                        )
                        Text(
                            text = alert.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupMemoryChip(
    memoryNotes: String?,
    modifier: Modifier = Modifier
) {
    if (memoryNotes.isNullOrBlank()) return
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tokens.aiContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = "Smart Split Memory",
                tint = tokens.aiAccent,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SMART SPLIT MEMORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.aiAccent
                )
                Text(
                    text = memoryNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DailyBurnMeterCard(
    stats: DailyBurnStats,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (stats.activeDays <= 0) return
    val tokens = SplitFlowThemeTokens.colors
    EditorialCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = tokens.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Daily Burn Meter", style = MaterialTheme.typography.titleSmall)
                }
                stats.projectedDaysLeft?.let { days ->
                    val badgeColor = when {
                        days <= 0 -> tokens.negative
                        days <= 3 -> tokens.warning
                        else -> tokens.positive
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (days <= 0) "Cap reached" else "~$days d runway",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AVG / DAY", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                    TabularCurrencyText(
                        minorUnits = stats.avgDailyMinor,
                        currency = currency,
                        style = TabularAmountStyle.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "PEAK (${stats.peakDayLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                    TabularCurrencyText(
                        minorUnits = stats.peakDayMinor,
                        currency = currency,
                        style = TabularSmallStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.warning
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyCashFlowChart(
    points: List<MonthlyCashFlowPoint>,
    currency: CurrencyCode,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val tokens = SplitFlowThemeTokens.colors
    // Ensure a clean 5-month architectural capsule strip (pad preceding months if fewer than 5)
    val displayPoints = remember(points) {
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val trimmed = points.takeLast(5)
        if (trimmed.size >= 5) {
            trimmed
        } else {
            val first = trimmed.first()
            val parts = first.monthKey.split("-")
            val startYear = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val startMonth = parts.getOrNull(1)?.toIntOrNull() ?: 11
            val missing = 5 - trimmed.size
            val padded = (missing downTo 1).map { offset ->
                var m = startMonth - offset
                var y = startYear
                while (m <= 0) {
                    m += 12
                    y -= 1
                }
                MonthlyCashFlowPoint(
                    monthKey = "%04d-%02d".format(y, m),
                    monthLabel = monthNames.getOrElse(m - 1) { "M$m" },
                    totalSpendMinor = 0L,
                    lentMinor = 0L,
                    borrowedMinor = 0L,
                    topCategoryHex = "#0E6F4E"
                )
            }
            padded + trimmed
        }
    }
    var selectedIndex by remember(displayPoints.size) { mutableIntStateOf(displayPoints.lastIndex) }
    val selected = displayPoints.getOrNull(selectedIndex) ?: displayPoints.last()
    val maxTotal = (displayPoints.maxOfOrNull { it.totalSpendMinor } ?: 1L).coerceAtLeast(1L)

    val fullMonthTitle = remember(selected.monthLabel) {
        when (selected.monthLabel.take(3).lowercase()) {
            "jan" -> "January Spend"
            "feb" -> "February Spend"
            "mar" -> "March Spend"
            "apr" -> "April Spend"
            "may" -> "May Spend"
            "jun" -> "June Spend"
            "jul" -> "July Spend"
            "aug" -> "August Spend"
            "sep" -> "September Spend"
            "oct" -> "October Spend"
            "nov" -> "November Spend"
            "dec" -> "December Spend"
            else -> "${selected.monthLabel} Spend"
        }
    }

    EditorialCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(tokens.cardGradientStart, tokens.cardGradientEnd)
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Split: Left Spend Total + Right 5-Month Capsule Bar Chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = fullMonthTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TabularCurrencyText(
                        minorUnits = selected.totalSpendMinor,
                        currency = currency,
                        style = com.example.ui.theme.TabularTitleStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    displayPoints.forEachIndexed { idx, pt ->
                        val isSelected = idx == selectedIndex
                        val ratio = if (pt.totalSpendMinor <= 0L) {
                            // Subtle architectural floor bars for prior zero-spend months
                            listOf(0.38f, 0.72f, 0.44f, 0.62f, 0.85f).getOrElse(idx) { 0.35f }
                        } else {
                            (pt.totalSpendMinor.toFloat() / maxTotal.toFloat()).coerceIn(0.26f, 1f)
                        }
                        val targetBarHeightDp = (64f * ratio).dp
                        val barHeightDp by androidx.compose.animation.core.animateDpAsState(
                            targetValue = targetBarHeightDp,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.76f,
                                stiffness = 360f
                            ),
                            label = "cashflow_bar_$idx"
                        )
                        val isPlaceholder = pt.totalSpendMinor <= 0L && !isSelected

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .tactileClickable(pressedScale = 0.94f) { selectedIndex = idx }
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(66.dp)
                                    .width(18.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isSelected) {
                                    if (tokens.isDark) {
                                        // Warm Obsidian Dark Mode: vertical Acid-Lime gradient rounded capsule bar
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(barHeightDp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            tokens.limeGradientStart,
                                                            tokens.limeGradientEnd
                                                        )
                                                    )
                                                )
                                        )
                                    } else {
                                        // Warm Alabaster Light Mode: Obsidian gradient capsule with Acid-Lime gradient top cap
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(barHeightDp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            tokens.heroGradientStart,
                                                            tokens.heroGradientEnd
                                                        )
                                                    )
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height((barHeightDp * 0.22f).coerceIn(8.dp, 14.dp))
                                                    .align(Alignment.TopCenter)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                tokens.limeGradientStart,
                                                                tokens.limeGradientEnd
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(barHeightDp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (isPlaceholder) tokens.barTrack.copy(alpha = 0.75f)
                                                else tokens.barTrack
                                            )
                                    )
                                }
                            }
                            Text(
                                text = pt.monthLabel.take(3),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else tokens.inkMuted
                            )
                        }
                    }
                }
            }

            androidx.compose.material3.HorizontalDivider(color = tokens.hairline)

            // 3 Inline Metrics: Spent | Lent | Borrowed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                    TabularCurrencyText(
                        minorUnits = selected.totalSpendMinor,
                        currency = currency,
                        style = TabularAmountStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                androidx.compose.material3.VerticalDivider(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(horizontal = 10.dp),
                    color = tokens.hairline
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Lent",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                    TabularCurrencyText(
                        minorUnits = selected.lentMinor,
                        currency = currency,
                        style = TabularAmountStyle,
                        color = tokens.positive
                    )
                }
                androidx.compose.material3.VerticalDivider(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(horizontal = 10.dp),
                    color = tokens.hairline
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Borrowed",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.inkMuted
                    )
                    TabularCurrencyText(
                        minorUnits = selected.borrowedMinor,
                        currency = currency,
                        style = TabularAmountStyle,
                        color = tokens.negative
                    )
                }
            }
        }
    }
}


