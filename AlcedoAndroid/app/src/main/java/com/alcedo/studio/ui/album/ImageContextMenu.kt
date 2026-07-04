package com.alcedo.studio.ui.album

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class ContextMenuItem(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ImageContextMenu(
    imageName: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onRate: () -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopyAdjustments: () -> Unit = {},
    onPasteAdjustments: () -> Unit = {},
    onAnalyzeAi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ContextMenuItem("Edit", Icons.Default.Edit, onClick = onEdit),
        ContextMenuItem("Rate", Icons.Default.Star, onClick = onRate),
        ContextMenuItem("Add to Collection", Icons.Default.Collections, onClick = onAddToCollection),
        ContextMenuItem("Export", Icons.Default.FileDownload, onClick = onExport),
        ContextMenuItem("Copy Adjustments", Icons.Default.ContentCopy, onClick = onCopyAdjustments),
        ContextMenuItem("Paste Adjustments", Icons.Default.ContentPaste, onClick = onPasteAdjustments),
        ContextMenuItem("Analyze with AI", Icons.Default.AutoAwesome, onClick = onAnalyzeAi),
        ContextMenuItem("Delete", Icons.Default.Delete, isDestructive = true, onClick = onDelete)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                imageName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                item.onClick()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(20.dp),
                                tint = if (item.isDestructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.isDestructive) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (item.label == "Export" || item.label == "Paste Adjustments") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
