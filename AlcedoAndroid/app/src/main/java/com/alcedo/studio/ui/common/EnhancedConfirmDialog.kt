package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/** Severity of an enhanced confirm dialog, controlling icon and accent color. */
enum class ConfirmSeverity { INFO, WARNING, DANGER }

/**
 * An enhanced confirmation dialog with a severity icon and matching accent.
 * Supersedes [ConfirmDialog] for flows where the destructive nature should be
 * visually emphasized (delete version, clear cache, revoke consent, etc.).
 */
@Composable
fun EnhancedConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    severity: ConfirmSeverity = ConfirmSeverity.WARNING,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
) {
    val (icon: ImageVector, tint: Color) = when (severity) {
        ConfirmSeverity.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
        ConfirmSeverity.WARNING -> Icons.Outlined.WarningAmber to AlcedoColors.Warning
        ConfirmSeverity.DANGER -> Icons.Filled.Warning to AlcedoColors.Danger
    }
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (severity == ConfirmSeverity.DANGER) AlcedoColors.Danger else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText, color = AlcedoColors.TextSecondary)
            }
        },
    )
}
