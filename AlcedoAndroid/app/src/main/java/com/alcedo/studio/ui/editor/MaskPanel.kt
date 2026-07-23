package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.MaskCombineMode
import com.alcedo.studio.data.model.MaskContainer
import com.alcedo.studio.data.model.MaskType
import com.alcedo.studio.data.model.SubMask
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel
import kotlin.math.roundToInt

/**
 * Mask management panel for AI-driven local adjustments.
 *
 * Lists all [MaskContainer]s with per-container visibility / invert / opacity
 * and a name field, each container's [SubMask]s with combine-mode / invert /
 * opacity controls (plus brush size & hardness when a brush sub-mask is
 * active), a red overlay preview of the combined mask, and drag-to-reorder for
 * sub-masks.
 */
@Composable
fun MaskPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val containers by viewModel.maskContainers.collectAsStateWithLifecycle()
    val maskPreview by viewModel.maskPreviewBitmap.collectAsStateWithLifecycle()
    val original by viewModel.originalBitmap.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingMask.collectAsStateWithLifecycle()

    var showNewMaskMenu by remember { mutableStateOf(false) }
    var expandedContainerId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Header: title + New Mask ──
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringRes { maskTitle },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { showNewMaskMenu = true },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(stringRes { maskNewMask }) }
                    )
                    DropdownMenu(
                        expanded = showNewMaskMenu,
                        onDismissRequest = { showNewMaskMenu = false }
                    ) {
                        MaskType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(maskTypeLabel(type)) },
                                leadingIcon = {
                                    Icon(maskTypeIcon(type), contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                onClick = {
                                    viewModel.addMaskContainer(type)
                                    // Use containers from the already-collected state
                                    expandedContainerId = containers.lastOrNull()?.id
                                    showNewMaskMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Preview overlay ──
        MaskPreviewCard(
            original = original,
            overlay = maskPreview,
            isAnalyzing = isAnalyzing,
            modifier = Modifier.fillMaxWidth()
        )

        // ── Container list ──
        if (containers.isEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringRes { maskNewMask },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            containers.forEach { container ->
                MaskContainerCard(
                    container = container,
                    expanded = expandedContainerId == container.id,
                    viewModel = viewModel,
                    onToggleExpand = {
                        expandedContainerId = if (expandedContainerId == container.id) null else container.id
                    },
                    onUpdate = viewModel::updateMaskContainer,
                    onDelete = { viewModel.removeMaskContainer(container.id) },
                    onAddSubMask = { type, mode -> viewModel.addSubMask(container.id, type, mode) },
                    onRemoveSubMask = { subId -> viewModel.removeSubMask(container.id, subId) },
                    onMoveSubMask = { from, to -> viewModel.moveSubMask(container.id, from, to) }
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Preview card: original bitmap with the red mask overlay composited on top
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun MaskPreviewCard(
    original: android.graphics.Bitmap?,
    overlay: android.graphics.Bitmap?,
    isAnalyzing: Boolean,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (original != null) {
                Image(
                    bitmap = original.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
            if (overlay != null) {
                Image(
                    bitmap = overlay.asImageBitmap(),
                    contentDescription = stringRes { maskTitle },
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
            if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        stringRes { maskAnalyzing },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Container card
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun MaskContainerCard(
    container: MaskContainer,
    expanded: Boolean,
    viewModel: EditorViewModel,
    onToggleExpand: () -> Unit,
    onUpdate: (MaskContainer) -> Unit,
    onDelete: () -> Unit,
    onAddSubMask: (MaskType, MaskCombineMode) -> Unit,
    onRemoveSubMask: (String) -> Unit,
    onMoveSubMask: (Int, Int) -> Unit
) {
    var showSubMaskMenu by remember { mutableStateOf(false) }
    var newSubMode by remember { mutableStateOf(MaskCombineMode.ADDITIVE) }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Name field
                OutlinedTextField(
                    value = container.name,
                    onValueChange = { onUpdate(container.copy(name = it)) },
                    label = { Text(stringRes { maskName }) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                // Visibility toggle
                IconButton(onClick = { onUpdate(container.copy(visible = !container.visible)) }) {
                    Icon(
                        if (container.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringRes { maskVisible },
                        tint = if (container.visible) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Delete
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringRes { maskDeleteMask },
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            // Invert + opacity
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AssistChip(
                    onClick = { onUpdate(container.copy(inverted = !container.inverted)) },
                    label = { Text(stringRes { maskInvert }) },
                    leadingIcon = {
                        Icon(Icons.Default.InvertColors, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = if (container.inverted) AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) else AssistChipDefaults.assistChipColors()
                )
                Spacer(Modifier.width(8.dp))
                AdjustmentSlider(
                    label = stringRes { maskOpacity },
                    value = container.opacity,
                    range = 0f..1f,
                    onValueChange = { onUpdate(container.copy(opacity = it)) },
                    defaultValue = 1f,
                    valueDisplayTransform = { "${(it * 100).roundToInt()}%" },
                    modifier = Modifier.weight(1f)
                )
            }

            if (expanded) {
                // Sub-mask list with drag-to-reorder
                SubMaskList(
                    subMasks = container.subMasks,
                    containerId = container.id,
                    viewModel = viewModel,
                    onMove = onMoveSubMask,
                    onUpdate = { updated ->
                        onUpdate(container.copy(
                            subMasks = container.subMasks.map { if (it.id == updated.id) updated else it }
                        ))
                    },
                    onRemove = onRemoveSubMask
                )

                // Add sub-mask dropdown
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showSubMaskMenu = true },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            label = { Text(stringRes { maskNewMask }) }
                        )
                        DropdownMenu(expanded = showSubMaskMenu, onDismissRequest = { showSubMaskMenu = false }) {
                            // Combine-mode picker first
                            MaskCombineMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        val cm = combineModeLabel(mode)
                                        val applyTo = stringRes { maskApplyTo }
                                        Text("$cm — $applyTo")
                                    },
                                    onClick = { newSubMode = mode }
                                )
                            }
                            // Then mask types
                            MaskType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(maskTypeLabel(type)) },
                                    leadingIcon = {
                                        Icon(maskTypeIcon(type), contentDescription = null, modifier = Modifier.size(20.dp))
                                    },
                                    onClick = {
                                        onAddSubMask(type, newSubMode)
                                        showSubMaskMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        combineModeLabel(newSubMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Sub-mask list with drag-to-reorder (long-press drag swaps neighbours)
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun SubMaskList(
    subMasks: List<SubMask>,
    containerId: String,
    viewModel: EditorViewModel,
    onMove: (Int, Int) -> Unit,
    onUpdate: (SubMask) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        subMasks.forEachIndexed { index, sub ->
            var dragOffset by remember(sub.id) { mutableStateOf(0f) }
            SubMaskRow(
                sub = sub,
                containerId = containerId,
                subMaskIndex = index,
                viewModel = viewModel,
                isFirst = index == 0,
                isLast = index == subMasks.size - 1,
                onUpdate = onUpdate,
                onRemove = { onRemove(sub.id) },
                onMoveUp = { if (index > 0) onMove(index, index - 1) },
                onMoveDown = { if (index < subMasks.size - 1) onMove(index, index + 1) },
                onDrag = { delta ->
                    // Accumulate vertical drag; once it crosses a row-height
                    // threshold, perform a swap and reset the accumulator so a
                    // single long-press drag can reorder multiple positions.
                    dragOffset += delta
                    val threshold = 56f
                    if (dragOffset <= -threshold && index > 0) {
                        onMove(index, index - 1)
                        dragOffset += threshold
                    } else if (dragOffset >= threshold && index < subMasks.size - 1) {
                        onMove(index, index + 1)
                        dragOffset -= threshold
                    }
                }
            )
        }
    }
}

@Composable
private fun SubMaskRow(
    sub: SubMask,
    containerId: String,
    subMaskIndex: Int,
    viewModel: EditorViewModel,
    isFirst: Boolean,
    isLast: Boolean,
    onUpdate: (SubMask) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDrag: (Float) -> Unit
) {
    val brushState by remember { viewModel.brushState }
    val activeBrush = viewModel.activeBrushSubMaskIndex.collectAsState().value
    val isActiveBrush = activeBrush?.first == containerId && activeBrush?.second == subMaskIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle (long-press to drag-reorder) + up/down buttons
        Box(
            modifier = Modifier.pointerInput(sub.id) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Icon(
            maskTypeIcon(sub.type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val typeLabel = maskTypeLabel(sub.type)
            val modeLabel = combineModeLabel(sub.combineMode)
            Text(
                "$typeLabel · $modeLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Combine-mode selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaskCombineMode.entries.forEach { mode ->
                    FilterChip(
                        selected = sub.combineMode == mode,
                        onClick = { onUpdate(sub.copy(combineMode = mode)) },
                        label = { Text(combineModeLabel(mode), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
            // Opacity + invert
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { onUpdate(sub.copy(inverted = !sub.inverted)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.InvertColors,
                        contentDescription = stringRes { maskInvert },
                        tint = if (sub.inverted) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                AdjustmentSlider(
                    label = stringRes { maskOpacity },
                    value = sub.opacity,
                    range = 0f..1f,
                    onValueChange = { onUpdate(sub.copy(opacity = it)) },
                    defaultValue = 1f,
                    valueDisplayTransform = { "${(it * 100).roundToInt()}%" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Brush controls — 画笔交互参数（仅当该行被激活为画笔目标时可调）
            if (sub.type == MaskType.BRUSH) {
                // 激活/退出画笔编辑按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActiveBrush) {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.clearActiveBrushSubMask() },
                            label = { Text(stringRes { maskBrushDone }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    } else {
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.setActiveBrushSubMask(containerId, subMaskIndex) },
                            label = { Text(stringRes { maskBrushEdit }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${sub.params.brushStrokes.size} strokes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 仅当该 sub-mask 被激活为画笔目标时显示完整画笔参数
                if (isActiveBrush) {
                    AdjustmentSlider(
                        label = stringRes { maskBrushSize },
                        value = brushState.brushSize,
                        range = 0.01f..0.2f,
                        onValueChange = { viewModel.setBrushSize(it) },
                        defaultValue = 0.05f,
                        valueDisplayTransform = { "${(it * 100).roundToInt()}" }
                    )
                    AdjustmentSlider(
                        label = stringRes { maskBrushHardness },
                        value = brushState.brushHardness,
                        range = 0f..1f,
                        onValueChange = { viewModel.setBrushHardness(it) },
                        defaultValue = 0.5f,
                        valueDisplayTransform = { "${(it * 100).roundToInt()}%" }
                    )
                    AdjustmentSlider(
                        label = stringRes { maskBrushOpacity },
                        value = brushState.brushOpacity,
                        range = 0f..1f,
                        onValueChange = { viewModel.setBrushOpacity(it) },
                        defaultValue = 1f,
                        valueDisplayTransform = { "${(it * 100).roundToInt()}%" }
                    )

                    // 模式切换行：绘制 / 导航 / 橡皮擦 / 撤销 / 清除
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = brushState.isDrawingMode && !brushState.isEraser,
                            onClick = {
                                viewModel.setBrushEraser(false)
                                viewModel.setBrushDrawingMode(true)
                            },
                            label = { Text(stringRes { maskBrushDraw }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = brushState.isEraser,
                            onClick = {
                                viewModel.setBrushEraser(true)
                                viewModel.setBrushDrawingMode(true)
                            },
                            label = { Text(stringRes { maskBrushEraser }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        Spacer(Modifier.width(6.dp))
                        FilterChip(
                            selected = !brushState.isDrawingMode,
                            onClick = { viewModel.setBrushDrawingMode(false) },
                            label = { Text(stringRes { maskBrushNavigate }, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { viewModel.undoLastBrushStroke() },
                            enabled = brushState.strokes.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = stringRes { maskBrushUndo },
                                tint = if (brushState.strokes.isNotEmpty())
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearAllBrushStrokes() },
                            enabled = brushState.strokes.isNotEmpty(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringRes { maskBrushClear },
                                tint = if (brushState.strokes.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // 未激活时仅显示静态画笔大小/硬度（与该 sub-mask 当前参数一致）
                    AdjustmentSlider(
                        label = stringRes { maskBrushSize },
                        value = sub.params.brushSize,
                        range = 0.01f..0.2f,
                        onValueChange = { onUpdate(sub.copy(params = sub.params.copy(brushSize = it))) },
                        defaultValue = 0.05f,
                        valueDisplayTransform = { "${(it * 100).roundToInt()}" }
                    )
                    AdjustmentSlider(
                        label = stringRes { maskBrushHardness },
                        value = sub.params.brushHardness,
                        range = 0f..1f,
                        onValueChange = { onUpdate(sub.copy(params = sub.params.copy(brushHardness = it))) },
                        defaultValue = 0.5f,
                        valueDisplayTransform = { "${(it * 100).roundToInt()}%" }
                    )
                }
            }

            // Feather for geometric masks
            if (sub.type == MaskType.LINEAR || sub.type == MaskType.RADIAL ||
                sub.type == MaskType.COLOR_RANGE || sub.type == MaskType.LUMINANCE_RANGE
            ) {
                AdjustmentSlider(
                    label = stringRes { maskFeather },
                    value = sub.params.feather,
                    range = 0f..1f,
                    onValueChange = { onUpdate(sub.copy(params = sub.params.copy(feather = it))) },
                    defaultValue = 0.3f,
                    valueDisplayTransform = { "${(it * 100).roundToInt()}%" }
                )
            }
        }

        // Reorder buttons + delete
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringRes { maskDeleteSubMask },
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Labels & icons
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun maskTypeLabel(type: MaskType): String = when (type) {
    MaskType.SUBJECT -> stringRes { maskSubject }
    MaskType.SKY -> stringRes { maskSky }
    MaskType.FOREGROUND -> stringRes { maskForeground }
    MaskType.LINEAR -> stringRes { maskLinear }
    MaskType.RADIAL -> stringRes { maskRadial }
    MaskType.BRUSH -> stringRes { maskBrush }
    MaskType.COLOR_RANGE -> stringRes { maskColorRange }
    MaskType.LUMINANCE_RANGE -> stringRes { maskLuminanceRange }
    MaskType.WHOLE_IMAGE -> stringRes { maskWholeImage }
}

@Composable
private fun combineModeLabel(mode: MaskCombineMode): String = when (mode) {
    MaskCombineMode.ADDITIVE -> stringRes { maskAdditive }
    MaskCombineMode.SUBTRACTIVE -> stringRes { maskSubtractive }
    MaskCombineMode.INTERSECT -> stringRes { maskIntersect }
}

private fun maskTypeIcon(type: MaskType): ImageVector = when (type) {
    MaskType.SUBJECT -> Icons.Default.Person
    MaskType.SKY -> Icons.Default.FilterDrama
    MaskType.FOREGROUND -> Icons.Default.Layers
    MaskType.LINEAR -> Icons.Default.Gradient
    MaskType.RADIAL -> Icons.Default.BlurOn
    MaskType.BRUSH -> Icons.Default.Brush
    MaskType.COLOR_RANGE -> Icons.Default.InvertColors
    MaskType.LUMINANCE_RANGE -> Icons.Default.Tune
    MaskType.WHOLE_IMAGE -> Icons.Default.AutoFixHigh
}
