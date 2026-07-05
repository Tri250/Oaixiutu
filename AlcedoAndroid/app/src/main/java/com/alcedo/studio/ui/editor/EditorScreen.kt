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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.viewmodel.EditorViewModel
import com.alcedo.studio.ui.common.LoadingOverlay

enum class ScopeType(val label: String) {
    HISTOGRAM("Histogram"),
    WAVEFORM("Waveform"),
    VECTORSCOPE("Vectorscope"),
    CHROMATICITY("Chromaticity")
}

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
    val isCompareMode by viewModel.isCompareMode.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(false) }
    var selectedScopeType by remember { mutableStateOf(ScopeType.HISTOGRAM) }
    var histogramChannel by remember { mutableStateOf(HistogramChannel.RGB) }
    var histogramScale by remember { mutableStateOf(HistogramScale.LINEAR) }
    var waveformMode by remember { mutableStateOf(WaveformMode.RGB_PARADE) }
    var gamutOverlay by remember { mutableStateOf(setOf(GamutOverlay.SRGB)) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Zoom/Pan state
    val zoomableState = rememberZoomableState()
    val originalBitmap by viewModel.originalBitmap.collectAsState()

    // Scope data
    var histogramData by remember { mutableStateOf(HistogramData()) }
    var waveformData by remember { mutableStateOf(WaveformData()) }
    var vectorscopeData by remember { mutableStateOf(VectorscopeData()) }
    var chromaticityData by remember { mutableStateOf(ChromaticityData()) }
    var isScopeComputing by remember { mutableStateOf(false) }

    // Compute scope data when bitmap changes
    LaunchedEffect(preview) {
        preview?.let { bitmap ->
            isScopeComputing = true
            try {
                histogramData = ScopeAnalyzer.computeHistogram(bitmap)
                waveformData = ScopeAnalyzer.computeWaveform(bitmap)
                vectorscopeData = ScopeAnalyzer.computeVectorscope(bitmap)
                chromaticityData = ScopeAnalyzer.computeChromaticity(bitmap)
            } finally {
                isScopeComputing = false
            }
        }
    }

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
                    IconButton(onClick = { viewModel.toggleCompareMode() }) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = "Compare",
                            tint = if (isCompareMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showScope = !showScope }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Scope Analyzer",
                            tint = if (showScope) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.saveVersion() }) {
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
                        previewBitmap = preview?.asImageBitmap(),
                        originalBitmap = originalBitmap?.asImageBitmap(),
                        zoomableState = zoomableState
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
                // Phone: vertical layout with adjustable preview/panel split
                Column(modifier = Modifier.fillMaxSize()) {
                    // Image preview – takes more space on phone for better visibility
                    ImagePreviewArea(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f),
                        isProcessing = isProcessing,
                        isCompareMode = isCompareMode,
                        previewBitmap = preview?.asImageBitmap(),
                        originalBitmap = originalBitmap?.asImageBitmap(),
                        zoomableState = zoomableState
                    )

                    // Panel tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedPanel.ordinal,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        EditorPanel.entries.forEach { panel ->
                            Tab(
                                selected = selectedPanel == panel,
                                onClick = { selectedPanel = panel },
                                text = { Text(panel.label, style = MaterialTheme.typography.labelSmall) },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Editor panel content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // Scope Analyzer overlay panel
            if (showScope) {
                ScopeAnalyzerPanel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(if (isTablet) 0.35f else 0.5f)
                        .padding(8.dp),
                    selectedScopeType = selectedScopeType,
                    onScopeTypeSelected = { selectedScopeType = it },
                    histogramData = histogramData,
                    histogramChannel = histogramChannel,
                    onHistogramChannelChange = { histogramChannel = it },
                    histogramScale = histogramScale,
                    onHistogramScaleChange = { histogramScale = it },
                    waveformData = waveformData,
                    waveformMode = waveformMode,
                    onWaveformModeChange = { waveformMode = it },
                    vectorscopeData = vectorscopeData,
                    chromaticityData = chromaticityData,
                    gamutOverlay = gamutOverlay,
                    onGamutOverlayChange = { gamutOverlay = it },
                    isComputing = isScopeComputing,
                    onClose = { showScope = false }
                )
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

// ── Scope Analyzer Panel ──────────────────────────────────────────────────

@Composable
private fun ScopeAnalyzerPanel(
    modifier: Modifier = Modifier,
    selectedScopeType: ScopeType,
    onScopeTypeSelected: (ScopeType) -> Unit,
    histogramData: HistogramData,
    histogramChannel: HistogramChannel,
    onHistogramChannelChange: (HistogramChannel) -> Unit,
    histogramScale: HistogramScale,
    onHistogramScaleChange: (HistogramScale) -> Unit,
    waveformData: WaveformData,
    waveformMode: WaveformMode,
    onWaveformModeChange: (WaveformMode) -> Unit,
    vectorscopeData: VectorscopeData,
    chromaticityData: ChromaticityData,
    gamutOverlay: Set<GamutOverlay>,
    onGamutOverlayChange: (Set<GamutOverlay>) -> Unit,
    isComputing: Boolean,
    onClose: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color(0xE0121212),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Header with scope type tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScopeType.entries.forEach { scopeType ->
                    FilterChip(
                        selected = selectedScopeType == scopeType,
                        onClick = { onScopeTypeSelected(scopeType) },
                        label = {
                            Text(
                                scopeType.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            if (isComputing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // Scope content
            when (selectedScopeType) {
                ScopeType.HISTOGRAM -> {
                    HistogramView(
                        histogramData = histogramData,
                        showChannels = histogramChannel,
                        scale = histogramScale,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Channel selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HistogramChannel.entries.forEach { ch ->
                            val color = when (ch) {
                                HistogramChannel.RGB -> Color.White
                                HistogramChannel.RED -> Color.Red
                                HistogramChannel.GREEN -> Color.Green
                                HistogramChannel.BLUE -> Color(0xFF4488FF)
                                HistogramChannel.LUMINANCE -> Color.White
                            }
                            FilterChip(
                                selected = histogramChannel == ch,
                                onClick = { onHistogramChannelChange(ch) },
                                label = {
                                    Text(
                                        ch.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (histogramChannel == ch) color
                                        else Color.Gray
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HistogramScale.entries.forEach { sc ->
                            FilterChip(
                                selected = histogramScale == sc,
                                onClick = { onHistogramScaleChange(sc) },
                                label = {
                                    Text(
                                        sc.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
                ScopeType.WAVEFORM -> {
                    WaveformView(
                        waveformData = waveformData,
                        mode = waveformMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        WaveformMode.entries.forEach { m ->
                            FilterChip(
                                selected = waveformMode == m,
                                onClick = { onWaveformModeChange(m) },
                                label = {
                                    Text(
                                        m.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
                ScopeType.VECTORSCOPE -> {
                    VectorscopeView(
                        vectorscopeData = vectorscopeData,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ScopeType.CHROMATICITY -> {
                    ChromaticityView(
                        chromaticityData = chromaticityData,
                        showGamut = gamutOverlay,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GamutOverlay.entries.forEach { g ->
                            val color = when (g) {
                                GamutOverlay.SRGB -> Color.White
                                GamutOverlay.P3 -> Color(0xFF4FC3F7)
                                GamutOverlay.REC2020 -> Color(0xFFFFB74D)
                                GamutOverlay.ACES -> Color(0xFFCE93D8)
                            }
                            FilterChip(
                                selected = g in gamutOverlay,
                                onClick = {
                                    onGamutOverlayChange(
                                        if (g in gamutOverlay) gamutOverlay - g
                                        else gamutOverlay + g
                                    )
                                },
                                label = {
                                    Text(
                                        g.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (g in gamutOverlay) color else Color.Gray
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Image Preview Area ────────────────────────────────────────────────────

@Composable
private fun ImagePreviewArea(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    isCompareMode: Boolean,
    previewBitmap: ImageBitmap?,
    originalBitmap: ImageBitmap?,
    zoomableState: ZoomableState
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0D0D0D)),
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator(color = Color.White)
        } else if (isCompareMode && originalBitmap != null && previewBitmap != null) {
            CompareView(
                originalBitmap = originalBitmap,
                editedBitmap = previewBitmap,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ZoomableImageView(
                imageBitmap = previewBitmap,
                modifier = Modifier.fillMaxSize(),
                zoomableState = zoomableState
            )
        }

        if (previewBitmap == null && !isProcessing) {
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

// ── Editor Panel Column ───────────────────────────────────────────────────

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
