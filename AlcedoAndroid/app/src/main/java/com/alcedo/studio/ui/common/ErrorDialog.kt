package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * An error dialog with a collapsible details section for the underlying message.
 * Used to surface ViewModel errors with enough context to diagnose the failure.
 */
@Composable
fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    details: String? = null,
    retryText: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val s = Strings.res
    androidx.compose.material3.AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextSecondary,
                )
                if (!details.isNullOrBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlcedoColors.TextTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DesignTokens.spacingSm),
                    )
                }
            }
        },
        confirmButton = {
            if (retryText != null && onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(text = retryText, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(text = s.dismiss, color = AlcedoColors.TextSecondary)
                }
            }
        },
        dismissButton = if (retryText != null && onRetry != null) {
            { TextButton(onClick = onDismiss) { Text(text = s.dismiss, color = AlcedoColors.TextSecondary) } }
        } else null,
    )
}
