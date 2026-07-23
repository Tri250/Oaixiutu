package com.alcedo.studio.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.alcedo.studio.ui.common.AlcedoEasing
import com.alcedo.studio.ui.editor.HlsProfilePanel
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoGlass
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    val imageLoadError by viewModel.imageLoadError.collectAsStateWithLifecycle()
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
    val compareMode by viewModel.compareMode.collectAsStateWithLifecycle()
    val overlayOpacity by viewModel.overlayOpacity.collectAsStateWithLifecycle()
    val showClippingWarning by viewModel.showClippingWarning.collectAsStateWithLifecycle()

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

    // Snackbar 统一反馈: 收集 EditorViewModel 的事件流
    val editorSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            editorSnackbarHostState.showSnackbar(message)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAutoSave()
        }
    }

    // S2 修复: 监听系统内存压力,低内存时释放非关键位图资源
    val context = LocalContext.current
    DisposableEffect(context) {
        val componentCallback = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                viewModel.onTrimMemory(level)
            }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                viewModel.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
        }
        context.registerComponentCallbacks(componentCallback)
        onDispose {
            context.unregisterComponentCallbacks(componentCallback)
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

    // 专注模式 — RapidRAW 风格：仅展开当前活跃的小节，其余自动折叠
    val focusMode = remember { FocusModeState() }

    // 位图变化时计算示波器数据 — 在后台线程执行，避免阻塞 UI
    // UX 修复: 仅在示波器面板可见时计算,避免拖动滑块时全量重算导致卡顿
    // S2 修复: 添加防抖 + 取消前次计算,避免滑块快速拖动时竞态和 ANR
    LaunchedEffect(preview, showScope) {
        if (!showScope) return@LaunchedEffect
        preview?.let { bitmap ->
            // 延迟防抖,等待滑块停止后再计算
            delay(300)
            // 再次检查 scope 是否仍然可见
            if (!showScope) return@LaunchedEffect
            isScopeComputing = true
            try {
                withContext(Dispatchers.Default) {
                    histogramData = ScopeAnalyzer.computeHistogram(bitmap)
                    waveformData = ScopeAnalyzer.computeWaveform(bitmap)
                    vectorscopeData = ScopeAnalyzer.computeVectorscope(bitmap)
                    chromaticityData = ScopeAnalyzer.computeChromaticity(bitmap)
                }
            } catch (e: CancellationException) {
                // 正常取消,不处理
            } catch (e: Exception) {
                android.util.Log.e("EditorScreen", "Scope computation failed", e)
            } finally {
                isScopeComputing = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(editorSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        image?.imageName ?: stringRes { editorTitle },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    // P3-4 修复: navigationIcon 必须与 BackHandler 一致,检查未保存修改
                    IconButton(onClick = {
                        if (viewModel.hasUnsavedChanges()) {
                            showUnsavedDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringRes { back },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = stringRes { editorUndo },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = stringRes { editorRedo },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleCompareMode() }) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = stringRes { editorCompare },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (isCompareMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 示波器 — 与专注模式按钮相邻，便于平板与手机共用同一入口
                    IconButton(onClick = { viewModel.toggleShowScope() }) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = stringRes { editorScopeAnalyzer },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (showScope) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 剪裁预警开关（类似 Lightroom 的 J 键功能）
                    IconButton(onClick = { viewModel.toggleClippingWarning() }) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringRes { editorClippingWarning },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (showClippingWarning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 专注模式开关 — RapidRAW 风格：自动折叠其它小节，仅保留当前活跃小节
                    IconButton(onClick = { focusMode.toggle() }) {
                        Icon(
                            Icons.Default.CenterFocusStrong,
                            contentDescription = stringRes { editorFocusMode },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = if (focusMode.enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.saveVersion() }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = stringRes { editorSave },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.toggleShowExport() }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringRes { editorExport },
                            modifier = Modifier.size(AlcedoIconSize.lg),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = AlcedoGlass.toolbarOpacity),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                // 2026: 顶部工具栏增加微妙的底部边框
                modifier = Modifier.then(
                    Modifier.drawWithContent {
                        drawContent()
                        drawLine(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }
                )
            )
        },
        bottomBar = {
            // 专注模式开启时隐藏底部工具栏，留出更多空间给图片与当前面板
            if (!isTablet && !focusMode.enabled) {
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
            // P2-7 色彩空间工作流提示：在工具栏下方显示 输入→工作→输出 色彩空间链路
            val currentParams = viewModel.params.value
            val imgType = image?.imageType
            val isRawImage = imgType != null && imgType in setOf(
                ImageType.ARW, ImageType.CR2, ImageType.CR3,
                ImageType.NEF, ImageType.DNG
            )
            val (csInput, csWorking, csOutput) = resolveColorSpaceFlow(currentParams, isRawImage)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                shape = AlcedoRadius.xs,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            ) {
                ColorSpaceIndicator(
                    inputSpace = csInput,
                    workingSpace = csWorking,
                    outputSpace = csOutput,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (isTablet) {
                // 平板：左右分栏布局
                Row(modifier = Modifier.fillMaxSize()) {
                    // 图片预览
                    ImagePreviewArea(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isProcessing = isProcessing,
                        imageLoadError = imageLoadError,
                        onRetryLoad = { viewModel.retryLoadImage() },
                        isCompareMode = isCompareMode,
                        previewBitmap = preview?.asImageBitmap(),
                        originalBitmap = originalBitmap?.asImageBitmap(),
                        zoomableState = zoomableState,
                        viewModel = viewModel,
                        selectedPanel = selectedPanel,
                        compareMode = compareMode,
                        overlayOpacity = overlayOpacity
                    )

                    // 编辑器面板
                    CompositionLocalProvider(LocalEditorEnabled provides !isProcessing) {
                        EditorPanelColumn(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight(),
                            selectedPanel = selectedPanel,
                            onPanelSelected = { viewModel.updateSelectedPanel(it) },
                            viewModel = viewModel,
                            focusMode = focusMode
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
                        imageLoadError = imageLoadError,
                        onRetryLoad = { viewModel.retryLoadImage() },
                        isCompareMode = isCompareMode,
                        previewBitmap = preview?.asImageBitmap(),
                        originalBitmap = originalBitmap?.asImageBitmap(),
                        zoomableState = zoomableState,
                        viewModel = viewModel,
                        selectedPanel = selectedPanel,
                        compareMode = compareMode,
                        overlayOpacity = overlayOpacity
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        edgePadding = 12.dp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    ) {
                        EditorPanel.entries.forEach { panel ->
                            val selected = pagerState.currentPage == panel.ordinal
                            Tab(
                                selected = selected,
                                onClick = { viewModel.updateSelectedPanel(panel) },
                                icon = {
                                    Icon(
                                        imageVector = panelIcon(panel),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        stringRes(panel.labelKey),
                                        style = if (selected) MaterialTheme.typography.labelMedium
                                        else MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                                        else androidx.compose.ui.text.font.FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
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
                        AnimatedContent(
                            targetState = panel,
                            transitionSpec = {
                                (slideInHorizontally { it } + fadeIn(tween(280, easing = AlcedoEasing.EmphasizedDecelerate))) togetherWith
                                (slideOutHorizontally { -it } + fadeOut(tween(200, easing = AlcedoEasing.EmphasizedAccelerate)))
                            }
                        ) { currentPanel ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (currentPanel) {
                                    EditorPanel.BASIC -> BasicPanel(viewModel = viewModel, focusMode = focusMode)
                                    EditorPanel.TONE_CURVE -> ToneCurvePanel(viewModel = viewModel)
                                    EditorPanel.COLOR -> ColorPanel(viewModel = viewModel, focusMode = focusMode)
                                    EditorPanel.HSL -> {
                                        val params by remember { viewModel.params }
                                        HlsProfilePanel(
                                            params = params,
                                            onParamsChanged = { viewModel.updateParamsWithHistory(it, OperatorType.HSL) }
                                        )
                                    }
                                    EditorPanel.GEOMETRY -> GeometryPanel(viewModel = viewModel)
                                    EditorPanel.EFFECTS -> EffectsPanel(viewModel = viewModel, focusMode = focusMode)
                                    EditorPanel.RAW -> RawDecodePanel(viewModel = viewModel)
                                    EditorPanel.HISTORY -> HistoryPanel(viewModel = viewModel)
                                    EditorPanel.DISPLAY_TRANSFORM -> DisplayTransformPanel(
                                        params = viewModel.params.value,
                                        onParamsChanged = { viewModel.updateParamsWithHistory(it, OperatorType.DISPLAY_TRANSFORM) }
                                    )
                                    EditorPanel.LMT -> {
                                        val lmtParams = viewModel.params.value
                                        LmtPanel(
                                            lmtEnabled = lmtParams.lutEnabled,
                                            lmtPath = lmtParams.lutPath,
                                            lmtIntensity = lmtParams.lutIntensity,
                                            onLmtChanged = { enabled, path, intensity ->
                                                viewModel.updateParamsWithHistory(
                                                    lmtParams.copy(
                                                        lutEnabled = enabled,
                                                        lutPath = path,
                                                        lutIntensity = intensity
                                                    ),
                                                    OperatorType.LUT
                                                )
                                            }
                                        )
                                    }
                                    EditorPanel.INSPECTOR -> ImageInspectorPanel(
                                        image = viewModel.imageModel.value
                                    )
                                    EditorPanel.LENS_CORRECTION -> LensCorrectionPanel(viewModel = viewModel)
                                    EditorPanel.MASKS -> MaskPanel(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    EditorPanel.PRESETS -> PresetPanel(
                                        viewModel = viewModel,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
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
                    showClippingWarning = showClippingWarning,
                    onToggleClippingWarning = { viewModel.toggleClippingWarning() },
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
            if (isProcessing) {
                LoadingOverlay(
                    message = stringRes { editorProcessing },
                    modifier = Modifier.fillMaxSize()
                )
            }
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
    showClippingWarning: Boolean,
    onToggleClippingWarning: () -> Unit,
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
        // UX 修复: 使用主题色而非硬编码深色,确保浅色主题下视觉一致
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
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
                        }
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringRes { close },
                        modifier = Modifier.size(AlcedoIconSize.lg),
                        // UX 修复: 使用主题色而非硬编码白色
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        showClippingWarning = showClippingWarning,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 通道选择行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HistogramChannel.entries.forEach { ch ->
                            val color = when (ch) {
                                HistogramChannel.RGB -> MaterialTheme.colorScheme.onSurface
                                HistogramChannel.RED -> Color.Red
                                HistogramChannel.GREEN -> Color.Green
                                HistogramChannel.BLUE -> Color(0xFF42A5F5)
                                HistogramChannel.LUMINANCE -> MaterialTheme.colorScheme.onSurface
                            }
                            FilterChip(
                                selected = histogramChannel == ch,
                                onClick = { onHistogramChannelChange(ch) },
                                label = {
                                    Text(
                                        ch.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (histogramChannel == ch) color
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                        }
                    }
                    // 剪裁预警切换行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showClippingWarning,
                            onClick = onToggleClippingWarning,
                            label = {
                                Text(
                                    "Clipping Warning",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(26.dp)
                        )
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
                            Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
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
                            Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
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
                                GamutOverlay.SRGB -> MaterialTheme.colorScheme.onSurface
                                GamutOverlay.P3 -> Color(0xFF4FC3F7)
                                GamutOverlay.REC2020 -> Color(0xFFFFA726)
                                GamutOverlay.ACES -> Color(0xFFAB47BC)
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
                                        color = if (g in gamutOverlay) color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.height(26.dp)
                            )
                            Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
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
    imageLoadError: Boolean = false,
    onRetryLoad: () -> Unit = {},
    isCompareMode: Boolean,
    previewBitmap: ImageBitmap?,
    originalBitmap: ImageBitmap?,
    zoomableState: ZoomableState,
    viewModel: EditorViewModel,
    selectedPanel: EditorPanel,
    compareMode: CompareMode = CompareMode.SPLIT,
    overlayOpacity: Float = 0.5f
) {
    // 长按对比 — 长按显示原图（修改前），松开恢复
    var showOriginalOverlay by remember { mutableStateOf(false) }

    // 图片显示区域的计算
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 计算 imageDisplayRect（考虑 ContentScale.Fit + zoomableState）
    val bitmapWidth = previewBitmap?.width ?: 0
    val bitmapHeight = previewBitmap?.height ?: 0

    // ContentScale.Fit 下图片的理论尺寸（未缩放时）
    val fitRect = remember(containerSize, bitmapWidth, bitmapHeight) {
        if (containerSize.width <= 0 || containerSize.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            Rect.Zero
        } else {
            val scale = minOf(
                containerSize.width.toFloat() / bitmapWidth,
                containerSize.height.toFloat() / bitmapHeight
            )
            val fitW = bitmapWidth * scale
            val fitH = bitmapHeight * scale
            val left = (containerSize.width - fitW) / 2f
            val top = (containerSize.height - fitH) / 2f
            Rect(left, top, left + fitW, top + fitH)
        }
    }

    // 考虑 zoomableState 的缩放与平移
    val imageDisplayRect = remember(fitRect, zoomableState.scale, zoomableState.offsetX, zoomableState.offsetY) {
        if (fitRect == Rect.Zero) Rect.Zero
        else {
            // 缩放后的尺寸
            val scaledW = fitRect.width * zoomableState.scale
            val scaledH = fitRect.height * zoomableState.scale
            // 平移后的位置（fitRect 居中，TransformOrigin(0.5f,0.5f)）
            val centerX = fitRect.left + fitRect.width / 2f
            val centerY = fitRect.top + fitRect.height / 2f
            val newLeft = centerX - scaledW / 2f + zoomableState.offsetX
            val newTop = centerY - scaledH / 2f + zoomableState.offsetY
            Rect(newLeft, newTop, newLeft + scaledW, newTop + scaledH)
        }
    }

    // Brush 状态
    val brushState by remember { viewModel.brushState }
    val activeBrush = viewModel.activeBrushSubMaskIndex.collectAsState().value
    val maskContainers = viewModel.maskContainers.collectAsState().value

    // 当前激活的 Brush sub-mask 类型
    val isBrushActive = activeBrush != null &&
        maskContainers.firstOrNull { it.id == activeBrush.first }
            ?.subMasks?.getOrNull(activeBrush.second)?.type == MaskType.BRUSH

    Box(
        modifier = modifier
            // UX 修复: 使用主题色而非硬编码深色,照片查看器背景随主题切换
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged { size -> containerSize = size }
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
                    // UX 修复: 使用主题色而非硬编码白色,确保浅色主题下可见
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                "compare" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 对比模式切换器
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompareMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = compareMode == mode,
                                    onClick = { viewModel.updateCompareMode(mode) },
                                    label = {
                                        Text(
                                            mode.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = AlcedoSpacing.xs)
                                )
                            }
                        }
                        CompareView(
                            originalImage = originalBitmap!!,
                            editedImage = previewBitmap!!,
                            compareMode = compareMode,
                            overlayOpacity = overlayOpacity,
                            onOverlayOpacityChange = { viewModel.updateOverlayOpacity(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                else -> {
                        ZoomableImageView(
                            imageBitmap = previewBitmap,
                            modifier = Modifier.fillMaxSize(),
                            zoomableState = zoomableState
                        )

                        // 当当前面板是 Mask 且有激活的 Brush sub-mask 时，显示画笔覆盖层
                        if (selectedPanel == EditorPanel.MASKS && isBrushActive) {
                            BrushOverlay(
                                brushState = brushState,
                                onBrushStateChanged = { newState ->
                                    viewModel.updateBrushState(newState)
                                },
                                imageRect = imageDisplayRect,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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

        // P2-8 锐化蒙版可视化：在图片上方叠加显示边缘蒙版（白色高亮锐化区域）
        val showSharpeningMask by viewModel.showSharpeningMask.collectAsStateWithLifecycle()
        val sharpeningMaskBitmap by viewModel.sharpeningMaskBitmap.collectAsStateWithLifecycle()
        if (showSharpeningMask && sharpeningMaskBitmap != null) {
            Image(
                bitmap = sharpeningMaskBitmap!!.asImageBitmap(),
                contentDescription = stringRes { effectsShowSharpeningMask },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        if (previewBitmap == null && !isProcessing) {
            if (imageLoadError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringRes { editorNoImage },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRetryLoad,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重试")
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringRes { editorNoImage },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
    viewModel: EditorViewModel,
    focusMode: FocusModeState
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedPanel.ordinal,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
            }
        ) {
            EditorPanel.entries.forEach { panel ->
                val selected = selectedPanel == panel
                Tab(
                    selected = selected,
                    onClick = { onPanelSelected(panel) },
                    text = {
                        Text(
                            stringRes(panel.labelKey),
                            style = if (selected) MaterialTheme.typography.labelMedium
                            else MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                EditorPanel.BASIC -> BasicPanel(viewModel = viewModel, focusMode = focusMode)
                EditorPanel.TONE_CURVE -> ToneCurvePanel(viewModel = viewModel)
                EditorPanel.COLOR -> ColorPanel(viewModel = viewModel, focusMode = focusMode)
                EditorPanel.HSL -> {
                    val params by remember { viewModel.params }
                    HlsProfilePanel(
                        params = params,
                        onParamsChanged = { viewModel.updateParamsWithHistory(it, OperatorType.HSL) }
                    )
                }
                EditorPanel.GEOMETRY -> GeometryPanel(viewModel = viewModel)
                EditorPanel.EFFECTS -> EffectsPanel(viewModel = viewModel, focusMode = focusMode)
                EditorPanel.RAW -> RawDecodePanel(viewModel = viewModel)
                EditorPanel.HISTORY -> HistoryPanel(viewModel = viewModel)
                EditorPanel.DISPLAY_TRANSFORM -> DisplayTransformPanel(
                    params = viewModel.params.value,
                    onParamsChanged = { viewModel.updateParamsWithHistory(it, OperatorType.DISPLAY_TRANSFORM) }
                )
                EditorPanel.LMT -> {
                    val lmtParams = viewModel.params.value
                    LmtPanel(
                        lmtEnabled = lmtParams.lutEnabled,
                        lmtPath = lmtParams.lutPath,
                        lmtIntensity = lmtParams.lutIntensity,
                        onLmtChanged = { enabled, path, intensity ->
                            viewModel.updateParamsWithHistory(
                                lmtParams.copy(
                                    lutEnabled = enabled,
                                    lutPath = path,
                                    lutIntensity = intensity
                                ),
                                OperatorType.LUT
                            )
                        }
                    )
                }
                EditorPanel.INSPECTOR -> ImageInspectorPanel(
                    image = viewModel.imageModel.value
                )
                EditorPanel.LENS_CORRECTION -> LensCorrectionPanel(viewModel = viewModel)
                EditorPanel.MASKS -> MaskPanel(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
                )
                EditorPanel.PRESETS -> PresetPanel(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth()
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

    Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)) {
        Text(stringRes { editorDemosaicAlgorithm }, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
            DemosaicAlgorithm.entries.forEach { algo ->
                FilterChip(
                    selected = params.rawDecodeParams.demosaicAlgorithm == algo,
                    onClick = {
                        viewModel.updateParamsWithHistory(
                            params.copy(
                                rawDecodeParams = params.rawDecodeParams.copy(
                                    demosaicAlgorithm = algo
                                )
                            ),
                            OperatorType.RAW_DECODE
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
                    viewModel.updateParamsWithHistory(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                highlightReconstruction = it
                            )
                        ),
                        OperatorType.RAW_DECODE
                    )
                }
            )
            Text(stringRes { editorHighlightReconstruction }, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.autoBrightness,
                onCheckedChange = {
                    viewModel.updateParamsWithHistory(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                autoBrightness = it
                            )
                        ),
                        OperatorType.RAW_DECODE
                    )
                }
            )
            Text(stringRes { editorAutoBrightness }, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.rawDecodeParams.useCameraMatrix,
                onCheckedChange = {
                    viewModel.updateParamsWithHistory(
                        params.copy(
                            rawDecodeParams = params.rawDecodeParams.copy(
                                useCameraMatrix = it
                            )
                        ),
                        OperatorType.RAW_DECODE
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

/**
 * Maps each editor panel to a representative leading icon for the bottom tabs.
 */
private fun panelIcon(panel: EditorPanel): ImageVector = when (panel) {
    EditorPanel.BASIC -> Icons.Default.Tune
    EditorPanel.TONE_CURVE -> Icons.Default.ShowChart
    EditorPanel.COLOR -> Icons.Default.Palette
    EditorPanel.HSL -> Icons.Default.InvertColors
    EditorPanel.GEOMETRY -> Icons.Default.Crop
    EditorPanel.EFFECTS -> Icons.Default.AutoFixHigh
    EditorPanel.RAW -> Icons.Default.Image
    EditorPanel.HISTORY -> Icons.Default.History
    EditorPanel.DISPLAY_TRANSFORM -> Icons.Default.Layers
    EditorPanel.LMT -> Icons.Default.FilterVintage
    EditorPanel.INSPECTOR -> Icons.Default.Info
    EditorPanel.LENS_CORRECTION -> Icons.Default.CameraAlt
    EditorPanel.MASKS -> Icons.Default.BlurOn
    EditorPanel.PRESETS -> Icons.Default.AutoAwesome
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
    // 2026: 底部工具栏 — 玻璃态 + 顶部渐变边框
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = AlcedoGlass.toolbarOpacity),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AlcedoSpacing.sm, vertical = AlcedoSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 智能优化 — 一键自动增强
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
    // 2026: 按钮按下缩放动画
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = AlcedoAnimation.buttonPressScale,
        label = "toolbarScale"
    )

    val iconColor = when {
        !enabled -> tint.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> tint
    }
    val labelColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 选中时显示暖色背景,加强可读性
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(AlcedoRadius.sm))
            .background(backgroundColor)
            .clickable(enabled = enabled) {
                isPressed = true
                onClick()
            }
            .padding(horizontal = AlcedoSpacing.sm, vertical = AlcedoSpacing.xs)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(AlcedoIconSize.lg),
            tint = iconColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1
        )
    }

    // 2026: 释放缩放动画
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(120)
            isPressed = false
        }
    }
}
