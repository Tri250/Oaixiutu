package com.alcedo.studio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.*
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun GeometryPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
    var selectedCropAspectRatio by remember { mutableStateOf(CropAspectRatio.FREE) }
    var selectedOverlay by remember { mutableStateOf(CompositionOverlayType.NONE) }
    val view = LocalView.current
    val alcedoColors = LocalAlcedoColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        // ── Transform ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionTransform },
                        style = AlcedoFontRoles.uiTitle,
                        color = alcedoColors.text
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateGeometryRotate(0f)
                            viewModel.updateGeometryScale(1f)
                            viewModel.updateGeometryFlipH(false)
                            viewModel.updateGeometryFlipV(false)
                        },
                        modifier = Modifier.size(AlcedoIconSize.xl)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetTransform },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = alcedoColors.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                AdjustmentSlider(
                    label = stringRes { editorRotate },
                    value = params.geometryRotate,
                    range = -45f..45f,
                    onValueChange = {
                        viewModel.updateGeometryRotate(it)
                    },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.1f°".format(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateGeometryFlipH(!params.geometryFlipH)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (params.geometryFlipH)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(AlcedoIconSize.sm)
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                        Text(stringRes { editorFlipH })
                    }
                    OutlinedButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateGeometryFlipV(!params.geometryFlipV)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (params.geometryFlipV)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(AlcedoIconSize.sm)
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                        Text(stringRes { editorFlipV })
                    }
                }

                OutlinedButton(
                    onClick = {
                        HapticFeedback.heavyClick(view)
                        viewModel.updateGeometryRotate(0f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(AlcedoIconSize.sm)
                    )
                    Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                    Text(stringRes { geometryResetRotation })
                }
            }
        }

        // ── Crop ───────────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionCrop },
                        style = AlcedoFontRoles.uiTitle,
                        color = alcedoColors.text
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetCrop()
                            selectedCropAspectRatio = CropAspectRatio.FREE
                        },
                        modifier = Modifier.size(AlcedoIconSize.xl)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetCrop },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = alcedoColors.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                // 裁剪比例选择 — 使用 CropAspectRatio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    CropAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = selectedCropAspectRatio == ratio,
                            onClick = {
                                HapticFeedback.click(view)
                                selectedCropAspectRatio = ratio
                                viewModel.updateCropAspectRatio(ratio)
                            },
                            label = { Text(ratio.label, style = AlcedoFontRoles.uiCaption) }
                        )
                    }
                }
            }
        }

        // ── Rotate / Flip ────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { editorSectionTransform },
                    style = AlcedoFontRoles.uiTitle,
                color = alcedoColors.text
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                ) {
                    IconButton(onClick = {
                        val newRotation = ((params.cropRotation + 90) % 360)
                        viewModel.updateCropRotation(newRotation)
                    }) {
                        Icon(Icons.Default.RotateRight, contentDescription = stringRes { rotate90 })
                    }
                    IconButton(onClick = {
                        viewModel.updateCropFlip(!params.cropFlipHorizontal, params.cropFlipVertical)
                    }) {
                        Icon(Icons.Default.Flip, contentDescription = stringRes { flipHorizontal })
                    }
                    IconButton(onClick = {
                        viewModel.updateCropFlip(params.cropFlipHorizontal, !params.cropFlipVertical)
                    }) {
                        Icon(
                            Icons.Default.Flip,
                            contentDescription = stringRes { flipVertical },
                            modifier = Modifier.graphicsLayer { scaleY = -1f }
                        )
                    }
                    IconButton(onClick = {
                        viewModel.resetCrop()
                        selectedCropAspectRatio = CropAspectRatio.FREE
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringRes { resetButton })
                    }
                }
            }
        }

        // ── Composition Guide ─────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { cropCompositionGuide },
                    style = AlcedoFontRoles.uiTitle,
                color = alcedoColors.text
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                CompositionOverlaySelector(
                    selected = selectedOverlay,
                    onSelect = {
                        HapticFeedback.click(view)
                        selectedOverlay = it
                    }
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    CompositionOverlay(
                        overlayType = selectedOverlay,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ── Perspective Correction (4-point) ────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionPerspective },
                        style = AlcedoFontRoles.uiTitle,
                        color = alcedoColors.text
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetPerspectiveCorrection()
                        },
                        modifier = Modifier.size(AlcedoIconSize.xl)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetPerspective },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = alcedoColors.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                // Mode preset buttons
                Text(
                    stringRes { perspectiveMode },
                    style = AlcedoFontRoles.uiCaptionStrong,
                    color = alcedoColors.text
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    PerspectiveMode.entries.forEach { mode ->
                        FilterChip(
                            selected = params.perspectiveCorrectionMode == mode.ordinal,
                            onClick = {
                                HapticFeedback.click(view)
                                viewModel.updatePerspectiveCorrectionMode(mode.ordinal)
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        PerspectiveMode.MANUAL -> stringRes { perspectiveModeManual }
                                        PerspectiveMode.VERTICAL -> stringRes { perspectiveModeVertical }
                                        PerspectiveMode.HORIZONTAL -> stringRes { perspectiveModeHorizontal }
                                        PerspectiveMode.VH -> stringRes { perspectiveModeVH }
                                        PerspectiveMode.FULL -> stringRes { perspectiveModeFull }
                                    },
                                    style = AlcedoFontRoles.uiCaption
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                // Corner point drag overlay
                PerspectiveCornerOverlay(
                    corners = params.perspectiveCorners,
                    onCornerChange = { index, x, y ->
                        viewModel.updatePerspectiveCorner(index, x, y)
                    },
                    showGrid = params.perspectiveShowGrid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                // Grid toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { perspectiveShowGrid },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.perspectiveShowGrid,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updatePerspectiveShowGrid(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                // Auto-detect toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { perspectiveAutoDetect },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.perspectiveAutoDetect,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updatePerspectiveAutoDetect(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                // Correction amount slider
                AdjustmentSlider(
                    label = stringRes { perspectiveAmount },
                    value = params.perspectiveCorrectionAmount,
                    range = 0f..100f,
                    onValueChange = {
                        viewModel.updatePerspectiveCorrectionAmount(it)
                    },
                    defaultValue = 100f,
                    valueDisplayTransform = { "%.0f%%".format(it) }
                )
            }
        }

        // ── Perspective Transform ─────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { cropPerspectiveTransform },
                        style = AlcedoFontRoles.uiTitle,
                        color = alcedoColors.text
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetPerspective()
                        },
                        modifier = Modifier.size(AlcedoIconSize.xl)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetPerspective },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = alcedoColors.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                PerspectiveTransformSlider(stringRes { cropDistortion }, params.perspectiveDistortion) {
                    viewModel.updatePerspectiveDistortion(it)
                }
                PerspectiveTransformSlider(stringRes { cropVerticalPerspective }, params.perspectiveVertical) {
                    viewModel.updatePerspectiveVertical(it)
                }
                PerspectiveTransformSlider(stringRes { cropHorizontalPerspective }, params.perspectiveHorizontal) {
                    viewModel.updatePerspectiveHorizontal(it)
                }
                PerspectiveTransformSlider(stringRes { cropRotationFine }, params.perspectiveRotation) {
                    viewModel.updatePerspectiveRotation(it)
                }
                PerspectiveTransformSlider(stringRes { cropAspect }, params.perspectiveAspect) {
                    viewModel.updatePerspectiveAspect(it)
                }
                PerspectiveTransformSlider(stringRes { cropScale }, params.perspectiveScale) {
                    viewModel.updatePerspectiveScale(it)
                }
                PerspectiveTransformSlider(stringRes { cropXOffset }, params.perspectiveXOffset) {
                    viewModel.updatePerspectiveXOffset(it)
                }
                PerspectiveTransformSlider(stringRes { cropYOffset }, params.perspectiveYOffset) {
                    viewModel.updatePerspectiveYOffset(it)
                }
            }
        }

        // ── Lens Correction ────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { cropLensCorrection },
                        style = AlcedoFontRoles.uiTitle,
                        color = alcedoColors.text
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetLensCorrection()
                        },
                        modifier = Modifier.size(AlcedoIconSize.xl)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetLens },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = alcedoColors.icon
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                // Auto-detect toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { cropLensAutoDetect },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.lensAutoDetect,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updateLensAutoDetect(it)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                // Manual lens fields
                OutlinedTextField(
                    value = params.lensMaker,
                    onValueChange = { viewModel.updateLensMaker(it) },
                    label = { Text(stringRes { cropLensMaker }, style = AlcedoFontRoles.uiCaption) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !params.lensAutoDetect
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                OutlinedTextField(
                    value = params.lensModel,
                    onValueChange = { viewModel.updateLensModel(it) },
                    label = { Text(stringRes { cropLensModel }, style = AlcedoFontRoles.uiCaption) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !params.lensAutoDetect
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                // Correction toggles + amounts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { cropCorrectDistortion },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.lensCorrectDistortion,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updateLensCorrectDistortion(it)
                        }
                    )
                }
                if (params.lensCorrectDistortion) {
                    AdjustmentSlider(
                        label = stringRes { cropAmount },
                        value = params.lensDistortionAmount,
                        range = 0f..100f,
                        onValueChange = {
                            viewModel.updateLensDistortionAmount(it)
                        },
                        defaultValue = 0f,
                        valueDisplayTransform = { "%.0f%%".format(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { cropCorrectVignette },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.lensCorrectVignette,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updateLensCorrectVignette(it)
                        }
                    )
                }
                if (params.lensCorrectVignette) {
                    AdjustmentSlider(
                        label = stringRes { cropAmount },
                        value = params.lensVignetteAmount,
                        range = 0f..100f,
                        onValueChange = {
                            viewModel.updateLensVignetteAmount(it)
                        },
                        defaultValue = 0f,
                        valueDisplayTransform = { "%.0f%%".format(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringRes { cropCorrectTca },
                        style = AlcedoFontRoles.uiCaptionStrong,
                        color = alcedoColors.text
                    )
                    Switch(
                        checked = params.lensCorrectTca,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            viewModel.updateLensCorrectTca(it)
                        }
                    )
                }
                if (params.lensCorrectTca) {
                    AdjustmentSlider(
                        label = stringRes { cropAmount },
                        value = params.lensTcaAmount,
                        range = 0f..100f,
                        onValueChange = {
                            viewModel.updateLensTcaAmount(it)
                        },
                        defaultValue = 0f,
                        valueDisplayTransform = { "%.0f%%".format(it) }
                    )
                }

                // Legacy manual K1/K2 sliders (always visible for advanced use)
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { geometryDistortionK1 },
                    value = params.lensK1,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        viewModel.updateLensCorrection(
                            it, params.lensK2, params.lensK3, params.lensP1, params.lensP2
                        )
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { geometryK2 },
                    value = params.lensK2,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        viewModel.updateLensCorrection(
                            params.lensK1, it, params.lensK3, params.lensP1, params.lensP2
                        )
                    },
                    defaultValue = 0f
                )
            }
        }
    }
}

/** Convenience wrapper: -100..+100 slider for perspective transform parameters. */
@Composable
private fun PerspectiveTransformSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    AdjustmentSlider(
        label = label,
        value = value,
        range = -100f..100f,
        onValueChange = onValueChange,
        defaultValue = 0f
    )
}

/**
 * Compute crop bounds that respect the given aspect ratio from the current
 * image bounds and write them to params.
 */
private fun applyAspectRatioCrop(
    viewModel: EditorViewModel,
    params: com.alcedo.studio.data.model.PipelineParams,
    ratio: AspectRatio
) {
    if (ratio == AspectRatio.FREE) {
        // FREE — just reset to full image
        viewModel.resetCrop()
        return
    }

    val targetRatio = ratio.ratio ?: return
    if (targetRatio < 0.001f) return

    // Current crop bounds in normalised coordinates
    val currentLeft = params.geometryCropLeft
    val currentTop = params.geometryCropTop
    val currentRight = params.geometryCropRight
    val currentBottom = params.geometryCropBottom

    val cropW = currentRight - currentLeft
    val cropH = currentBottom - currentTop

    if (cropW < 0.001f || cropH < 0.001f) return

    // We treat the normalised coordinate space as having an aspect ratio of 1:1
    // (square) since we don't have the real pixel dimensions here. This gives a
    // reasonable approximation — the crop overlay in the editor works in the same
    // normalised space.
    val currentRatio = cropW / cropH

    if (currentRatio > targetRatio) {
        // Too wide — shrink width, center horizontally
        val newW = cropH * targetRatio
        val centerX = (currentLeft + currentRight) / 2f
        val newLeft = (centerX - newW / 2f).coerceIn(0f, 1f)
        val newRight = (centerX + newW / 2f).coerceIn(0f, 1f)
        viewModel.updateGeometryCrop(newLeft, currentTop, newRight, currentBottom)
    } else {
        // Too tall — shrink height, center vertically
        val newH = cropW / targetRatio
        val centerY = (currentTop + currentBottom) / 2f
        val newTop = (centerY - newH / 2f).coerceIn(0f, 1f)
        val newBottom = (centerY + newH / 2f).coerceIn(0f, 1f)
        viewModel.updateGeometryCrop(currentLeft, newTop, currentRight, newBottom)
    }
}

// ============================================================
// Perspective Correction Mode
// ============================================================

/** Perspective correction mode — must match C++ PerspectiveMode enum. */
enum class PerspectiveMode {
    MANUAL,       // Free 4-corner drag
    VERTICAL,     // Vertical lines correction only
    HORIZONTAL,   // Horizontal lines correction only
    VH,           // Vertical + Horizontal
    FULL          // Full free-form 4-point
}

// ============================================================
// Perspective Corner Drag Overlay
// ============================================================

/**
 * Overlay showing 4 draggable corner points and an optional alignment grid.
 * [corners] is a FloatArray of 8 values: [TLx, TLy, TRx, TRy, BRx, BRy, BLx, BLy]
 * All values are in normalized [0,1] coordinates.
 */
@Composable
private fun PerspectiveCornerOverlay(
    corners: FloatArray,
    onCornerChange: (index: Int, x: Float, y: Float) -> Unit,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    // Local mutable copy for drag
    val localCorners = remember { corners.copyOf() }
    // Sync from external state
    LaunchedEffect(corners) {
        corners.copyInto(localCorners)
    }

    val handleRadius = 12f
    val lineColor = Color(0xFF4FC3F7) // Light blue
    val handleColor = Color(0xFF29B6F6)
    val handleBorderColor = Color.White
    val gridColor = Color(0x804FC3F7) // Semi-transparent

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw alignment grid
            if (showGrid) {
                val gridDivisions = 8
                for (i in 1 until gridDivisions) {
                    val x = w * i / gridDivisions
                    val y = h * i / gridDivisions
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
            }

            // Convert normalized corners to pixel positions
            val px = FloatArray(4) { localCorners[it * 2] * w }
            val py = FloatArray(4) { localCorners[it * 2 + 1] * h }

            // Draw quadrilateral edges
            for (i in 0 until 4) {
                val next = (i + 1) % 4
                drawLine(lineColor, Offset(px[i], py[i]), Offset(px[next], py[next]), strokeWidth = 2f)
            }

            // Draw diagonal guides
            drawLine(gridColor, Offset(px[0], py[0]), Offset(px[2], py[2]), strokeWidth = 1f)
            drawLine(gridColor, Offset(px[1], py[1]), Offset(px[3], py[3]), strokeWidth = 1f)

            // Draw corner handles
            for (i in 0 until 4) {
                drawCircle(handleColor, handleRadius, Offset(px[i], py[i]))
                drawCircle(handleBorderColor, handleRadius, Offset(px[i], py[i]), style = Stroke(width = 2f))
            }
        }

        // Invisible drag areas for each corner
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(i) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val w = size.width
                            val h = size.height
                            if (w > 0f && h > 0f) {
                                val newX = (localCorners[i * 2] + dragAmount.x / w).coerceIn(0f, 1f)
                                val newY = (localCorners[i * 2 + 1] + dragAmount.y / h).coerceIn(0f, 1f)
                                localCorners[i * 2] = newX
                                localCorners[i * 2 + 1] = newY
                                onCornerChange(i, newX, newY)
                            }
                        }
                    }
            )
        }
    }
}
