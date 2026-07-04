package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

@Composable
fun ToneCurvePanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var curveMode by remember { mutableStateOf(ToneCurveMode.POINT) }

    val controlPoints = remember(params.toneCurveX, params.toneCurveY) {
        val count = params.toneCurvePoints
        (0 until count).map { i ->
            CurvePoint(params.toneCurveX[i], params.toneCurveY[i])
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToneCurveMode.entries.forEach { mode ->
                FilterChip(
                    selected = curveMode == mode,
                    onClick = { curveMode = mode },
                    label = { Text(mode.label) }
                )
            }
        }

        // Channel selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CurveChannel.entries.forEach { ch ->
                val isSelected = selectedChannel == ch
                val color = when (ch) {
                    CurveChannel.RGB -> MaterialTheme.colorScheme.primary
                    CurveChannel.RED -> MaterialTheme.colorScheme.error
                    CurveChannel.GREEN -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                    CurveChannel.BLUE -> androidx.compose.ui.graphics.Color(0xFF4488FF)
                }
                AssistChip(
                    onClick = { selectedChannel = ch },
                    label = {
                        Text(
                            ch.name,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (curveMode) {
            ToneCurveMode.POINT -> {
                // Interactive curve
                ToneCurveView(
                    controlPoints = controlPoints,
                    onControlPointsChanged = { newPoints ->
                        val newX = FloatArray(16) { if (it < newPoints.size) newPoints[it].x else 0f }
                        val newY = FloatArray(16) { if (it < newPoints.size) newPoints[it].y else 0f }
                        onParamsChanged(
                            params.copy(
                                toneCurveX = newX,
                                toneCurveY = newY,
                                toneCurvePoints = newPoints.size
                            )
                        )
                    },
                    channel = selectedChannel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            ToneCurveMode.PARAMETRIC -> {
                SectionHeader(title = "Parametric Curve") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdjustmentSlider(
                            label = "Highlights",
                            value = params.highlights,
                            range = -1f..1f,
                            onValueChange = { onParamsChanged(params.copy(highlights = it)) },
                            defaultValue = 0f
                        )
                        AdjustmentSlider(
                            label = "Lights",
                            value = params.sigmoidShoulder,
                            range = 0f..1f,
                            onValueChange = { onParamsChanged(params.copy(sigmoidShoulder = it)) },
                            defaultValue = 0.5f
                        )
                        AdjustmentSlider(
                            label = "Darks",
                            value = params.sigmoidPivot,
                            range = 0f..1f,
                            onValueChange = { onParamsChanged(params.copy(sigmoidPivot = it)) },
                            defaultValue = 0.18f
                        )
                        AdjustmentSlider(
                            label = "Shadows",
                            value = params.shadows,
                            range = -1f..1f,
                            onValueChange = { onParamsChanged(params.copy(shadows = it)) },
                            defaultValue = 0f
                        )
                        AdjustmentSlider(
                            label = "Sigmoid Contrast",
                            value = params.sigmoidContrast,
                            range = 0f..2f,
                            onValueChange = { onParamsChanged(params.copy(sigmoidContrast = it)) },
                            defaultValue = 0f
                        )
                    }
                }
            }
        }

        // Reset button
        OutlinedButton(
            onClick = {
                onParamsChanged(
                    params.copy(
                        toneCurveX = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                        toneCurveY = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                        toneCurvePoints = 5
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Curve")
        }
    }
}

enum class ToneCurveMode(val label: String) {
    POINT("Point"),
    PARAMETRIC("Parametric")
}