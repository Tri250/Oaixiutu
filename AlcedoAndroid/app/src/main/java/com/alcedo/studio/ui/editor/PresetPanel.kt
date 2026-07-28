package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelinePreset
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Preset panel. Grid of presets grouped by category with a favourite toggle
 * and an apply action. "Save current as preset" opens a small dialog to name
 * the new preset.
 */
@Composable
fun PresetPanel(
    presets: List<PipelinePreset>,
    favorites: List<PipelinePreset>,
    onApply: (PipelinePreset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onToggleFavorite: (PipelinePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var saveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    val favoriteIds = favorites.map { it.id }.toHashSet()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.presets) {
            OutlinedButton(onClick = { saveDialog = true; saveName = "" }) {
                Icon(Icons.Outlined.Save, contentDescription = null, tint = AlcedoColors.AccentBlue)
                Text(s.savePreset, color = AlcedoColors.AccentBlue)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isFavorite = preset.id in favoriteIds,
                    onApply = { onApply(preset) },
                    onToggleFavorite = { onToggleFavorite(preset) },
                )
            }
        }
    }

    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text(s.savePreset) },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text(s.projectName) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveName.isNotBlank()) onSaveCurrent(saveName)
                        saveDialog = false
                    },
                ) { Text(s.save, color = AlcedoColors.AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { saveDialog = false }) { Text(s.cancel, color = AlcedoColors.TextSecondary) }
            },
        )
    }
}

@Composable
private fun PresetCard(
    preset: PipelinePreset,
    isFavorite: Boolean,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        onClick = onApply,
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DesignTokens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(AlcedoColors.SurfaceElevated, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = preset.name.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.AccentBlue,
                    )
                }
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = DesignTokens.spacingSm),
                    maxLines = 1,
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) AlcedoColors.Amber else AlcedoColors.TextTertiary,
                    )
                }
            }
            Text(
                text = preset.category,
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextTertiary,
            )
        }
    }
}
