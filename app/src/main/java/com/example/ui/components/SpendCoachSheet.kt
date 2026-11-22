package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AsyncUiState
import com.example.model.SpendCoachReport
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularHeroStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpendCoachSheet(
    scopeLabel: String,
    coachState: AsyncUiState<SpendCoachReport>,
    followUpAnswer: String,
    isStreaming: Boolean,
    onGenerateReport: () -> Unit,
    onAskFollowUp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var followUpInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (coachState is AsyncUiState.Idle) {
            onGenerateReport()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AI Spend Coach",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AiBadge("GEMINI 3.8 FLASH")
                    }
                    Text(
                        text = scopeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = onGenerateReport,
                        modifier = Modifier.testTag("refresh_spend_coach_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate insights")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_spend_coach_sheet")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            }

            when (coachState) {
                is AsyncUiState.Idle,
                is AsyncUiState.Loading -> {
                    AiShimmerSkeleton(label = "Analyzing category velocity, recurring bills & budget pace...")
                }

                is AsyncUiState.Error -> {
                    HairlineSection {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = tokens.negative
                                )
                                Text(
                                    text = "Coaching unavailable",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.negative
                                )
                            }
                            Text(
                                text = coachState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onGenerateReport,
                                modifier = Modifier.testTag("retry_spend_coach_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }

                is AsyncUiState.Success -> {
                    val report = coachState.data
                    val scoreColor = when {
                        report.healthScore >= 75 -> tokens.positive
                        report.healthScore >= 50 -> tokens.warning
                        else -> tokens.negative
                    }

                    // Health Score & Verdict Card
                    HairlineSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val track = tokens.elevatedSurface
                                Canvas(modifier = Modifier.size(72.dp)) {
                                    val stroke = 7.dp.toPx()
                                    val diameter = size.minDimension - stroke
                                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                                    val arcSize = Size(diameter, diameter)
                                    drawArc(
                                        color = track,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = scoreColor,
                                        startAngle = -90f,
                                        sweepAngle = (report.healthScore / 100f) * 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${report.healthScore}",
                                    style = TabularHeroStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                                    color = scoreColor
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "LEDGER HEALTH SCORE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.inkMuted
                                )
                                Text(
                                    text = report.headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Budget Alert Callout (if any)
                    report.budgetAlertNote.takeIf { it.isNotBlank() }?.let { alert ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.warningContainer,
                            border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = tokens.warning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = alert,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Structured Coaching Insights (Hairline-divided)
                    if (report.insights.isNotEmpty()) {
                        HairlineSection {
                            report.insights.forEachIndexed { idx, insight ->
                                val toneColor = when (idx % 3) {
                                    0 -> tokens.positive
                                    1 -> tokens.aiAccent
                                    else -> tokens.warning
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = insight.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        insight.potentialSavingsLabel.takeIf { it.isNotBlank() }?.let { pill ->
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = toneColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = pill,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = toneColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = insight.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (idx < report.insights.lastIndex) {
                                    HorizontalDivider(color = tokens.hairline)
                                }
                            }
                        }
                    }

                    // Streaming Follow-up Q&A
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ask your Spend Coach",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val prompts = listOf(
                            "Where can I trim 15% next month?",
                            "Audit my recurring bills",
                            "How is our Goa trip budget pacing?"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            prompts.forEach { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        followUpInput = suggestion
                                        onAskFollowUp(suggestion)
                                    },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = followUpInput,
                                onValueChange = { followUpInput = it },
                                placeholder = { Text("Ask about budgets, food spend, FX...") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coach_followup_input")
                            )
                            Button(
                                onClick = {
                                    if (followUpInput.isNotBlank() && !isStreaming) {
                                        onAskFollowUp(followUpInput)
                                    }
                                },
                                enabled = followUpInput.isNotBlank() && !isStreaming,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.aiAccent),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("coach_followup_send")
                            ) {
                                if (isStreaming) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send question")
                                }
                            }
                        }

                        if (followUpAnswer.isNotBlank() || isStreaming) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = tokens.aiContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, tokens.aiAccent.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = tokens.aiAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (isStreaming) "Streaming response..." else "Coach Answer",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = tokens.aiAccent
                                        )
                                    }
                                    Text(
                                        text = followUpAnswer.ifBlank { "Thinking..." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
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
