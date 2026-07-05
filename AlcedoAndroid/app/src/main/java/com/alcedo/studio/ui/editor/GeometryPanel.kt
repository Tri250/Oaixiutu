package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun GeometryPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.FREE) }
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Transform ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionTransform },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(
                                params.copy(
                                    geometryRotate = 0f,
                                    geometryScale = 1f,
                                    geometryFlipH = false,
                                    geometryFlipV = false
                                )
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetTransform },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AdjustmentSlider(
                    label = stringRes { editorRotate },
                    value = params.geometryRotate,
                    range = -45f..45f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryRotate = it))
                    },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.1f°".format(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(
                                params.copy(geometryFlipH = !params.geometryFlipH)
                            )
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes { editorFlipH })
                    }
                    OutlinedButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(
                                params.copy(geometryFlipV = !params.geometryFlipV)
                            )
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes { editorFlipV })
                    }
                }

                OutlinedButton(
                    onClick = {
                        HapticFeedback.heavyClick(view)
                        viewModel.updateParams(params.copy(geometryRotate = 0f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringRes { geometryResetRotation })
                }
            }
        }

        // ── Crop ───────────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionCrop },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(
                                params.copy(
                                    geometryCropLeft = 0f,
                                    geometryCropTop = 0f,
                                    geometryCropRight = 1f,
                                    geometryCropBottom = 1f
                                )
                            )
                            selectedAspectRatio = AspectRatio.FREE
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetCrop },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    stringRes { editorAspectRatio },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AspectRatio.entries.take(4).forEach { ratio ->
                        FilterChip(
                            selected = selectedAspectRatio == ratio,
                            onClick = {
                                HapticFeedback.click(view)
                                selectedAspectRatio = ratio
                                applyAspectRatioCrop(viewModel, params, ratio)
                            },
                            label = { Text(ratio.label(), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AspectRatio.entries.drop(4).forEach { ratio ->
                        FilterChip(
                            selected = selectedAspectRatio == ratio,
                            onClick = {
                                HapticFeedback.click(view)
                                selectedAspectRatio = ratio
                                applyAspectRatioCrop(viewModel, params, ratio)
                            },
                            label = { Text(ratio.label(), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AdjustmentSlider(
                    label = stringRes { editorCropLeft },
                    value = params.geometryCropLeft,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropLeft = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorCropTop },
                    value = params.geometryCropTop,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropTop = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorCropRight },
                    value = params.geometryCropRight,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropRight = it))
                    },
                    defaultValue = 1f
                )
                AdjustmentSlider(
                    label = stringRes { editorCropBottom },
                    value = params.geometryCropBottom,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropBottom = it))
                    },
                    defaultValue = 1f
                )
            }
        }

        // ── Perspective Correction ─────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionPerspective },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(
                                params.copy(
                                    geometryPerspectiveDst = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
                                )
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetPerspective },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AdjustmentSlider(
                    label = stringRes { editorHorizontal },
                    value = (params.geometryPerspectiveDst[0] + params.geometryPerspectiveDst[2]) / 2f,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        val newPersp = params.geometryPerspectiveDst.clone()
                        newPersp[0] = 0f + it
                        newPersp[2] = 1f + it
                        viewModel.updateParams(params.copy(geometryPerspectiveDst = newPersp))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorVertical },
                    value = (params.geometryPerspectiveDst[1] + params.geometryPerspectiveDst[5]) / 2f,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        val newPersp = params.geometryPerspectiveDst.clone()
                        newPersp[1] = 0f + it
                        newPersp[5] = 1f + it
                        viewModel.updateParams(params.copy(geometryPerspectiveDst = newPersp))
                    },
                    defaultValue = 0f
                )
            }
        }

        // ── Lens Correction ────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionLensCorrection },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateLensCorrection(0f, 0f, 0f, 0f, 0f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { geometryResetLens },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

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
        viewModel.updateParams(
            params.copy(
                geometryCropLeft = 0f,
                geometryCropTop = 0f,
                geometryCropRight = 1f,
                geometryCropBottom = 1f
            )
        )
        return
    }

    val targetRatio = ratio.ratio ?: return

    // Current crop bounds in normalised coordinates
    val currentLeft = params.geometryCropLeft
    val currentTop = params.geometryCropTop
    val currentRight = params.geometryCropRight
    val currentBottom = params.geometryCropBottom

    val cropW = currentRight - currentLeft
    val cropH = currentBottom - currentTop

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
        viewModel.updateParams(
            params.copy(geometryCropLeft = newLeft, geometryCropRight = newRight)
        )
    } else {
        // Too tall — shrink height, center vertically
        val newH = cropW / targetRatio
        val centerY = (currentTop + currentBottom) / 2f
        val newTop = (centerY - newH / 2f).coerceIn(0f, 1f)
        val newBottom = (centerY + newH / 2f).coerceIn(0f, 1f)
        viewModel.updateParams(
            params.copy(geometryCropTop = newTop, geometryCropBottom = newBottom)
        )
    }
}
