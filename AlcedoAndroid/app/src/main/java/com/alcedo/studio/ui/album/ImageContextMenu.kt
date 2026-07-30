package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Long-press context menu for an image thumbnail. Offers copy/paste
 * adjustments, rating, flag, delete and add-to-collection actions.
 *
 * The menu is anchored at [offset] within the host coordinate space.
 */
@Composable
fun ImageContextMenu(
    expanded: Boolean,
    image: ImageItem?,
    offset: DpOffset,
    onDismiss: () -> Unit,
    onCopyAdjustments: () -> Unit = {},
    onPasteAdjustments: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    onSetFlag: (ImageFlag) -> Unit = {},
    onSetColorLabel: (ColorLabel) -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val s = Strings.res
    DropdownMenu(
        expanded = expanded && image != null,
        offset = offset,
        onDismissRequest = onDismiss,
    ) {
        if (image == null) return@DropdownMenu
        ContextItem(Icons.Outlined.ContentCopy, s.copyAdjustments, onCopyAdjustments)
        ContextItem(Icons.Outlined.ContentPaste, s.pasteAdjustments, onPasteAdjustments)
        HorizontalDivider(color = AlcedoColors.Divider)
        ContextItem(Icons.Outlined.Star, s.rate + " 1★", onClick = { onRate(1) })
        ContextItem(Icons.Outlined.Star, s.rate + " 3★", onClick = { onRate(3) })
        ContextItem(Icons.Outlined.Star, s.rate + " 5★", onClick = { onRate(5) })
        HorizontalDivider(color = AlcedoColors.Divider)
        ContextItem(Icons.Outlined.Flag, s.flagPick, onClick = { onSetFlag(ImageFlag.PICK) })
        ContextItem(Icons.Outlined.Flag, s.flagReject, onClick = { onSetFlag(ImageFlag.REJECT) })
        ContextItem(Icons.Outlined.Flag, s.flagClear, onClick = { onSetFlag(ImageFlag.NONE) })
        HorizontalDivider(color = AlcedoColors.Divider)
        ColorLabelRow(onSetColorLabel)
        HorizontalDivider(color = AlcedoColors.Divider)
        ContextItem(Icons.Outlined.Folder, s.addToCollection, onAddToCollection)
        ContextItem(Icons.Outlined.Delete, s.delete, onDelete, tint = AlcedoColors.Danger)
    }
}

@Composable
private fun ContextItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AlcedoColors.TextSecondary,
) {
    DropdownMenuItem(
        text = { Text(text = label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun ColorLabelRow(onSetColorLabel: (ColorLabel) -> Unit) {
    val s = Strings.res
    Column(modifier = Modifier.padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm)) {
        Text(text = s.colorLabel, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 220.dp)
                .padding(top = DesignTokens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorLabel.entries.forEach { label ->
                if (label != ColorLabel.NONE) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(label.hex), CircleShape)
                            .clickable { onSetColorLabel(label) },
                    )
                }
            }
        }
    }
}
