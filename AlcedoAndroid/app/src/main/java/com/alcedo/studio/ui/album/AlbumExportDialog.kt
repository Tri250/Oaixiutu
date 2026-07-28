package com.alcedo.studio.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alcedo.studio.data.model.ExportConfig
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Export dialog shown from the album's batch export action. Configures format,
 * quality, max resolution, metadata inclusion and Ultra HDR. Mirrors the editor
 * export sheet but is self-contained so it can run over a multi-selection
 * without an open pipeline.
 */
@Composable
fun AlbumExportDialog(
    config: ExportConfig,
    count: Int,
    onConfigChange: (ExportConfig) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = Strings.res
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = "${s.exportTitle} ($count)",
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextPrimary,
            )

            // Format dropdown
            var formatExpanded by remember { mutableStateOf(false) }
            Text(s.format, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
            Box {
                OutlinedTextField(
                    value = config.format.name,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { formatExpanded = true }) { Text("▾") }
                    },
                )
                DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                    ExportFormat.entries.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text("${fmt.name} (.${fmt.extension})") },
                            onClick = {
                                onConfigChange(config.copy(format = fmt, ultraHdr = fmt == ExportFormat.JPEG && config.ultraHdr))
                                formatExpanded = false
                            },
                        )
                    }
                }
            }

            // Quality (for lossy formats)
            if (config.format == ExportFormat.JPEG || config.format == ExportFormat.WEBP) {
                Text("${s.quality}: ${config.quality}", style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
                Slider(
                    value = config.quality.toFloat(),
                    onValueChange = { onConfigChange(config.copy(quality = it.toInt())) },
                    valueRange = 1f..100f,
                )
                Row {
                    Switch(
                        checked = config.ultraHdr,
                        onCheckedChange = { onConfigChange(config.copy(ultraHdr = it && config.format == ExportFormat.JPEG)) },
                        enabled = config.format == ExportFormat.JPEG,
                    )
                    Text(s.ultraHdr, modifier = Modifier.padding(start = DesignTokens.spacingSm))
                }
            }

            // Max dimension
            Text("${s.maxDimension}: ${if (config.maxDimension == 0) "Original" else config.maxDimension}px",
                style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
            Slider(
                value = if (config.maxDimension == 0) 0f else config.maxDimension.toFloat(),
                onValueChange = { onConfigChange(config.copy(maxDimension = it.toInt())) },
                valueRange = 0f..8000f,
            )

            // Color space
            var csExpanded by remember { mutableStateOf(false) }
            Text(s.colorSpace, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
            Box {
                OutlinedTextField(
                    value = config.colorSpace,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { csExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(expanded = csExpanded, onDismissRequest = { csExpanded = false }) {
                    listOf("sRGB", "Display P3", "Rec.2020", "Adobe RGB").forEach { cs ->
                        DropdownMenuItem(
                            text = { Text(cs) },
                            onClick = { onConfigChange(config.copy(colorSpace = cs)); csExpanded = false },
                        )
                    }
                }
            }

            // Naming pattern
            OutlinedTextField(
                value = config.namingPattern,
                onValueChange = { onConfigChange(config.copy(namingPattern = it.ifBlank { "{name}_edit" })) },
                label = { Text(s.namingPattern) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Metadata
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = config.includeMetadata,
                    onCheckedChange = { onConfigChange(config.copy(includeMetadata = it)) },
                )
                Text(s.includeMetadata, modifier = Modifier.padding(start = DesignTokens.spacingSm))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(s.cancel) }
                Button(onClick = onExport) { Text(s.exportButton) }
            }
        }
    }
}
