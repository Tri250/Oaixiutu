package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class ConfirmDialogType {
    NORMAL, WARNING, DANGER
}

@Composable
fun EnhancedConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    type: ConfirmDialogType = ConfirmDialogType.NORMAL,
    icon: ImageVector? = when(type) {
        ConfirmDialogType.WARNING -> Icons.Default.Warning
        ConfirmDialogType.DANGER -> Icons.Default.Delete
        else -> null
    },
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = if (icon != null) {
            {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when(type) {
                        ConfirmDialogType.DANGER -> MaterialTheme.colorScheme.error
                        ConfirmDialogType.WARNING -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
        } else null,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (type == ConfirmDialogType.DANGER) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else ButtonDefaults.textButtonColors()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
