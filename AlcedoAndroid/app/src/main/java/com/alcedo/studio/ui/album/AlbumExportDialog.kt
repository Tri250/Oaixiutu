package com.alcedo.studio.ui.album

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumExportDialog(
    images: List<ImageModel>,
    onDismiss: () -> Unit,
    onExport: (List<Long>, ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateOf(mutableSetOf<Long>()) }
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableIntStateOf(95) }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var embedIcc by remember { mutableStateOf(true) }
    var includeMetadata by remember { mutableStateOf(true) }
    var maxDimension by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Export Images") },
        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image selection
                Text("Select Images (${selectedIds.value.size}/${images.count()})", style = MaterialTheme.typography.labelLarge)

                if (images.isEmpty()) {
                    Text(
                        "No images available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Quick actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { selectedIds.value = images.map { it.imageId }.toMutableSet() },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { selectedIds.value = mutableSetOf() },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Image list (show up to 10 for performance)
                    images.take(10).forEach { image ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedIds.value.contains(image.imageId),
                                onCheckedChange = {
                                    if (it) selectedIds.value.add(image.imageId)
                                    else selectedIds.value.remove(image.imageId)
                                }
                            )
                            Text(
                                image.imageName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                    if (images.count() > 10) {
                        Text(
                            "... and ${images.count() - 10} more images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Export preset (simplified)
                Text("Export Preset", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Web" to ExportFormat.JPEG, "Print" to ExportFormat.TIFF, "Archive" to ExportFormat.PNG)
                        .forEach { (name, fmt) ->
                            FilterChip(
                                selected = format == fmt && when (name) {
                                    "Web" -> quality == 85 && colorSpace == ColorSpace.SRGB
                                    "Print" -> quality == 100 && colorSpace == ColorSpace.DISPLAY_P3
                                    else -> quality == 95
                                },
                                onClick = {
                                    format = fmt
                                    when (name) {
                                        "Web" -> { quality = 85; colorSpace = ColorSpace.SRGB }
                                        "Print" -> { quality = 100; colorSpace = ColorSpace.DISPLAY_P3 }
                                        "Archive" -> { quality = 95 }
                                    }
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                }

                // Format
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Quality
                if (format == ExportFormat.JPEG || format == ExportFormat.ULTRA_HDR) {
                    Text("Quality: $quality%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 1f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Color space
                Text("Color Space", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.REC2020, ColorSpace.ACES)
                        .forEach { cs ->
                            FilterChip(
                                selected = colorSpace == cs,
                                onClick = { colorSpace = cs },
                                label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                }

                // Options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = embedIcc, onCheckedChange = { embedIcc = it })
                    Text("Embed ICC Profile", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                    Text("Include Metadata", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = maxDimension,
                    onValueChange = { maxDimension = it.filter { c -> c.isDigit() } },
                    label = { Text("Max dimension (px)") },
                    placeholder = { Text("No limit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
                )

                // Progress
                if (isExporting) {
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                    Text(
                        "Exporting... ${(exportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val settings = ExportSettings(
                        format = format,
                        quality = quality,
                        colorSpace = colorSpace,
                        embedIcc = embedIcc,
                        includeMetadata = includeMetadata,
                        maxDimension = maxDimension.toIntOrNull()
                    )
                    onExport(selectedIds.value.toList(), settings)
                },
                enabled = selectedIds.value.isNotEmpty() && !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text("Export ${selectedIds.value.count()} Image${if (selectedIds.value.count() != 1) "s" else ""}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) { Text("Cancel") }
        }
    )
}
