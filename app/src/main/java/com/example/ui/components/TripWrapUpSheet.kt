package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.AsyncUiState
import com.example.model.ExpenseGroup
import com.example.model.TripStoryNarrative
import com.example.ui.theme.SplitFlowThemeTokens
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TabularHeroStyle
import com.example.ui.theme.TabularSmallStyle
import com.example.viewmodel.LedgerSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripWrapUpSheet(
    group: ExpenseGroup,
    snapshot: LedgerSnapshot,
    storyState: AsyncUiState<TripStoryNarrative>,
    onGenerateStory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = SplitFlowThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val groupExpenses = remember(snapshot.expenses, group.id) {
        snapshot.expenses.filter { it.groupId == group.id }
    }
    val groupSettlements = remember(snapshot.settlements, group.id) {
        snapshot.settlements.filter { it.groupId == group.id }
    }
    val groupMembers = remember(snapshot.members, group.memberIds) {
        val ids = group.memberIds.toSet()
        snapshot.members.filter { it.id in ids }
    }
    val metrics = remember(group, groupMembers, groupExpenses, groupSettlements) {
        FinanceEngine.computeTripWrapUpMetrics(group, groupMembers, groupExpenses, groupSettlements)
    }

    LaunchedEffect(group.id) {
        if (storyState is AsyncUiState.Idle) {
            onGenerateStory(group.id)
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
                .padding(bottom = 32.dp)
                .testTag("trip_wrap_up_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    Text("Trip Wrap-Up Story", style = MaterialTheme.typography.titleLarge)
                    AiBadge("WRAPPED")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close trip story")
                }
            }

            // High-craft Obsidian Shareable Story Card
            val accent = parseHexColor(group.accentColorHex, tokens.positive)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = tokens.cardSurface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accent.copy(alpha = 0.16f),
                                    tokens.aiAccent.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SPLITFLOW LEDGER STORY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accent
                                )
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            if (storyState is AsyncUiState.Success) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.aiContainer
                                ) {
                                    Text(
                                        text = storyState.data.vibeTag,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = tokens.aiAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Hero Total Spend + Transfers Saved
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("TOTAL GROUP SPEND", style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
                            TabularCurrencyText(
                                minorUnits = metrics.totalSpendMinor,
                                currency = metrics.currency,
                                style = TabularHeroStyle.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${metrics.expenseCount} bills • ${groupMembers.size} members • Saved ${metrics.transfersSaved} settlement transfer(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.positive
                            )
                        }

                        // 2x2 Key Metrics Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StoryMetricTile(
                                label = "TOP SPENDER",
                                primary = metrics.topSpenderName.split(" ").first(),
                                subMinor = metrics.topSpenderMinor,
                                currency = metrics.currency,
                                modifier = Modifier.weight(1f)
                            )
                            StoryMetricTile(
                                label = "BIGGEST CATEGORY",
                                primary = metrics.biggestCategoryLabel,
                                subText = "${metrics.biggestCategoryPercent}% of spend",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StoryMetricTile(
                                label = "DAILY BURN RATE",
                                primary = "Avg / Day",
                                subMinor = metrics.avgDailyMinor,
                                currency = metrics.currency,
                                modifier = Modifier.weight(1f)
                            )
                            StoryMetricTile(
                                label = "GRAPH ENGINE",
                                primary = "${metrics.transfersSaved} Saved",
                                subText = "Min cash-flow paths",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Local On-Device Editorial Narrative
                        when (storyState) {
                            is AsyncUiState.Idle, is AsyncUiState.Loading -> {
                                AiShimmerSkeleton(label = "Writing your AI Ledger Story...")
                            }

                            is AsyncUiState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = tokens.negativeContainer
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = storyState.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = tokens.negative,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { onGenerateStory(group.id) }) {
                                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry story", tint = tokens.negative)
                                        }
                                    }
                                }
                            }

                            is AsyncUiState.Success -> {
                                val narrative = storyState.data
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = tokens.elevatedSurface.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, tokens.hairline)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = narrative.headline,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        narrative.highlights.forEach { bullet ->
                                            Text(
                                                text = "• $bullet",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "\"${narrative.closingQuip}\"",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tokens.aiAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Share & Regenerate Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onGenerateStory(group.id) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remix AI")
                }
                Button(
                    onClick = {
                        val narrative = (storyState as? AsyncUiState.Success)?.data
                        val shareText = buildString {
                            appendLine("✨ ${group.name} — SplitFlow Wrap-Up")
                            if (narrative != null) {
                                appendLine("${narrative.headline} [${narrative.vibeTag}]")
                            }
                            appendLine("Total Spend: ${FinanceEngine.formatMinorUnits(metrics.totalSpendMinor, metrics.currency)} (${metrics.expenseCount} bills)")
                            appendLine("Top Spender: ${metrics.topSpenderName} (${FinanceEngine.formatMinorUnits(metrics.topSpenderMinor, metrics.currency)})")
                            appendLine("Biggest Category: ${metrics.biggestCategoryLabel} (${metrics.biggestCategoryPercent}%)")
                            appendLine("Daily Burn: ${FinanceEngine.formatMinorUnits(metrics.avgDailyMinor, metrics.currency)}/day")
                            appendLine("Debt Simplification: Saved ${metrics.transfersSaved} transfer(s)")
                            if (narrative != null) {
                                appendLine()
                                narrative.highlights.forEach { appendLine("• $it") }
                                appendLine("\n${narrative.closingQuip}")
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "${group.name} Wrap-Up Story")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Trip Story"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.positive),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("share_trip_story_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Story")
                }
            }
        }
    }
}

@Composable
private fun StoryMetricTile(
    label: String,
    primary: String,
    subMinor: Long? = null,
    currency: com.example.model.CurrencyCode = com.example.model.CurrencyCode.INR,
    subText: String? = null,
    modifier: Modifier = Modifier
) {
    val tokens = SplitFlowThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tokens.elevatedSurface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, tokens.hairline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.inkMuted)
            Text(primary, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (subMinor != null) {
                TabularCurrencyText(
                    minorUnits = subMinor,
                    currency = currency,
                    style = TabularSmallStyle.copy(fontWeight = FontWeight.Bold),
                    color = tokens.positive
                )
            } else if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
