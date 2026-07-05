package com.alcedo.studio.ui.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class ContextMenuItem(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Mobile-idiomatic long-press context menu rendered as a ModalBottomSheet
 * instead of a desktop-style AlertDialog right-click menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            // Drag handle + title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = imageName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider()

            items.forEach { item ->
                ContextMenuRow(item = item, onDismiss = onDismiss)
                if (item.label == "Export" || item.label == "Paste Adjustments") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuRow(item: ContextMenuItem, onDismiss: () -> Unit) {
    val tint = if (item.isDestructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable {
                item.onClick()
                onDismiss()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f)
        )
    }
}
