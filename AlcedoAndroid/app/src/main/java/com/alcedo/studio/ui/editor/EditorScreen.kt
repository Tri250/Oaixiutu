package com.alcedo.studio.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.viewmodel.EditorPanel
import com.alcedo.studio.viewmodel.EditorViewModel
import com.alcedo.studio.viewmodel.ScopeType
import com.alcedo.studio.ui.common.LoadingOverlay
import com.alcedo.studio.ui.common.LocalEditorEnabled
import com.alcedo.studio.ui.editor.HlsProfilePanel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    navController: NavController,
    imageId: String,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModelFactory(imageId))
) {
    val image by viewModel.imageModel.collectAsStateWithLifecycle()
    val preview by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    val selectedPanel by viewModel.selectedPanel.collectAsStateWithLifecycle()
    val isCompareMode by viewModel.isCompareMode.collectAsStateWithLifecycle()
    val showExport by viewModel.showExport.collectAsStateWithLifecycle()
    val showScope by viewModel.showScope.collectAsStateWithLifecycle()
    val selectedScopeType by viewModel.selectedScopeType.collectAsStateWithLifecycle()
    val histogramChannel by viewModel.histogramChannel.collectAsStateWithLifecycle()
    val histogramScale by viewModel.histogramScale.collectAsStateWithLifecycle()
    val waveformMode by viewModel.waveformMode.collectAsStateWithLifecycle()
    val gamutOverlay by viewModel.gamutOverlay.collectAsStateWithLifecycle()

    // 未保存修改拦截 + 自动保存
    var showUnsavedDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (viewModel.hasUnsavedChanges()) {
            showUnsavedDialog = true
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startAutoSave()
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAutoSave()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringRes { editorUnsavedTitle }) },
            text = { Text(stringRes { editorUnsavedMessage }) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveVersion()
                    showUnsavedDialog = false
                    navController.popBackStack()
                }) { Text(stringRes { editorSaveAndExit }) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    navController.popBackStack()
                }) { Text(stringRes { editorDiscardAndExit }) }
            }
        )
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // 缩放/平移状态
    val zoomableState = rememberZoomableState()
    val originalBitmap by viewModel.originalBitmap.collectAsStateWithLifecycle()

    // 示波器数据
    var histogramData by remember { mutableStateOf(HistogramData()) }
    var waveformData by remember { mutableStateOf(WaveformData()) }
    var vectorscopeData by remember { mutableStateOf(VectorscopeData()) }
    var chromaticityData by remember { mutableStateOf(ChromaticityData()) }
    var isScopeComputing by remember { mutableStateOf(false) }

    // 位图变化时计算示波器数据
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
                        image?.imageName ?: stringRes { editorTitle },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            if (!isTablet) {
                EditorBottomToolbar(
                    isCompareMode = isCompareMode,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onAutoEnhance = { viewModel.autoEnhance() },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onCompare = { viewModel.toggleCompareMode() },
                    onScope = { viewModel.toggleShowScope() },
                    onSave = { viewModel.saveVersion() },
                    onExport = { viewModel.toggleShowExport() }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isTablet) {
                // 平板：左右分栏布局
                Row(modifier = Modifier.fillMaxSize()) {
                    // 图片预览
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

                    // 编辑器面板
                    CompositionLocalProvider(LocalEditorEnabled provides !isProcessing) {
                        EditorPanelColumn(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(),
                            selectedPanel = selectedPanel,
                            onPanelSelected = { viewModel.updateSelectedPanel(it) },
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                // 手机：垂直布局，预览/面板可调
                Column(modifier = Modifier.fillMaxSize()) {
                    // 图片预览 – 手机上占用更多空间以提升可见性
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

                    // 面板标签（与下方可滑动 Pager 同步）
                    val pagerState = rememberPagerState(
                        initialPage = selectedPanel.ordinal,
                        pageCount = { EditorPanel.entries.size }
                    )
                    // 保持标签选择与 Pager 页面同步
                    LaunchedEffect(selectedPanel) {
                        if (pagerState.currentPage != selectedPanel.ordinal) {
                            pagerState.animateScrollToPage(selectedPanel.ordinal)
                        }
                    }
                    LaunchedEffect(pagerState.currentPage) {
                        val synced = EditorPanel.entries[pagerState.currentPage]
                        if (synced != selectedPanel) viewModel.updateSelectedPanel(synced)
                    }

                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        EditorPanel.entries.forEach { panel ->
                            Tab(
                                selected = pagerState.currentPage == panel.ordinal,
                                onClick = { viewModel.updateSelectedPanel(panel) },
                                text = { Text(stringRes(panel.labelKey), style = MaterialTheme.typography.labelSmall) },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 可滑动的编辑器面板内容（HorizontalPager 替代纯标签导航）
                    CompositionLocalProvider(LocalEditorEnabled provides !isProcessing) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f),
                        pageSpacing = 8.dp
                    ) { pageIndex ->
                        val panel = EditorPanel.entries[pageIndex]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (panel) {
                                EditorPanel.BASIC -> BasicPanel(viewModel = viewModel)
                                EditorPanel.TONE_CURVE -> ToneCurvePanel(viewModel = viewModel)
                                EditorPanel.COLOR -> ColorPanel(viewModel = viewModel)
                                EditorPanel.HSL -> {
                                    val params by remember { viewModel.params }
                                    HlsProfilePanel(
                                        params = params,
                                        onParamsChanged = { viewModel.updateParams(it) }
                                    )
                                }
                                EditorPanel.GEOMETRY -> GeometryPanel(viewModel = viewModel)
                                EditorPanel.EFFECTS -> EffectsPanel(viewModel = viewModel)
                                EditorPanel.RAW -> RawDecodePanel(viewModel = viewModel)
                                EditorPanel.HISTORY -> HistoryPanel(viewModel = viewModel)
                                EditorPanel.DISPLAY_TRANSFORM -> DisplayTransformPanel(
                                    params = viewModel.params.value,
                                    onParamsChanged = { viewModel.updateParams(it) }
                                )
                                EditorPanel.LMT -> LmtPanel(
                                    params = viewModel.params.value,
                                    onParamsChanged = { viewModel.updateParams(it) }
                                )
                                EditorPanel.INSPECTOR -> ImageInspectorPanel(
                                    image = viewModel.imageModel.value
                                )
                            }
                        }
                    }
                    } // CompositionLocalProvider
                }
            }

            // 示波器分析浮层
            if (showScope) {
                ScopeAnalyzerPanel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(if (isTablet) 0.35f else 0.5f)
                        .padding(8.dp),
                    selectedScopeType = selectedScopeType,
                    onScopeTypeSelected = { viewModel.updateSelectedScopeType(it) },
                    histogramData = histogramData,
                    histogramChannel = histogramChannel,
                    onHistogramChannelChange = { viewModel.updateHistogramChannel(it) },
                    histogramScale = histogramScale,
                    onHistogramScaleChange = { viewModel.updateHistogramScale(it) },
                    waveformData = waveformData,
                    waveformMode = waveformMode,
                    onWaveformModeChange = { viewModel.updateWaveformMode(it) },
                    vectorscopeData = vectorscopeData,
                    chromaticityData = chromaticityData,
                    gamutOverlay = gamutOverlay,
                    onGamutOverlayChange = { viewModel.updateGamutOverlay(it) },
                    isComputing = isScopeComputing,
                    onClose = { viewModel.dismissShowScope() }
                )
            }

            // 后台任务进度浮层
            LoadingOverlay(
                isProcessing,
                message = stringRes { editorProcessing },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // 导出对话框
    if (showExport) {
        ExportDialog(
            onDismiss = { viewModel.dismissExport() },
            onExport = { settings ->
                viewModel.export(settings)
                viewModel.dismissExport()
            },
            imageId = imageId
        )
    }
}

// ── 示波器分析面板 ──────────────────────────────────────────────────

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
            // 头部示波器类型标签
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
                                stringRes(scopeType.labelKey),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringRes { close },
                        modifier = Modifier.size(24.dp),
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

            // 示波器内容
            when (selectedScopeType) {
                ScopeType.HISTOGRAM -> {
                    HistogramView(
                        histogramData = histogramData,
                        showChannels = histogramChannel,
                        scale = histogramScale,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 通道选择行
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

// ── 图片预览区域 ────────────────────────────────────────────────────

@Composable
private fun ImagePreviewArea(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    isCompareMode: Boolean,
    previewBitmap: ImageBitmap?,
    originalBitmap: ImageBitmap?,
    zoomableState: ZoomableState
) {
    // 长按对比 — 长按显示原图（修改前），松开恢复
    var showOriginalOverlay by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color(0xFF0D0D0D))
            .pointerInput(isCompareMode) {
                // 对比模式下由 CompareView 负责交互，不拦截长按
                if (isCompareMode) return@pointerInput
                detectTapGestures(
                    onLongPress = {
                        // 长按显示原图
                        showOriginalOverlay = true
                    },
                    onPress = {
                        // 松开（或手势被取消）后恢复编辑后预览
                        tryAwaitRelease()
                        showOriginalOverlay = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = when {
                isProcessing -> "loading"
                isCompareMode && originalBitmap != null && previewBitmap != null -> "compare"
                else -> "preview"
            },
            animationSpec = tween(300),
            label = "previewCrossfade"
        ) { state ->
            when (state) {
                "loading" -> {
                    CircularProgressIndicator(color = Color.White)
                }
                "compare" -> {
                    CompareView(
                        originalBitmap = originalBitmap!!,
                        editedBitmap = previewBitmap!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    ZoomableImageView(
                        imageBitmap = previewBitmap,
                        modifier = Modifier.fillMaxSize(),
                        zoomableState = zoomableState
                    )
                }
            }
        }

        // 长按时叠加显示原图
        if (showOriginalOverlay && originalBitmap != null) {
            Image(
                bitmap = originalBitmap,
                contentDescription = stringRes { compareBefore },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
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
                    stringRes { editorNoImage },
                    color = Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ── 编辑器面板列 ────────────────────────────────────────────────────

@Composable
private fun EditorPanelColumn(
    modifier: Modifier = Modifier,
    selectedPanel: EditorPanel,
    onPanelSelected: (EditorPanel) -> Unit,
    viewModel: EditorViewModel
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
                    text = { Text(stringRes(panel.labelKey), style = MaterialTheme.typography.labelSmall) }
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
                EditorPanel.BASIC -> BasicPanel(viewModel = viewModel)
                EditorPanel.TONE_CURVE -> ToneCurvePanel(viewModel = viewModel)
                EditorPanel.COLOR -> ColorPanel(viewModel = viewModel)
                EditorPanel.HSL -> {
                    val params by remember { viewModel.params }
                    HlsProfilePanel(
                        params = params,
                        onParamsChanged = { viewModel.updateParams(it) }
                    )
                }
                EditorPanel.GEOMETRY -> GeometryPanel(viewModel = viewModel)
                EditorPanel.EFFECTS -> EffectsPanel(viewModel = viewModel)
                EditorPanel.RAW -> RawDecodePanel(viewModel = viewModel)
                EditorPanel.HISTORY -> HistoryPanel(viewModel = viewModel)
                EditorPanel.DISPLAY_TRANSFORM -> DisplayTransformPanel(
                    params = viewModel.params.value,
                    onParamsChanged = { viewModel.updateParams(it) }
                )
                EditorPanel.LMT -> LmtPanel(
                    params = viewModel.params.value,
                    onParamsChanged = { viewModel.updateParams(it) }
                )
                EditorPanel.INSPECTOR -> ImageInspectorPanel(
                    image = viewModel.imageModel.value
                )
            }
        }
    }
}

@Composable
private fun RawDecodePanel(
    viewModel: EditorViewModel
) {
    val params by remember { viewModel.params }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringRes { editorDemosaicAlgorithm }, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemosaicAlgorithm.entries.forEach { algo ->
                FilterChip(
                    selected = params.rawDecodeParams.demosaicAlgorithm == algo,
                    onClick = {
                        viewModel.updateParams(
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
                    viewModel.updateParams(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                highlightReconstruction = it
                            )
                        )
                    )
                }
            )
            Text(stringRes { editorHighlightReconstruction }, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.autoBrightness,
                onCheckedChange = {
                    viewModel.updateParams(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                autoBrightness = it
                            )
                        )
                    )
                }
            )
            Text(stringRes { editorAutoBrightness }, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.useCameraMatrix,
                onCheckedChange = {
                    viewModel.updateParams(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                useCameraMatrix = it
                            )
                        )
                    )
                }
            )
            Text(stringRes { editorUseCameraMatrix }, style = MaterialTheme.typography.bodyMedium)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes { exportImage }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 格式
                Text(stringRes { exportFormat }, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 质量
                if (format == ExportFormat.JPEG || format == ExportFormat.ULTRA_HDR) {
                    Text(stringRes { exportQuality }.format("$quality"), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 1f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 色彩空间
                Text(stringRes { exportColorSpace }, style = MaterialTheme.typography.labelLarge)
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
                    Text(stringRes { exportEmbedIcc }, style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                    Text(stringRes { exportIncludeMetadata }, style = MaterialTheme.typography.bodyMedium)
                }
                if (format == ExportFormat.ULTRA_HDR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHdr, onCheckedChange = { isHdr = it })
                        Text(stringRes { exportHdrOutput }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = maxDimension,
                    onValueChange = { maxDimension = it.filter { c -> c.isDigit() } },
                    label = { Text(stringRes { exportMaxDimension }) },
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
                Text(stringRes { export })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

@Composable
private fun EditorBottomToolbar(
    isCompareMode: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onAutoEnhance: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompare: () -> Unit,
    onScope: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 智能优化 — 一键自动增强（置于工具栏首位，Undo 之前）
            EditorToolbarButton(
                icon = Icons.Default.AutoAwesome,
                label = stringRes { editorAutoEnhance },
                onClick = onAutoEnhance,
                tint = MaterialTheme.colorScheme.primary
            )
            EditorToolbarButton(
                icon = Icons.Default.Undo,
                label = stringRes { editorUndo },
                enabled = canUndo,
                onClick = onUndo
            )
            EditorToolbarButton(
                icon = Icons.Default.Redo,
                label = stringRes { editorRedo },
                enabled = canRedo,
                onClick = onRedo
            )
            EditorToolbarButton(
                icon = Icons.Default.Compare,
                label = stringRes { editorCompare },
                selected = isCompareMode,
                onClick = onCompare
            )
            EditorToolbarButton(
                icon = Icons.Default.BarChart,
                label = stringRes { editorScopeAnalyzer },
                onClick = onScope
            )
            EditorToolbarButton(
                icon = Icons.Default.Save,
                label = stringRes { editorSave },
                onClick = onSave
            )
            EditorToolbarButton(
                icon = Icons.Default.Share,
                label = stringRes { editorExport },
                onClick = onExport,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditorToolbarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (!enabled) tint.copy(alpha = 0.38f)
                else if (selected) MaterialTheme.colorScheme.primary
                else tint
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (!enabled) tint.copy(alpha = 0.38f)
                else if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
