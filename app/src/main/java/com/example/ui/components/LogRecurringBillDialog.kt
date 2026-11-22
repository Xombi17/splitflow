package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.engine.FinanceEngine
import com.example.model.ExpenseRecord
import com.example.ui.theme.SplitFlowThemeTokens

@Composable
fun LogRecurringBillDialog(
    expense: ExpenseRecord,
    onConfirm: (overrideAmountMinor: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = SplitFlowThemeTokens.colors
    var amountInput by remember(expense.id) {
        mutableStateOf(FinanceEngine.minorToMajorInput(expense.totalMinorUnits))
    }
    val parsedMinor = FinanceEngine.parseMajorToMinor(amountInput)
    val nextDue = FinanceEngine.advanceDueDateIso(
        expense.nextDueDateLabel ?: expense.dateLabel,
        expense.recurringEnum
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${expense.recurringEnum.label} Bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${expense.title} — keep the preset amount for fixed bills or adjust for variable utilities (electricity, water, Wi-Fi). Payer & split weights rescale automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("This Cycle Amount (${expense.currencyEnum.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recurring_variable_amount_input")
                )
                Text(
                    text = "Next scheduled due date will advance to $nextDue",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.positive
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedMinor > 0L) {
                        onConfirm(parsedMinor)
                    }
                },
                enabled = parsedMinor > 0L,
                modifier = Modifier.testTag("confirm_log_recurring_button")
            ) {
                Text("Log Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
