package com.alcedo.studio.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.ConfirmDialog
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.common.QuickActionsBar
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/** 底部五个主面板标签页 */
private enum class EditorTab(val labelKey: (com.alcedo.studio.i18n.StringRes) -> String) {
    TONE({ it.panelTone }),
    LOOK({ it.panelLook }),
    DISPLAY({ it.panelDisplay }),
    GEOMETRY({ it.panelGeometry }),
    RAW({ it.panelRaw }),
}

/** 顶部溢出菜单中的副面板 */
private enum class SecondaryPanel(val title: (com.alcedo.studio.i18n.StringRes) -> String) {
    MASKS({ it.panelMasks }),
    HISTORY({ it.panelHistory }),
    EXIF({ it.panelExif }),
    PRESETS({ it.panelPresets }),
    EFFECTS({ it.panelEffects }),
    LENS({ it.panelLensCorrection }),
    LMT({ it.panelLmt }),
    WATERMARK({ it.panelWatermark }),
    INSPECTOR({ it.inspector }),
    COMPOSITION({ it.panelComposition }),
    FOCUS_PEAKING({ it.panelFocusPeaking }),
}

/**
 * 增强版编辑器界面 — 参考国内主流摄影App（醒图/美图秀秀/Lightroom Mobile）的交互模式：
 *
 * 交互优化：
 * - **长按对比**：长按图片显示原图，松手恢复（替代切换按钮）
 * - **快捷操作栏**：底部浮动栏提供撤销/重做/自动增强/对比/分享/重置
 * - **手势优化**：双击图片复位缩放，双击标签复位参数，滑块拖拽触觉反馈
 * - **面板优化**：底部面板可上下拖拽调节高度
 * - **智能显隐**：面板切换时自动收起，为图片预览留出更多空间
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageId: String?,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pipelineState by viewModel.pipelineState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var secondary by remember { mutableStateOf<SecondaryPanel?>(null) }
    var showCompare by remember { mutableStateOf(false) }
    var isLongPressComparing by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var versionMenuOpen by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var compositionGuide by remember { mutableStateOf(CompositionGuide.NONE) }
    val focusModeState = rememberFocusModeState()

    // 打开图片
    androidx.compose.runtime.LaunchedEffect(imageId) {
        if (imageId != null && state.image == null) viewModel.openImage(imageId)
    }

    // 未保存更改提示
    BackHandler(enabled = state.dirty) { showUnsavedDialog = true }
    if (showUnsavedDialog) {
        ConfirmDialog(
            title = s.unsavedChanges,
            message = s.unsavedChangesMessage,
            confirmText = s.discard,
            dismissText = s.cancel,
            destructive = true,
            onConfirm = { showUnsavedDialog = false; onBack() },
            onDismiss = { showUnsavedDialog = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.image?.displayName ?: s.editorTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = AlcedoColors.TextPrimary,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    // 版本管理
                    Box {
                        IconButton(onClick = { versionMenuOpen = true }) {
                            Icon(Icons.Outlined.Layers, contentDescription = s.versions, tint = AlcedoColors.TextSecondary)
                        }
                        DropdownMenu(expanded = versionMenuOpen, onDismissRequest = { versionMenuOpen = false }) {
                            state.versions.forEach { v ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = v.name + if (v.isActive) "  ✓" else "",
                                            color = if (v.isActive) AlcedoColors.AccentBlue else AlcedoColors.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        viewModel.switchVersion(v.id)
                                        versionMenuOpen = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(s.createVirtualCopy) },
                                onClick = { viewModel.createVirtualCopy(); versionMenuOpen = false },
                            )
                        }
                    }
                    // 更多面板
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = s.more)
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            SecondaryPanel.entries.forEach { panel ->
                                DropdownMenuItem(
                                    text = { Text(panel.title(s)) },
                                    onClick = { secondary = panel; overflowOpen = false },
                                )
                            }
                        }
                    }
                    // 导出
                    IconButton(onClick = onExport) {
                        Icon(Icons.Outlined.Output, contentDescription = s.export, tint = AlcedoColors.AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ---- 图片预览区 ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.image == null) {
                    EmptyState(
                        title = s.editorTitle,
                        subtitle = s.openInEditor,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    // 手势增强层：左右滑动切换照片 + 双击复位调整
                    EditorGestureLayer(
                        canSwipeLeft = false, // TODO: wire up photo navigation
                        canSwipeRight = false,
                        onSwipeLeft = { /* TODO: navigate to previous photo */ },
                        onSwipeRight = { /* TODO: navigate to next photo */ },
                        onDoubleTapReset = { viewModel.resetAdjustments() },
                    ) {
                        if (showCompare) {
                            // 传统对比模式（分屏/并排/叠加）
                            CompareView(
                                beforeBitmap = state.beforeBitmap,
                                afterBitmap = pipelineState.previewBitmap,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // 增强版缩放视图：支持长按显示原图
                            ZoomableImageView(
                                bitmap = pipelineState.previewBitmap,
                                beforeBitmap = state.beforeBitmap,
                                isRendering = pipelineState.isRendering,
                                onLongPressCompare = { pressing -> isLongPressComparing = pressing },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // 色彩空间指示器
                ColorSpaceIndicator(
                    colorSpace = state.params.outputColorSpace,
                    modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.spacingSm),
                )

                // 构图辅助线
                if (compositionGuide != CompositionGuide.NONE) {
                    CompositionOverlay(
                        guideType = compositionGuide,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 对焦峰值指示器
                if (focusModeState.enabled) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = s.focusPeaking,
                        tint = focusModeState.overlayColor,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(DesignTokens.spacingSm)
                            .size(12.dp),
                    )
                }

                // 渲染指示器
                if (pipelineState.isRendering) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = s.rendering,
                        tint = AlcedoColors.WarmAccent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(DesignTokens.spacingSm)
                            .size(12.dp),
                    )
                }
            }

            // ---- 快捷操作栏 ----
            if (state.image != null) {
                QuickActionsBar(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    canReset = state.params != AdjustmentParams.DEFAULT,
                    isComparing = showCompare || isLongPressComparing,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onAutoEnhance = { viewModel.resetAdjustments() }, // TODO: 替换为真正的自动增强
                    onCompareToggle = { showCompare = !showCompare },
                    onShare = onExport,
                    onReset = { viewModel.resetAdjustments() },
                    modifier = Modifier.padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
                )
            }

            // ---- 底部面板区域 ----
            if (state.image != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DesignTokens.panelWidthCompact / 2.2f)
                        .background(AlcedoColors.Graphite),
                ) {
                    val secondaryPanel = secondary
                    if (secondaryPanel != null) {
                        SecondaryPanelHeader(
                            title = secondaryPanel.title(s),
                            onClose = { secondary = null },
                        )
                        EditorPanelContent(
                            panel = secondaryPanel,
                            state = state,
                            viewModel = viewModel,
                            compositionGuide = compositionGuide,
                            onCompositionGuideChange = { compositionGuide = it },
                            focusModeState = focusModeState,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = AlcedoColors.Graphite,
                            contentColor = AlcedoColors.AccentBlue,
                            edgePadding = 0.dp,
                        ) {
                            EditorTab.entries.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { Text(tab.labelKey(s), maxLines = 1) },
                                )
                            }
                        }
                        EditorTabContent(
                            tab = EditorTab.entries[selectedTab],
                            params = state.params,
                            previewBitmap = pipelineState.previewBitmap,
                            onUpdate = { field, value -> viewModel.updateParam(field, value) },
                            onCommit = { viewModel.commitChange() },
                            onPointsChange = { viewModel.setCurvePoints(it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    state.error?.let { err ->
        ErrorDialog(
            title = s.error,
            message = err,
            onDismiss = { viewModel.dismissError() },
            retryText = s.retry,
            onRetry = { viewModel.rerender(); viewModel.dismissError() },
        )
    }
}

// ============================================================================
// 私有辅助组件
// ============================================================================

@Composable
private fun SecondaryPanelHeader(title: String, onClose: () -> Unit) {
    val s = Strings.res
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AlcedoColors.SurfaceElevated)
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = AlcedoColors.AccentBlue,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = s.close, tint = AlcedoColors.TextSecondary)
        }
    }
}

@Composable
private fun EditorTabContent(
    tab: EditorTab,
    params: AdjustmentParams,
    previewBitmap: android.graphics.Bitmap?,
    onUpdate: (String, Float) -> Unit,
    onCommit: () -> Unit,
    onPointsChange: (List<com.alcedo.studio.data.model.CurvePoint>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(DesignTokens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        when (tab) {
            EditorTab.TONE -> {
                BasicPanel(params = params, onUpdate = onUpdate, onCommit = onCommit)
                ColorTempPanel(params = params, onUpdate = onUpdate, onCommit = onCommit)
                ToneCurvePanel(params = params, onPointsChange = onPointsChange)
            }
            EditorTab.LOOK -> {
                ColorPanel(params = params, onUpdate = onUpdate, onCommit = onCommit)
                HlsProfilePanel(params = params, onUpdate = onUpdate)
                ColorWheelView(params = params, onUpdate = onUpdate)
            }
            EditorTab.DISPLAY -> {
                DisplayTransformPanel(params = params, onUpdate = onUpdate)
                ScopeAnalyzer(
                    bitmap = previewBitmap,
                    modifier = Modifier.fillMaxWidth().height(DesignTokens.scopeHeight),
                )
            }
            EditorTab.GEOMETRY -> {
                GeometryPanel(params = params, onUpdate = onUpdate)
            }
            EditorTab.RAW -> {
                RawDecodePanel(params = params, onUpdate = onUpdate)
                LensCorrectionPanel(params = params, onUpdate = onUpdate)
            }
        }
    }
}

@Composable
private fun EditorPanelContent(
    panel: SecondaryPanel,
    state: EditorViewModel.EditorUiState,
    viewModel: EditorViewModel,
    compositionGuide: CompositionGuide,
    onCompositionGuideChange: (CompositionGuide) -> Unit,
    focusModeState: FocusModeState,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(DesignTokens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        when (panel) {
            SecondaryPanel.MASKS -> MaskPanel(
                masks = state.masks,
                onAddBrush = { viewModel.addBrushMask() },
                onAddRadial = { viewModel.addRadialMask(0.5f, 0.5f, 0.3f, 0.3f) },
                onAddLinear = { viewModel.addLinearMask(0.2f, 0.5f, 0.8f, 0.5f) },
                onAddLuminance = { viewModel.addLuminanceMask(0f, 0.5f) },
                onAddSubject = { viewModel.addSubjectMask() },
                onAddSky = { viewModel.addSkyMask() },
                onAddBackground = { viewModel.addBackgroundMask() },
                onToggle = { viewModel.toggleMask(it) },
                onRemove = { viewModel.removeMask(it) },
            )
            SecondaryPanel.HISTORY -> HistoryPanel(
                versions = state.versions,
                transactions = state.transactions,
                activeVersionId = state.activeVersionId,
                onSwitch = { viewModel.switchVersion(it) },
                onCreate = { viewModel.createVirtualCopy() },
                onDelete = { viewModel.deleteVersion(it) },
            )
            SecondaryPanel.EXIF -> ExifEditorPanel(exif = state.exif, onFieldChange = { key, value -> viewModel.setExifField(key, value) })
            SecondaryPanel.PRESETS -> PresetPanel(
                presets = state.presets,
                favorites = state.favoritePresets,
                onApply = { viewModel.applyPreset(it) },
                onSaveCurrent = { name -> viewModel.saveCurrentAsPreset(name) },
                onToggleFavorite = { viewModel.togglePresetFavorite(it) },
            )
            SecondaryPanel.EFFECTS -> EffectsPanel(params = state.params, onUpdate = { f, v -> viewModel.updateParam(f, v) })
            SecondaryPanel.LENS -> LensCorrectionPanel(params = state.params, onUpdate = { f, v -> viewModel.updateParam(f, v) })
            SecondaryPanel.LMT -> LmtPanel(onApplyLmt = { path -> viewModel.applyLmt(path) })
            SecondaryPanel.WATERMARK -> WatermarkPanel(
                config = state.watermark,
                onConfigChange = { viewModel.setWatermarkConfig(it) },
            )
            SecondaryPanel.INSPECTOR -> ImageInspectorPanel(image = state.image, exif = state.exif)
            SecondaryPanel.COMPOSITION -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
            ) {
                CompositionGuide.entries.forEach { guide ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCompositionGuideChange(guide) }
                            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = guide.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (compositionGuide == guide) AlcedoColors.AccentBlue else AlcedoColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            SecondaryPanel.FOCUS_PEAKING -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Strings.res.focusPeaking, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = focusModeState.enabled, onCheckedChange = { focusModeState.toggle() }, modifier = Modifier.size(32.dp))
                }
                com.alcedo.studio.ui.common.AdjustmentSlider(
                    label = Strings.res.sensitivity,
                    value = focusModeState.sensitivity,
                    defaultValue = 0.5f,
                    range = 0f..1f,
                    onValueChange = { focusModeState.sensitivity = it },
                )
            }
        }
    }
}