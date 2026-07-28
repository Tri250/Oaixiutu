package com.alcedo.studio.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Batch edit panel for the album's multi-selection. Lets the user apply a
 * preset, sync adjustments from the first selected image, or clear all
 * adjustments across the selected set.
 */
@Composable
fun BatchEditPanel(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    onApplyPreset: () -> Unit = {},
    onSyncFromFirst: () -> Unit = {},
    onClearAdjustments: () -> Unit = {},
    onCopyAdjustments: () -> Unit = {},
    onPasteAdjustments: () -> Unit = {},
) {
    val s = Strings.res
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Text(
            text = s.batchEditTitle + " ($selectedCount)",
            style = MaterialTheme.typography.titleMedium,
            color = AlcedoColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
            OutlinedButton(onClick = onCopyAdjustments, modifier = Modifier.weight(1f)) {
                Text(s.copyAdjustments)
            }
            OutlinedButton(onClick = onPasteAdjustments, modifier = Modifier.weight(1f), enabled = selectedCount > 0) {
                Text(s.pasteAdjustments)
            }
        }
        Button(
            onClick = onApplyPreset,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedCount > 0,
        ) {
            Text(s.batchApplyPreset)
        }
        Button(
            onClick = onSyncFromFirst,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedCount > 1,
            colors = ButtonDefaults.buttonColors(containerColor = AlcedoColors.SurfaceElevated),
        ) {
            Text(s.batchSyncSettings)
        }
        OutlinedButton(
            onClick = onClearAdjustments,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedCount > 0,
        ) {
            Text(s.batchClearAdjustments, color = AlcedoColors.Danger)
        }
    }
}
