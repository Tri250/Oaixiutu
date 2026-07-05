package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun GeometryPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.FREE) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

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
                        "Transform",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            viewModel.updateParams(
                                params.copy(geometryRotate = 0f, geometryScale = 1f)
                            )
                            flipHorizontal = false
                            flipVertical = false
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Transform",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AdjustmentSlider(
                    label = "Rotate",
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
                            flipHorizontal = !flipHorizontal
                            val currentScale = params.geometryScale
                            viewModel.updateParams(
                                params.copy(
                                    geometryScale = if (flipHorizontal) -currentScale else currentScale
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip H")
                    }
                    OutlinedButton(
                        onClick = {
                            flipVertical = !flipVertical
                            viewModel.updateParams(
                                params.copy(
                                    geometryScale = if (flipVertical) -params.geometryScale else params.geometryScale
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip V")
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel.updateParams(params.copy(geometryRotate = 0f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto Straighten")
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
                        "Crop",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
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
                            contentDescription = "Reset Crop",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Aspect Ratio",
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
                            onClick = { selectedAspectRatio = ratio },
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
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
                            onClick = { selectedAspectRatio = ratio },
                            label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                AdjustmentSlider(
                    label = "Crop Left",
                    value = params.geometryCropLeft,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropLeft = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Crop Top",
                    value = params.geometryCropTop,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropTop = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Crop Right",
                    value = params.geometryCropRight,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(geometryCropRight = it))
                    },
                    defaultValue = 1f
                )
                AdjustmentSlider(
                    label = "Crop Bottom",
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
                        "Perspective",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
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
                            contentDescription = "Reset Perspective",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AdjustmentSlider(
                    label = "Horizontal",
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
                    label = "Vertical",
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
                        "Lens Correction",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            viewModel.updateLensCorrection(0f, 0f, 0f, 0f, 0f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Lens",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                AdjustmentSlider(
                    label = "Distortion (K1)",
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
                    label = "K2",
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
