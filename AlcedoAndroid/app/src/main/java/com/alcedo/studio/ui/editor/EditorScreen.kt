package com.alcedo.studio.ui.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.viewmodel.EditorViewModel
import com.alcedo.studio.ui.common.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    imageId: String,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModelFactory(imageId))
) {
    val image by viewModel.imageModel.collectAsState()
    val preview by viewModel.previewBitmap.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val params by remember { viewModel.params }
    val history by viewModel.history.collectAsState()
    val workingVersion by remember { viewModel.workingVersion }

    var selectedPanel by remember { mutableStateOf(EditorPanel.BASIC) }
    var isCompareMode by remember { mutableStateOf(false) }
    var comparePosition by remember { mutableFloatStateOf(0.5f) }
    var showExport by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(false) }
    var showHistogram by remember { mutableStateOf(true) }
    var histogramMode by remember { mutableStateOf(HistogramChannel.RGB) }
    var waveformMode by remember { mutableStateOf(WaveformMode.RGB_PARADE) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTablet = configuration.screenWidthDp >= 600

    // Zoom/Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        image?.imageName ?: "Editor",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { isCompareMode = !isCompareMode }) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = "Compare",
                            tint = if (isCompareMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showHistogram = !showHistogram }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Histogram",
                            tint = if (showHistogram) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showScope = !showScope }) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Waveform",
                            tint = if (showScope) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.commitChanges() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isTablet) {
                // Tablet: side-by-side layout
                Row(modifier = Modifier.fillMaxSize()) {
                    // Image preview
                    ImagePreviewArea(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isProcessing = isProcessing,
                        isCompareMode = isCompareMode,
                        comparePosition = comparePosition,
                        scale = scale,
                        offset = offset,
                        onScaleChange = { scale = it },
                        onOffsetChange = { offset = it },
                        showHistogram = showHistogram,
                        histogramMode = histogramMode,
                        onHistogramModeChange = { histogramMode = it },
                        showScope = showScope,
                        waveformMode = waveformMode,
                        onWaveformModeChange = { waveformMode = it },
                        imagePath = image?.imagePath ?: ""
                    )

                    // Editor panels
                    EditorPanelColumn(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight(),
                        selectedPanel = selectedPanel,
                        onPanelSelected = { selectedPanel = it },
                        params = params,
                        onParamsChanged = { viewModel.updateParams(it) },
                        history = history,
                        workingVersion = workingVersion,
                        onSwitchVersion = { viewModel.switchVersion(it) },
                        onCreateVersion = { viewModel.createVersion(it) },
                        onDeleteVersion = { viewModel.deleteVersion(it) },
                        onRenameVersion = { id, name -> viewModel.renameVersion(id, name) },
                        onCloneHistory = { viewModel.cloneHistory() },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() }
                    )
                }
            } else {
                // Phone: vertical layout
                Column(modifier = Modifier.fillMaxSize()) {
                    // Image preview
                    ImagePreviewArea(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f),
                        isProcessing = isProcessing,
                        isCompareMode = isCompareMode,
                        comparePosition = comparePosition,
                        scale = scale,
                        offset = offset,
                        onScaleChange = { scale = it },
                        onOffsetChange = { offset = it },
                        showHistogram = showHistogram,
                        histogramMode = histogramMode,
                        onHistogramModeChange = { histogramMode = it },
                        showScope = showScope,
                        waveformMode = waveformMode,
                        onWaveformModeChange = { waveformMode = it },
                        imagePath = image?.imagePath ?: ""
                    )

                    // Panel tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedPanel.ordinal,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp
                    ) {
                        EditorPanel.entries.forEach { panel ->
                            Tab(
                                selected = selectedPanel == panel,
                                onClick = { selectedPanel = panel },
                                text = { Text(panel.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Editor panel content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedPanel) {
                            EditorPanel.BASIC -> BasicPanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.TONE_CURVE -> ToneCurvePanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.COLOR -> ColorPanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.HSL -> ColorPanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.GEOMETRY -> GeometryPanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.EFFECTS -> EffectsPanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.RAW -> RawDecodePanel(
                                params = params,
                                onParamsChanged = { viewModel.updateParams(it) }
                            )
                            EditorPanel.HISTORY -> HistoryPanel(
                                history = history,
                                onSwitchVersion = { viewModel.switchVersion(it) },
                                onCreateVersion = { viewModel.createVersion(it) },
                                onDeleteVersion = { viewModel.deleteVersion(it) },
                                onRenameVersion = { id, name -> viewModel.renameVersion(id, name) },
                                onCloneHistory = { viewModel.cloneHistory() },
                                onUndo = { viewModel.undo() },
                                onRedo = { viewModel.redo() }
                            )
                        }
                    }
                }
            }

            // Loading overlay
            LoadingOverlay(
                isProcessing,
                message = "Processing...",
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Export dialog
    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onExport = { settings ->
                viewModel.export(settings)
                showExport = false
            },
            imageId = imageId
        )
    }
}

@Composable
private fun ImagePreviewArea(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    isCompareMode: Boolean,
    comparePosition: Float,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    showHistogram: Boolean,
    histogramMode: HistogramChannel,
    onHistogramModeChange: (HistogramChannel) -> Unit,
    showScope: Boolean,
    waveformMode: WaveformMode,
    onWaveformModeChange: (WaveformMode) -> Unit,
    imagePath: String
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0D0D0D))
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                    onScaleChange(newScale)
                    onOffsetChange(Offset(
                        offset.x + pan.x,
                        offset.y + pan.y
                    ))
                }
            }
    ) {
        // Image preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                // Placeholder for image
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath.isNotEmpty()) {
                        Text(
                            "Image Preview",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No image",
                                color = Color.White.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Compare mode slider
        if (isCompareMode) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Before half (left)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(comparePosition)
                        .background(Color.Transparent)
                )
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = (comparePosition * 10000).dp)
                        .background(Color.White)
                )
                // Compare handle
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterStart)
                        .offset(
                            x = (comparePosition * 10000).dp - 16.dp
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.UnfoldMore,
                            contentDescription = "Drag to compare",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                    }
                }
            }

            // Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "Before",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "After",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Histogram overlay
        if (showHistogram) {
            HistogramView(
                histogramData = HistogramData(
                    red = List(256) { (kotlin.math.sin(it * 0.05f) * 100 + 100).toInt() },
                    green = List(256) { (kotlin.math.sin(it * 0.05f + 1f) * 80 + 120).toInt() },
                    blue = List(256) { (kotlin.math.sin(it * 0.05f + 2f) * 90 + 110).toInt() },
                    luminance = List(256) { (kotlin.math.sin(it * 0.04f) * 100 + 100).toInt() }
                ),
                showChannels = histogramMode,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.4f)
                    .padding(8.dp)
            )
        }

        // Waveform overlay
        if (showScope) {
            WaveformView(
                waveformData = WaveformData(
                    red = List(128) { (kotlin.math.sin(it * 0.1f) * 0.3f + 0.5f).coerceIn(0f, 1f) },
                    green = List(128) { (kotlin.math.sin(it * 0.1f + 1f) * 0.3f + 0.5f).coerceIn(0f, 1f) },
                    blue = List(128) { (kotlin.math.sin(it * 0.1f + 2f) * 0.3f + 0.5f).coerceIn(0f, 1f) },
                    luminance = List(128) { (kotlin.math.sin(it * 0.08f) * 0.3f + 0.5f).coerceIn(0f, 1f) }
                ),
                mode = waveformMode,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.4f)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun EditorPanelColumn(
    modifier: Modifier = Modifier,
    selectedPanel: EditorPanel,
    onPanelSelected: (EditorPanel) -> Unit,
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    history: EditHistory?,
    workingVersion: WorkingVersion,
    onSwitchVersion: (String) -> Unit,
    onCreateVersion: (String) -> Unit,
    onDeleteVersion: (String) -> Unit,
    onRenameVersion: (String, String) -> Unit,
    onCloneHistory: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedPanel.ordinal,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp
        ) {
            EditorPanel.entries.forEach { panel ->
                Tab(
                    selected = selectedPanel == panel,
                    onClick = { onPanelSelected(panel) },
                    text = { Text(panel.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedPanel) {
                EditorPanel.BASIC -> BasicPanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.TONE_CURVE -> ToneCurvePanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.COLOR -> ColorPanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.HSL -> ColorPanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.GEOMETRY -> GeometryPanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.EFFECTS -> EffectsPanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.RAW -> RawDecodePanel(
                    params = params,
                    onParamsChanged = onParamsChanged
                )
                EditorPanel.HISTORY -> HistoryPanel(
                    history = history,
                    onSwitchVersion = onSwitchVersion,
                    onCreateVersion = onCreateVersion,
                    onDeleteVersion = onDeleteVersion,
                    onRenameVersion = onRenameVersion,
                    onCloneHistory = onCloneHistory,
                    onUndo = onUndo,
                    onRedo = onRedo
                )
            }
        }
    }
}

@Composable
private fun RawDecodePanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Demosaic Algorithm", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemosaicAlgorithm.entries.forEach { algo ->
                FilterChip(
                    selected = params.rawDecodeParams.demosaicAlgorithm == algo,
                    onClick = {
                        onParamsChanged(
                            params.copy(
                                rawDecodeParams = params.rawDecodeParams.copy(
                                    demosaicAlgorithm = algo
                                )
                            )
                        )
                    },
                    label = { Text(algo.name) }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.highlightReconstruction,
                onCheckedChange = {
                    onParamsChanged(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                highlightReconstruction = it
                            )
                        )
                    )
                }
            )
            Text("Highlight Reconstruction", style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.autoBrightness,
                onCheckedChange = {
                    onParamsChanged(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                autoBrightness = it
                            )
                        )
                    )
                }
            )
            Text("Auto Brightness", style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.useCameraMatrix,
                onCheckedChange = {
                    onParamsChanged(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                useCameraMatrix = it
                            )
                        )
                    )
                }
            )
            Text("Use Camera Matrix", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportSettings) -> Unit,
    imageId: String
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableIntStateOf(95) }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var embedIcc by remember { mutableStateOf(true) }
    var includeMetadata by remember { mutableStateOf(true) }
    var isHdr by remember { mutableStateOf(false) }
    var maxDimension by remember { mutableStateOf("") }
    var bitDepth by remember { mutableIntStateOf(8) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                // Bit depth
                if (format == ExportFormat.PNG || format == ExportFormat.TIFF) {
                    Text("Bit Depth", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = bitDepth == 8,
                            onClick = { bitDepth = 8 },
                            label = { Text("8-bit") }
                        )
                        FilterChip(
                            selected = bitDepth == 16,
                            onClick = { bitDepth = 16 },
                            label = { Text("16-bit") }
                        )
                    }
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = embedIcc, onCheckedChange = { embedIcc = it })
                    Text("Embed ICC Profile", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                    Text("Include Metadata", style = MaterialTheme.typography.bodyMedium)
                }
                if (format == ExportFormat.ULTRA_HDR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHdr, onCheckedChange = { isHdr = it })
                        Text("HDR Output", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = maxDimension,
                    onValueChange = { maxDimension = it.filter { c -> c.isDigit() } },
                    label = { Text("Max dimension (px)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onExport(
                    ExportSettings(
                        format = format,
                        quality = quality,
                        colorSpace = colorSpace,
                        embedIcc = embedIcc,
                        includeMetadata = includeMetadata,
                        maxDimension = maxDimension.toIntOrNull(),
                        isHdr = isHdr
                    )
                )
            }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

enum class EditorPanel(val label: String) {
    BASIC("Basic"),
    TONE_CURVE("Curve"),
    COLOR("Color"),
    HSL("HSL"),
    GEOMETRY("Geometry"),
    EFFECTS("Effects"),
    RAW("RAW"),
    HISTORY("History")
}