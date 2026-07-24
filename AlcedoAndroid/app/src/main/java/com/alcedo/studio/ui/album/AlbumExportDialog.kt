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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.*
import com.alcedo.studio.i18n.stringRes
import kotlinx.coroutines.launch

/**
 * Mobile-idiomatic batch export dialog rendered as a ModalBottomSheet.
 * Replaces the previous desktop-style AlertDialog that overflowed on small screens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumExportDialog(
    images: List<ImageModel>,
    onDismiss: () -> Unit,
    onExport: (List<Long>, ExportSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableIntStateOf(95) }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var embedIcc by remember { mutableStateOf(true) }
    var includeMetadata by remember { mutableStateOf(true) }
    var maxDimension by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                stringRes { exportImages },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Image selection
            Text(stringRes { exportSelectImages }.format(selectedIds.size, images.size), style = MaterialTheme.typography.labelLarge)

            if (images.isEmpty()) {
                Text(
                    stringRes { noImagesAvailable },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Quick actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { selectedIds = images.map { it.imageId }.toSet() },
                        modifier = Modifier.height(36.dp)
                    ) { Text(stringRes { selectAll }, style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { selectedIds = emptySet() },
                        modifier = Modifier.height(36.dp)
                    ) { Text(stringRes { clear }, style = MaterialTheme.typography.labelSmall) }
                }

                // Image list (show up to 10 for performance)
                images.take(10).forEach { image ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Checkbox(
                            checked = image.imageId in selectedIds,
                            onCheckedChange = {
                                if (it) selectedIds = selectedIds + image.imageId
                                else selectedIds = selectedIds - image.imageId
                            }
                        )
                        Text(
                            image.imageName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (images.size > 10) {
                    Text(
                        stringRes { exportMoreImages }.format(images.size - 10),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Export preset (simplified)
            Text(stringRes { exportPreset }, style = MaterialTheme.typography.labelLarge)
            val presetWebLabel = stringRes { exportPresetWeb }
            val presetPrintLabel = stringRes { exportPresetPrint }
            val presetArchiveLabel = stringRes { exportPresetArchive }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("web", ExportFormat.JPEG, presetWebLabel),
                    Triple("print", ExportFormat.TIFF, presetPrintLabel),
                    Triple("archive", ExportFormat.PNG, presetArchiveLabel)
                ).forEach { (key, fmt, label) ->
                    FilterChip(
                        selected = format == fmt && when (key) {
                            "web" -> quality == 85 && colorSpace == ColorSpace.SRGB
                            "print" -> quality == 100 && colorSpace == ColorSpace.DISPLAY_P3
                            else -> quality == 95
                        },
                        onClick = {
                            format = fmt
                            when (key) {
                                "web" -> { quality = 85; colorSpace = ColorSpace.SRGB }
                                "print" -> { quality = 100; colorSpace = ColorSpace.DISPLAY_P3 }
                                "archive" -> { quality = 95 }
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Format
            Text(stringRes { exportFormat }, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text(stringRes { exportQuality }.format(quality), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Color space
            Text(stringRes { exportColorSpace }, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.ADOBE_RGB, ColorSpace.PROPHOTO_RGB, ColorSpace.REC2020, ColorSpace.ACES)
                    .forEach { cs ->
                        FilterChip(
                            selected = colorSpace == cs,
                            onClick = { colorSpace = cs },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
            }

            // Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Checkbox(checked = embedIcc, onCheckedChange = { embedIcc = it })
                Text(stringRes { exportEmbedIcc }, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                Text(stringRes { exportIncludeMetadata }, style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = maxDimension,
                onValueChange = { maxDimension = it.filter { c -> c.isDigit() } },
                label = { Text(stringRes { exportMaxDimension }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
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
                        onExport(selectedIds.toList(), settings)
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismiss()
                        }
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text(stringRes { exportNImages }.format(selectedIds.size))
                }
            }
        }
    }
}
