package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.DemosaicAlgorithm
import com.alcedo.studio.data.model.RawDecodeParams
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel

private val DEMOSAIC_LABELS: Map<DemosaicAlgorithm, String> = mapOf(
    DemosaicAlgorithm.RCD to "RCD",
    DemosaicAlgorithm.AHD to "AHD",
    DemosaicAlgorithm.AMAZE to "AMaZE",
    DemosaicAlgorithm.DCB to "DCB",
    DemosaicAlgorithm.SIMPLE to "Simple"
)

@Composable
fun RawDecodePanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
    val raw = params.rawDecodeParams
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { rawDemosaicTitle },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParamsWithHistory(
                                params.copy(rawDecodeParams = RawDecodeParams()),
                                com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { rawReset },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                Text(
                    stringRes { rawDemosaicAlgorithm },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            state = androidx.compose.foundation.rememberScrollState()
                        ),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    DemosaicAlgorithm.entries.forEach { algo ->
                        val label = DEMOSAIC_LABELS[algo] ?: algo.name
                        val selected = raw.demosaicAlgorithm == algo
                        FilterChip(
                            selected = selected,
                            onClick = {
                                HapticFeedback.click(view)
                                val newRaw = raw.copy(demosaicAlgorithm = algo)
                                viewModel.updateParamsWithHistory(
                                    params.copy(rawDecodeParams = newRaw),
                                    com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                                )
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { rawProcessingOptions },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                RawBooleanRow(
                    label = stringRes { rawHighlightReconstruction },
                    description = stringRes { rawHighlightReconstructionDesc },
                    checked = raw.highlightReconstruction,
                    onToggle = {
                        HapticFeedback.click(view)
                        val newRaw = raw.copy(highlightReconstruction = it)
                        viewModel.updateParamsWithHistory(
                            params.copy(rawDecodeParams = newRaw),
                            com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                        )
                    }
                )

                RawBooleanRow(
                    label = stringRes { rawAutoBrightness },
                    description = stringRes { rawAutoBrightnessDesc },
                    checked = raw.autoBrightness,
                    onToggle = {
                        HapticFeedback.click(view)
                        val newRaw = raw.copy(autoBrightness = it)
                        viewModel.updateParamsWithHistory(
                            params.copy(rawDecodeParams = newRaw),
                            com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                        )
                    }
                )

                RawBooleanRow(
                    label = stringRes { rawUseCameraMatrix },
                    description = stringRes { rawUseCameraMatrixDesc },
                    checked = raw.useCameraMatrix,
                    onToggle = {
                        HapticFeedback.click(view)
                        val newRaw = raw.copy(useCameraMatrix = it)
                        viewModel.updateParamsWithHistory(
                            params.copy(rawDecodeParams = newRaw),
                            com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                        )
                    }
                )
            }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { rawSensorParameters },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                Text(
                    stringRes { rawBayerPattern },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    listOf("RGGB" to 0, "BGGR" to 1, "GRBG" to 2, "GBRG" to 3).forEach { (name, idx) ->
                        val selected = raw.bayerPattern == idx
                        FilterChip(
                            selected = selected,
                            onClick = {
                                HapticFeedback.click(view)
                                val newRaw = raw.copy(bayerPattern = idx)
                                viewModel.updateParamsWithHistory(
                                    params.copy(rawDecodeParams = newRaw),
                                    com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                                )
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AlcedoSpacing.md))

                AdjustmentSlider(
                    label = stringRes { rawWhiteLevel },
                    value = raw.whiteLevel.toFloat(),
                    range = 4095f..65535f,
                    onValueChange = {
                        val newRaw = raw.copy(whiteLevel = it.toInt())
                        viewModel.updateParamsWithHistory(
                            params.copy(rawDecodeParams = newRaw),
                            com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                        )
                    },
                    defaultValue = 65535f,
                    valueDisplayTransform = { it.toInt().toString() }
                )

                AdjustmentSlider(
                    label = stringRes { rawBlackLevel },
                    value = raw.blackLevel.toFloat(),
                    range = 0f..4095f,
                    onValueChange = {
                        val newRaw = raw.copy(blackLevel = it.toInt())
                        viewModel.updateParamsWithHistory(
                            params.copy(rawDecodeParams = newRaw),
                            com.alcedo.studio.data.model.OperatorType.RAW_DECODE
                        )
                    },
                    defaultValue = 0f,
                    valueDisplayTransform = { it.toInt().toString() }
                )
            }
        }
    }
}

@Composable
private fun RawBooleanRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AlcedoSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = AlcedoSpacing.xs)
    )
}
