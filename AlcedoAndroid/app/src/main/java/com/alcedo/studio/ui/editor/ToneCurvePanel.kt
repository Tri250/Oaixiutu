package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun ToneCurvePanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
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
        // ── Channel & Mode Selector ────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Tone Curve",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.resetToneCurve() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Curve",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

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
                            CurveChannel.GREEN -> Color(0xFF4CAF50)
                            CurveChannel.BLUE -> Color(0xFF4488FF)
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

                Spacer(modifier = Modifier.height(8.dp))

                when (curveMode) {
                    ToneCurveMode.POINT -> {
                        // Interactive curve
                        ToneCurveView(
                            controlPoints = controlPoints,
                            onControlPointsChanged = { newPoints ->
                                val count = newPoints.size
                                val newX = FloatArray(16) { if (it < count) newPoints[it].x else 0f }
                                val newY = FloatArray(16) { if (it < count) newPoints[it].y else 0f }
                                viewModel.updateToneCurve(newX, newY)
                            },
                            channel = selectedChannel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    ToneCurveMode.PARAMETRIC -> {
                        // Parametric curve sliders
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdjustmentSlider(
                                label = "Highlights",
                                value = params.highlights,
                                range = -1f..1f,
                                onValueChange = { viewModel.updateHighlights(it) },
                                defaultValue = 0f
                            )
                            AdjustmentSlider(
                                label = "Lights",
                                value = params.sigmoidShoulder,
                                range = 0f..1f,
                                onValueChange = {
                                    viewModel.updateParams(params.copy(sigmoidShoulder = it))
                                },
                                defaultValue = 0.5f
                            )
                            AdjustmentSlider(
                                label = "Darks",
                                value = params.sigmoidPivot,
                                range = 0f..1f,
                                onValueChange = {
                                    viewModel.updateParams(params.copy(sigmoidPivot = it))
                                },
                                defaultValue = 0.18f
                            )
                            AdjustmentSlider(
                                label = "Shadows",
                                value = params.shadows,
                                range = -1f..1f,
                                onValueChange = { viewModel.updateShadows(it) },
                                defaultValue = 0f
                            )
                            AdjustmentSlider(
                                label = "Sigmoid Contrast",
                                value = params.sigmoidContrast,
                                range = 0f..2f,
                                onValueChange = { viewModel.updateSigmoidContrast(it) },
                                defaultValue = 0f
                            )
                        }
                    }
                }
            }
        }

        // ── Reset Curve Button ─────────────────────────────────────
        OutlinedButton(
            onClick = { viewModel.resetToneCurve() },
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
