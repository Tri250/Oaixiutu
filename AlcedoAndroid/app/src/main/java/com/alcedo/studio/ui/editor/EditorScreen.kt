package com.alcedo.studio.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/** The five primary bottom-panel tabs. */
private enum class EditorTab(val labelKey: (com.alcedo.studio.i18n.StringRes) -> String) {
    TONE({ it.panelTone }),
    LOOK({ it.panelLook }),
    DISPLAY({ it.panelDisplay }),
    GEOMETRY({ it.panelGeometry }),
    RAW({ it.panelRaw }),
}

/** Secondary panels reachable from the top-bar overflow menu. */
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
}

/**
 * Full editor screen. Renders the live pipeline preview in a [ZoomableImageView]
 * with a top bar (undo/redo, version menu, compare, export, overflow) and a
 * bottom 5-tab panel switcher (Tone/Look/Display/Geometry/Raw). Secondary
 * panels (masks, history, EXIF, presets, effects, lens, LMT, watermark,
 * inspector) are reachable from the overflow menu and render in the panel area.
 *
 * State comes from [EditorViewModel]; [imageId] (from the nav route) opens the
 * image. A null imageId shows an empty picker state.
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
    var overflowOpen by remember { mutableStateOf(false) }
    var versionMenuOpen by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Open the requested image once.
    androidx.compose.runtime.LaunchedEffect(imageId) {
        if (imageId != null && state.image == null) viewModel.openImage(imageId)
    }

    // Warn about unsaved changes when pressing back in the editor.
    BackHandler(enabled = state.dirty) { showUnsavedDialog = true }
    if (showUnsavedDialog) {
        ConfirmDialog(
            title = "Unsaved changes",
            message = "You have unsaved edits. Discard them and leave the editor?",
            confirmText = "Discard",
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
                    IconButton(onClick = { viewModel.undo() }, enabled = state.versions.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undo)
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = s.redo)
                    }
                    // Version menu
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
                    IconButton(onClick = { showCompare = !showCompare }) {
                        Icon(Icons.Outlined.Compare, contentDescription = s.compare, tint = if (showCompare) AlcedoColors.AccentBlue else AlcedoColors.TextSecondary)
                    }
                    // Overflow → secondary panels
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
            // Viewport
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.image == null) {
                    EmptyState(
                        title = s.editorTitle,
                        subtitle = s.openInEditor,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (showCompare) {
                    CompareView(
                        beforeBitmap = state.beforeBitmap,
                        afterBitmap = pipelineState.previewBitmap,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableImageView(
                        bitmap = pipelineState.previewBitmap,
                        isRendering = pipelineState.isRendering,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Color space badge overlay
                ColorSpaceIndicator(
                    colorSpace = state.params.outputColorSpace,
                    modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.spacingSm),
                )
                // Render indicator
                if (pipelineState.isRendering) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = "Rendering",
                        tint = AlcedoColors.Amber,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(DesignTokens.spacingSm)
                            .size(12.dp),
                    )
                }
            }

            // Bottom panel area
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

@Composable
private fun SecondaryPanelHeader(title: String, onClose: () -> Unit) {
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
            Icon(Icons.Outlined.Close, contentDescription = "Close panel", tint = AlcedoColors.TextSecondary)
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
                WaveformScope(
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
        }
    }
}
