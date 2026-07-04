package com.alcedo.studio.ui.export

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.ProgressBar
import com.alcedo.studio.viewmodel.ExportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    imageId: String,
    viewModel: ExportViewModel = viewModel()
) {
    // Collect progress from ViewModel
    val progress by viewModel.exportProgress.collectAsState()
    val lastResult by viewModel.lastExportResult.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val isExporting = progress.status == ExportService.ExportStatus.EXPORTING

    // Batch export toggle (local UI state)
    var showBatchExport by remember { mutableStateOf(false) }

    // Show result snackbar
    var showResultSnack by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    LaunchedEffect(lastResult) {
        when (lastResult) {
            is ExportService.ExportResult.Success -> {
                resultMessage = Strings.current.exportSuccess.format((lastResult as ExportService.ExportResult.Success).filePath)
                showResultSnack = true
            }
            is ExportService.ExportResult.Error -> {
                resultMessage = Strings.current.exportFailed.format((lastResult as ExportService.ExportResult.Error).message)
                showResultSnack = true
            }
            null -> {}
        }
    }

    LaunchedEffect(batchProgress.isComplete) {
        if (batchProgress.isComplete) {
            resultMessage = Strings.current.exportBatchResult.format(
                "${batchProgress.completedItems}",
                "${batchProgress.totalItems - batchProgress.completedItems}"
            )
            showResultSnack = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { exportTitle }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                }
            )
        },
        snackbarHost = {
            if (showResultSnack) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showResultSnack = false }) {
                            Text("OK")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(resultMessage, maxLines = 2)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Format
            Text(stringRes { exportFormat }, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(
                        selected = viewModel.settings.value.format == f,
                        onClick = { viewModel.updateFormat(f) },
                        label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Quality
            if (viewModel.settings.value.format == ExportFormat.JPEG || viewModel.settings.value.format == ExportFormat.ULTRA_HDR) {
                Text(stringRes { exportQuality }.format("${viewModel.settings.value.quality}"), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.settings.value.quality.toFloat(),
                    onValueChange = { viewModel.updateQuality(it.toInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bit depth
            if (viewModel.settings.value.format == ExportFormat.PNG || viewModel.settings.value.format == ExportFormat.TIFF) {
                Text(stringRes { exportBitDepth }, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.settings.value.bitDepth == 8,
                        onClick = { viewModel.updateBitDepth(8) },
                        label = { Text(stringRes { export8Bit }) }
                    )
                    FilterChip(
                        selected = viewModel.settings.value.bitDepth == 16,
                        onClick = { viewModel.updateBitDepth(16) },
                        label = { Text(stringRes { export16Bit }) }
                    )
                }
            }

            // Color Space
            Text(stringRes { exportColorSpace }, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.REC2020, ColorSpace.ACES)
                    .forEach { cs ->
                        FilterChip(
                            selected = viewModel.settings.value.colorSpace == cs,
                            onClick = { viewModel.updateColorSpace(cs) },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
            }

            // Options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.settings.value.embedIcc, onCheckedChange = { viewModel.updateEmbedIcc(it) })
                Text(stringRes { exportEmbedIcc }, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.settings.value.includeMetadata, onCheckedChange = { viewModel.updateIncludeMetadata(it) })
                Text(stringRes { exportIncludeMetadata }, style = MaterialTheme.typography.bodyMedium)
            }
            if (viewModel.settings.value.format == ExportFormat.ULTRA_HDR) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = viewModel.settings.value.isHdr, onCheckedChange = { viewModel.updateHdr(it) })
                    Text(stringRes { exportHdrOutput }, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Max dimension (unified)
            OutlinedTextField(
                value = viewModel.settings.value.maxDimension?.toString() ?: "",
                onValueChange = { viewModel.updateMaxDimension(it.filter { c -> c.isDigit() }.toIntOrNull()) },
                label = { Text(stringRes { exportMaxDimension }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
            )

            // Max width
            OutlinedTextField(
                value = viewModel.settings.value.maxWidth?.toString() ?: "",
                onValueChange = { viewModel.updateMaxWidth(it.filter { c -> c.isDigit() }.toIntOrNull()) },
                label = { Text(stringRes { exportMaxWidth }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Max height
            OutlinedTextField(
                value = viewModel.settings.value.maxHeight?.toString() ?: "",
                onValueChange = { viewModel.updateMaxHeight(it.filter { c -> c.isDigit() }.toIntOrNull()) },
                label = { Text(stringRes { exportMaxHeight }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Output path
            OutlinedTextField(
                value = viewModel.settings.value.outputPath,
                onValueChange = { viewModel.updateOutputPath(it) },
                label = { Text(stringRes { exportOutputPath }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { /* Open directory picker */ }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringRes { browse })
                    }
                }
            )

            // Batch export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringRes { exportBatchExport }, style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = showBatchExport,
                    onCheckedChange = { showBatchExport = it }
                )
            }
            if (showBatchExport) {
                Text(
                    stringRes { exportBatchSelectDesc },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel.batchItems.value.isNotEmpty()) {
                    Text(
                        stringRes { exportBatchSelected }.format("${viewModel.batchItems.value.size}"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = { /* Add images from album */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes { exportAddImages })
                }
            }

            // Export progress
            if (isExporting || progress.status == ExportService.ExportStatus.COMPLETED ||
                progress.status == ExportService.ExportStatus.ERROR ||
                progress.status == ExportService.ExportStatus.CANCELLED
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Overall progress
                        ProgressBar(
                            progress = progress.overallProgress,
                            label = if (progress.totalItems > 1) {
                                stringRes { exportOverall }.format("${progress.completedItems}", "${progress.totalItems}")
                            } else {
                                stringRes { exportExporting }
                            },
                            showPercentage = true,
                            onCancel = if (isExporting) ({ viewModel.cancelExport() }) else null
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Per-item progress
                        if (progress.currentItemName.isNotEmpty()) {
                            Text(
                                stringRes { exportCurrent }.format(progress.currentItemName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { progress.currentItemProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Statistics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                "✓ ${progress.successCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "✗ ${progress.failureCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "${progress.completedItems}/${progress.totalItems}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Status
                        when (progress.status) {
                            ExportService.ExportStatus.COMPLETED -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringRes { exportCompleted },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            ExportService.ExportStatus.CANCELLED -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringRes { exportCancelled },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ExportService.ExportStatus.ERROR -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringRes { exportError },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Export button
            Button(
                onClick = {
                    viewModel.clearResult()
                    // For single image export, use the imageId as source path
                    // In production, resolve imageId to actual file path
                    val sourcePath = imageId
                    if (showBatchExport && viewModel.batchItems.value.isNotEmpty()) {
                        viewModel.exportBatch()
                    } else {
                        viewModel.exportImage(sourcePath, PipelineParams())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringRes { exportImage })
            }
        }
    }
}
