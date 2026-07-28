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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
 * Preset panel. Grid of presets grouped by category with a favourite toggle,
 * apply action, and category filter chips. "Save current as preset" opens a
 * dialog to name the new preset. Long-press on user presets allows deletion.
 */
@Composable
fun PresetPanel(
    presets: List<PipelinePreset>,
    favorites: List<PipelinePreset>,
    onApply: (PipelinePreset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onToggleFavorite: (PipelinePreset) -> Unit,
    onDeletePreset: (PipelinePreset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var saveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PipelinePreset?>(null) }
    val favoriteIds = favorites.map { it.id }.toHashSet()

    // Category filter
    val categories by remember(presets) {
        derivedStateOf {
            val cats = presets.map { it.category }.distinct()
            listOf("All") + cats
        }
    }
    var selectedCategory by remember { mutableStateOf("All") }
    val filteredPresets by remember(presets, selectedCategory) {
        derivedStateOf {
            if (selectedCategory == "All") presets
            else presets.filter { it.category == selectedCategory }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.presets) {
            OutlinedButton(onClick = { saveDialog = true; saveName = "" }) {
                Icon(Icons.Outlined.Save, contentDescription = null, tint = AlcedoColors.AccentBlue, modifier = Modifier.size(16.dp))
                Text(s.savePreset, color = AlcedoColors.AccentBlue)
            }
        }

        // Category filter chips
        if (categories.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = cat == selectedCategory,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, maxLines = 1) },
                    )
                }
            }
        }

        // Favorites section (always visible)
        val favPresets = favorites
        if (favPresets.isNotEmpty() && selectedCategory == "All") {
            SectionHeader(title = "★ ${s.presets}")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(favPresets, key = { "fav_${it.id}" }) { preset ->
                    PresetCard(
                        preset = preset,
                        isFavorite = true,
                        onApply = { onApply(preset) },
                        onToggleFavorite = { onToggleFavorite(preset) },
                        onDelete = { deleteTarget = preset },
                    )
                }
            }
        }

        // All/filtered presets
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filteredPresets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isFavorite = preset.id in favoriteIds,
                    onApply = { onApply(preset) },
                    onToggleFavorite = { onToggleFavorite(preset) },
                    onDelete = { deleteTarget = preset },
                )
            }
        }
    }

    // Save dialog
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

    // Delete confirmation dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(s.delete) },
            text = { Text("${target.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(target)
                        deleteTarget = null
                    },
                ) { Text(s.delete, color = AlcedoColors.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(s.cancel, color = AlcedoColors.TextSecondary) }
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
    onDelete: () -> Unit,
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
                if (!preset.isBuiltIn) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = AlcedoColors.TextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
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
