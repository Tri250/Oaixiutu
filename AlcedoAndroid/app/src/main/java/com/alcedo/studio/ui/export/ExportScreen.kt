package com.alcedo.studio.ui.export

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.ProgressBar
import com.alcedo.studio.ui.editor.WatermarkPanelDialog
import com.alcedo.studio.ui.theme.AlcedoThemeVariant
import com.alcedo.studio.ui.theme.ThemeManager
import com.alcedo.studio.viewmodel.ExportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    imageId: String,
    viewModel: ExportViewModel = viewModel()
) {
    // Collect progress from ViewModel
    val progress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val batchResult by viewModel.batchResult.collectAsStateWithLifecycle()
    val showWatermarkPanel by viewModel.showWatermarkPanel.collectAsStateWithLifecycle()
    val isExporting = progress.status == ExportService.ExportStatus.EXPORTING

    // UX 修复: 导出进行中时拦截返回,提示用户导出仍在进行
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = isExporting) {
        showExitConfirm = true
    }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringRes { exportInProgressTitle }) },
            text = { Text(stringRes { exportInProgressMessage }) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    navController.popBackStack()
                }) { Text(stringRes { leave }) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(stringRes { continueWaiting }) }
            }
        )
    }

    // Initialize Hasselblad watermark default based on theme variant
    val themeVariant by ThemeManager.themeVariant.collectAsStateWithLifecycle()
    LaunchedEffect(themeVariant) {
        if (viewModel.hassebladWatermark != (themeVariant == AlcedoThemeVariant.HASSELBLAD)) {
            viewModel.hassebladWatermark = themeVariant == AlcedoThemeVariant.HASSELBLAD
        }
    }

    // Snackbar host state for result feedback
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Directory picker for output path
    val context = LocalContext.current
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.outputPath = uri.toString()
        }
    }

    // Multiple image picker for batch export
    var batchImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val batchImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<android.net.Uri> ->
        for (uri in uris) {
            if (uri.toString() !in batchImageUris) {
                batchImageUris = batchImageUris + uri.toString()
            }
        }
    }

    LaunchedEffect(lastResult, batchResult) {
        when {
            lastResult is ExportService.ExportResult.Success -> {
                snackbarHostState.showSnackbar(
                    Strings.current.exportSuccess.format((lastResult as ExportService.ExportResult.Success).filePath)
                )
            }
            lastResult is ExportService.ExportResult.Error -> {
                snackbarHostState.showSnackbar(
                    Strings.current.exportFailed.format((lastResult as ExportService.ExportResult.Error).message)
                )
            }
            batchResult != null -> {
                val br = batchResult
                if (br != null) {
                    snackbarHostState.showSnackbar(
                        Strings.current.exportBatchResult.format(br.successCount.toString(), br.errorCount.toString())
                    )
                }
            }
        }
    }

    if (showWatermarkPanel) {
        WatermarkPanelDialog(
            initialConfig = viewModel.watermarkConfig,
            onConfigChange = { viewModel.updateWatermarkConfig(it) },
            onDismiss = { viewModel.setWatermarkPanelVisible(false) },
            onSavePreset = { config ->
                viewModel.updateWatermarkConfig(config)
                viewModel.setWatermarkPanelVisible(false)
            }
        )
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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

            // DNG-specific note: non-destructive editing with XMP sidecar
            if (viewModel.format == ExportFormat.DNG) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "DNG 非破坏性编辑",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "导出将原样保留原始 DNG 数据，编辑参数以 XMP sidecar（.xmp）" +
                                    "形式写入同名文件，可被 Camera Raw 等工具重新读取。" +
                                    "非 DNG 源（如 NEF/CR2/ARW）的 RAW→DNG 转换需要原生支持。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
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
                listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.ADOBE_RGB, ColorSpace.PROPHOTO_RGB, ColorSpace.REC2020, ColorSpace.ACES)
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

            // Custom Watermark
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes { watermarkTitle }, style = MaterialTheme.typography.bodyMedium)
                        val wm = viewModel.watermarkConfig
                        val summary = if (wm.enabled) {
                            when (wm.type) {
                                com.alcedo.studio.domain.service.WatermarkType.TEXT -> wm.text
                                com.alcedo.studio.domain.service.WatermarkType.IMAGE -> stringRes { watermarkTypeImage }
                                com.alcedo.studio.domain.service.WatermarkType.TEXT_WITH_LOGO -> stringRes { watermarkTypeTextWithLogo }
                            }
                        } else {
                            stringRes { exportWatermarkDescription }
                        }
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = viewModel.watermarkConfig.enabled,
                        onCheckedChange = { checked ->
                            viewModel.updateWatermarkConfig(viewModel.watermarkConfig.copy(enabled = checked))
                        }
                    )
                    IconButton(onClick = { viewModel.setWatermarkPanelVisible(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
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
                placeholder = { Text(stringRes { exportPathDefault }) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringRes { browse })
                    }
                }
            )

            // 使用原始文件名
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.useOriginalFilename,
                    onCheckedChange = { viewModel.useOriginalFilename = it }
                )
                Text(
                    stringRes { exportUseOriginalFilename },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Batch export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringRes { exportBatchExport }, style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = viewModel.showBatchExport.value,
                    onCheckedChange = { viewModel.setBatchExportVisible(it) }
                )
            }
            if (viewModel.showBatchExport.value) {
                Text(
                    stringRes { exportBatchSelectDesc },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (batchImageUris.isNotEmpty()) {
                    Text(
                        stringRes { exportBatchSelected }.format("${batchImageUris.size}"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = {
                        batchImagePickerLauncher.launch(arrayOf("image/*"))
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
                            onCancel = { viewModel.cancelExport() }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // ETA display
                        if (isExporting && progress.etaMillis > 0) {
                            val etaFormatted = formatEta(progress.etaMillis)
                            Text(
                                stringRes { exportEta }.format(etaFormatted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

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

            // 文件大小预估
            val estimatedSize = viewModel.estimateFileSize()
            if (estimatedSize > 0) {
                Text(
                    "预计文件大小：约 ${String.format("%.1f", estimatedSize / 1024.0 / 1024.0)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Export button
            Button(
                onClick = {
                viewModel.resetState()
                if (viewModel.showBatchExport.value && batchImageUris.isNotEmpty()) {
                    viewModel.exportBatchByIds(batchImageUris)
                } else {
                    viewModel.exportSingleById(imageId)
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

            // 导出成功后显示国内社交平台分享选项
            val successResult = lastResult as? ExportService.ExportResult.Success
            if (successResult != null && !isExporting) {
                Spacer(modifier = Modifier.height(16.dp))
                ShareOptionsPanel(exportedUri = successResult.uri)
            }
        }
    }
}

private fun formatEta(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
