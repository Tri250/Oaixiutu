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
    val lastResult by viewModel.lastResult.collectAsState()
    val batchResult by viewModel.batchResult.collectAsState()
    val isExporting = progress.status == ExportService.ExportStatus.EXPORTING

    // Show result snackbar
    var showResultSnack by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    LaunchedEffect(lastResult, batchResult) {
        when {
            lastResult is ExportService.ExportResult.Success -> {
                resultMessage = "导出成功：${(lastResult as ExportService.ExportResult.Success).filePath}"
                showResultSnack = true
            }
            lastResult is ExportService.ExportResult.Error -> {
                resultMessage = "导出失败：${(lastResult as ExportService.ExportResult.Error).message}"
                showResultSnack = true
            }
            batchResult != null -> {
                val br = batchResult!!
                resultMessage = "批量导出完成：成功 ${br.successCount}，失败 ${br.errorCount}"
                showResultSnack = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(
                        selected = viewModel.format == f,
                        onClick = { viewModel.format = f },
                        label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Quality
            if (viewModel.format == ExportFormat.JPEG || viewModel.format == ExportFormat.ULTRA_HDR) {
                Text("Quality: ${viewModel.quality}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.quality.toFloat(),
                    onValueChange = { viewModel.quality = it.toInt() },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bit depth
            if (viewModel.format == ExportFormat.PNG || viewModel.format == ExportFormat.TIFF) {
                Text("Bit Depth", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.bitDepth == 8,
                        onClick = { viewModel.bitDepth = 8 },
                        label = { Text("8-bit") }
                    )
                    FilterChip(
                        selected = viewModel.bitDepth == 16,
                        onClick = { viewModel.bitDepth = 16 },
                        label = { Text("16-bit") }
                    )
                }
            }

            // Color Space
            Text("Color Space", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.REC2020, ColorSpace.ACES)
                    .forEach { cs ->
                        FilterChip(
                            selected = viewModel.colorSpace == cs,
                            onClick = { viewModel.colorSpace = cs },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
            }

            // Options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.embedIcc, onCheckedChange = { viewModel.embedIcc = it })
                Text("Embed ICC Profile", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.includeMetadata, onCheckedChange = { viewModel.includeMetadata = it })
                Text("Include Metadata", style = MaterialTheme.typography.bodyMedium)
            }
            if (viewModel.format == ExportFormat.ULTRA_HDR) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = viewModel.isHdr, onCheckedChange = { viewModel.isHdr = it })
                    Text("HDR Output", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Max dimension (unified)
            OutlinedTextField(
                value = viewModel.maxDimension,
                onValueChange = { viewModel.maxDimension = it.filter { c -> c.isDigit() } },
                label = { Text("Max dimension (px)") },
                placeholder = { Text("No limit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
            )

            // Max width
            OutlinedTextField(
                value = viewModel.maxWidth,
                onValueChange = { viewModel.maxWidth = it.filter { c -> c.isDigit() } },
                label = { Text("Max width (px)") },
                placeholder = { Text("No limit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Max height
            OutlinedTextField(
                value = viewModel.maxHeight,
                onValueChange = { viewModel.maxHeight = it.filter { c -> c.isDigit() } },
                label = { Text("Max height (px)") },
                placeholder = { Text("No limit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Output path
            OutlinedTextField(
                value = viewModel.outputPath,
                onValueChange = { viewModel.outputPath = it },
                label = { Text("Output path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { /* Open directory picker */ }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse")
                    }
                }
            )

            // Batch export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Batch Export", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = viewModel.showBatchExport,
                    onCheckedChange = { viewModel.showBatchExport = it }
                )
            }
            if (viewModel.showBatchExport) {
                Text(
                    "Select images from album to batch export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel.batchImageIds.isNotEmpty()) {
                    Text(
                        "${viewModel.batchImageIds.size} images selected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = { /* Add images from album */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Images")
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
                                "Overall: ${progress.completedItems}/${progress.totalItems}"
                            } else {
                                "Exporting..."
                            },
                            showPercentage = true,
                            onCancel = if (isExporting) ({ viewModel.cancelExport() }) else null
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Per-item progress
                        if (progress.currentItemName.isNotEmpty()) {
                            Text(
                                "Current: ${progress.currentItemName}",
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
                                    "导出完成",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            ExportService.ExportStatus.CANCELLED -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "已取消",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            ExportService.ExportStatus.ERROR -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "导出出错",
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
                    viewModel.resetState()
                    // For single image export, use the imageId as source path
                    // In production, resolve imageId to actual file path
                    val sourcePath = imageId
                    if (viewModel.showBatchExport && viewModel.batchImageIds.isNotEmpty()) {
                        val items = viewModel.batchImageIds.map { id ->
                            ExportService.ExportBatchItem(sourcePath = id)
                        }
                        viewModel.exportBatch(items)
                    } else {
                        viewModel.exportSingle(sourcePath)
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
                Text("Export ${if (viewModel.showBatchExport && viewModel.batchImageIds.isNotEmpty()) "${viewModel.batchImageIds.size} " else ""}Image${if (viewModel.showBatchExport && viewModel.batchImageIds.size != 1) "s" else ""}")
            }
        }
    }
}
