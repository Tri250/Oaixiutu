package com.alcedo.studio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateGeometryRotate(0f)
                            viewModel.updateGeometryScale(1f)
                            viewModel.updateGeometryFlipH(false)
                            viewModel.updateGeometryFlipV(false)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetTransform },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetCrop()
                            selectedCropAspectRatio = CropAspectRatio.FREE
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetCrop },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) }
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
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
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
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetPerspective()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetPerspective },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.resetLensCorrection()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetLens },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                    label = { Text(stringRes { cropLensMaker }, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !params.lensAutoDetect
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                OutlinedTextField(
                    value = params.lensModel,
                    onValueChange = { viewModel.updateLensModel(it) },
                    label = { Text(stringRes { cropLensModel }, style = MaterialTheme.typography.labelSmall) },
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
