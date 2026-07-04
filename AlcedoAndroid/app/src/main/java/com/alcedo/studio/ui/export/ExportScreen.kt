package com.alcedo.studio.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    viewModel: ExportViewModel = hiltViewModel()
) {
    // Collect progress from ViewModel
    val progress by viewModel.exportProgress.collectAsState()
    val lastResult by viewModel.lastResult.collectAsState()
    val batchResult by viewModel.batchResult.collectAsState()
    val isExporting = progress.status == ExportService.ExportStatus.EXPORTING

    // Show result snackbar
    var showResultSnack by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    // SAF directory picker for output path
    val context = LocalContext.current
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val flags = context.contentResolver.persistableUriPermissions
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.outputPath = treeUri.toString()
        }
    }

    // Photo picker for batch image selection
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val imageIds = uris.map { it.toString() }
            viewModel.addBatchImageIds(imageIds)
        }
    }

    LaunchedEffect(lastResult, batchResult) {
        when {
            lastResult is ExportService.ExportResult.Success -> {
                resultMessage = Strings.current.exportSuccess.format((lastResult as ExportService.ExportResult.Success).filePath)
                showResultSnack = true
            }
            lastResult is ExportService.ExportResult.Error -> {
                resultMessage = Strings.current.exportFailed.format((lastResult as ExportService.ExportResult.Error).message)
                showResultSnack = true
            }
            batchResult != null -> {
                val br = batchResult ?: return@LaunchedEffect
                resultMessage = Strings.current.exportBatchResult.format(br.successCount.toString(), br.errorCount.toString())
                showResultSnack = true
            }
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
                        selected = viewModel.format == f,
                        onClick = { viewModel.format = f },
                        label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Quality
            if (viewModel.format == ExportFormat.JPEG || viewModel.format == ExportFormat.ULTRA_HDR) {
                Text(stringRes { exportQuality }.format("${viewModel.quality}"), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = viewModel.quality.toFloat(),
                    onValueChange = { viewModel.quality = it.toInt() },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bit depth
            if (viewModel.format == ExportFormat.PNG || viewModel.format == ExportFormat.TIFF) {
                Text(stringRes { exportBitDepth }, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.bitDepth == 8,
                        onClick = { viewModel.bitDepth = 8 },
                        label = { Text(stringRes { export8Bit }) }
                    )
                    FilterChip(
                        selected = viewModel.bitDepth == 16,
                        onClick = { viewModel.bitDepth = 16 },
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
                            selected = viewModel.colorSpace == cs,
                            onClick = { viewModel.colorSpace = cs },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
            }

            // Options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.embedIcc, onCheckedChange = { viewModel.embedIcc = it })
                Text(stringRes { exportEmbedIcc }, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.includeMetadata, onCheckedChange = { viewModel.includeMetadata = it })
                Text(stringRes { exportIncludeMetadata }, style = MaterialTheme.typography.bodyMedium)
            }
            if (viewModel.format == ExportFormat.ULTRA_HDR) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = viewModel.isHdr, onCheckedChange = { viewModel.isHdr = it })
                    Text(stringRes { exportHdrOutput }, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Max dimension (unified)
            OutlinedTextField(
                value = viewModel.maxDimension,
                onValueChange = { viewModel.maxDimension = it.filter { c -> c.isDigit() } },
                label = { Text(stringRes { exportMaxDimension }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
            )

            // Max width
            OutlinedTextField(
                value = viewModel.maxWidth,
                onValueChange = { viewModel.maxWidth = it.filter { c -> c.isDigit() } },
                label = { Text(stringRes { exportMaxWidth }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Max height
            OutlinedTextField(
                value = viewModel.maxHeight,
                onValueChange = { viewModel.maxHeight = it.filter { c -> c.isDigit() } },
                label = { Text(stringRes { exportMaxHeight }) },
                placeholder = { Text(stringRes { exportNoLimit }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) }
            )

            // Output path
            OutlinedTextField(
                value = viewModel.outputPath,
                onValueChange = { viewModel.outputPath = it },
                label = { Text(stringRes { exportOutputPath }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
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
                    checked = viewModel.showBatchExport,
                    onCheckedChange = { viewModel.showBatchExport = it }
                )
            }
            if (viewModel.showBatchExport) {
                Text(
                    stringRes { exportBatchSelectDesc },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (viewModel.batchImageIds.isNotEmpty()) {
                    Text(
                        stringRes { exportBatchSelected }.format("${viewModel.batchImageIds.size}"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(arrayOf("image/*"))
                    },
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
                Text(stringRes { exportImage })
            }
        }
    }
}
