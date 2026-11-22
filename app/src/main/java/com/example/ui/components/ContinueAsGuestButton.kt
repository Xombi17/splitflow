package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.motion.SpringOutlinedButton
import com.example.ui.theme.SplitFlowThemeTokens

/**
 * Reusable secondary CTA positioned beneath the "Sign in with Google" button
 * to initialize a persistent local guest session so users can split expenses
 * immediately without authentication.
 */
@Composable
fun ContinueAsGuestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val tokens = SplitFlowThemeTokens.colors
    SpringOutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("continue_as_guest_button"),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, tokens.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tokens.cardSurface,
            contentColor = MaterialTheme.colorScheme.onBackground
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onBackground,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PersonOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Continue as Guest",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
