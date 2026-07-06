package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.PresetEntry

/**
 * P2-5 批量导出预设同步面板。
 *
 * 选择多张图片后，可以将当前编辑图片的参数或某个预设同步应用到所有选中图片，
 * 然后通过 [onApplyParamsToAll] / [onApplyPresetToAll] 触发批量导出（使用相同参数）。
 */
@Composable
fun BatchEditPanel(
    selectedImages: List<ImageModel>,
    currentParams: PipelineParams,
    presets: List<PresetEntry>,
    onApplyParamsToAll: (PipelineParams) -> Unit,
    onApplyPresetToAll: (PresetEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 已选图片数量 ────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = stringRes { batchSelectedCount }.format(selectedImages.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (selectedImages.isEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringRes { batchSelectImagesFirst },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
            return
        }

        // ── 同步参数按钮 ────────────────────────────────────────────
        Button(
            onClick = { onApplyParamsToAll(currentParams) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringRes { batchSyncParams })
        }

        // ── 预设选择器 ──────────────────────────────────────────────
        PresetBatchSelector(
            presets = presets,
            onPresetSelected = { preset -> onApplyPresetToAll(preset) }
        )

        // ── 单独调整（覆盖） ────────────────────────────────────────
        if (selectedImages.size > 1) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringRes { batchIndividualAdjust },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetBatchSelector(
    presets: List<PresetEntry>,
    onPresetSelected: (PresetEntry) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringRes { batchPickPreset },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringRes { batchApplyPreset })
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name) },
                        onClick = {
                            onPresetSelected(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
