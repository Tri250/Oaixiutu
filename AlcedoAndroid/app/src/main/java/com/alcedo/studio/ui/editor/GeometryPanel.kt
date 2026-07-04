package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

@Composable
fun GeometryPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAspectRatio by remember { mutableStateOf(AspectRatio.FREE) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Rotation & Flip
        SectionHeader(title = "Transform") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Rotate",
                    value = params.geometryRotate,
                    range = -45f..45f,
                    onValueChange = { onParamsChanged(params.copy(geometryRotate = it)) },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.1f°".format(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Flip horizontal
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip H")
                    }
                    OutlinedButton(
                        onClick = {
                            // Flip vertical
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip V")
                    }
                }

                OutlinedButton(
                    onClick = {
                        onParamsChanged(params.copy(geometryRotate = 0f))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto Straighten")
                }
            }
        }

        // Crop
        SectionHeader(title = "Crop") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onParamsChanged(params.copy(geometryCropLeft = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Crop Top",
                    value = params.geometryCropTop,
                    range = 0f..1f,
                    onValueChange = {
                        onParamsChanged(params.copy(geometryCropTop = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Crop Right",
                    value = params.geometryCropRight,
                    range = 0f..1f,
                    onValueChange = {
                        onParamsChanged(params.copy(geometryCropRight = it))
                    },
                    defaultValue = 1f
                )
                AdjustmentSlider(
                    label = "Crop Bottom",
                    value = params.geometryCropBottom,
                    range = 0f..1f,
                    onValueChange = {
                        onParamsChanged(params.copy(geometryCropBottom = it))
                    },
                    defaultValue = 1f
                )
            }
        }

        // Perspective
        SectionHeader(title = "Perspective Correction") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Horizontal",
                    value = (params.geometryPerspectiveDst[0] + params.geometryPerspectiveDst[2]) / 2f,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        val newPersp = params.geometryPerspectiveDst.clone()
                        newPersp[0] = 0f + it
                        newPersp[2] = 1f + it
                        onParamsChanged(params.copy(geometryPerspectiveDst = newPersp))
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
                        onParamsChanged(params.copy(geometryPerspectiveDst = newPersp))
                    },
                    defaultValue = 0f
                )
            }
        }

        // Lens Correction
        SectionHeader(title = "Lens Correction") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Distortion",
                    value = params.lensK1,
                    range = -0.5f..0.5f,
                    onValueChange = {
                        onParamsChanged(params.copy(lensK1 = it))
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Vignette",
                    value = params.lensVignetteStrength,
                    range = 0f..1f,
                    onValueChange = {
                        onParamsChanged(params.copy(lensVignetteStrength = it))
                    },
                    defaultValue = 0f
                )
            }
        }
    }
}