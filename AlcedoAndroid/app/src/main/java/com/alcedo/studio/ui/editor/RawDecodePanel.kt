package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * RAW decode panel. Black level, white point, highlight reconstruction method,
 * demosaic algorithm, noise-reduction strength, and chromatic aberration
 * correction. These only affect RAW sources; non-RAW images show the controls
 * disabled.
 */
@Composable
fun RawDecodePanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onUpdateString: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: () -> Unit = {},
) {
    val s = Strings.res
    var demoExpanded by remember { mutableStateOf(false) }
    val demosaics = listOf("AHD", "DCB", "Amaze", "RCD", "PPG", "IGV", "LMMSE")
    val highlightMethods = listOf(
        "Clip" to s.highlightClip,
        "Reconstruct" to s.highlightReconstruct,
        "Blend" to s.highlightBlend,
        "Color" to s.highlightColor,
    )
    var selectedHighlightMethod by remember { mutableStateOf(params.rawHighlightMethod) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.rawDecode)

        AdjustmentSlider(
            label = s.blackLevel,
            value = params.rawBlackLevel.toFloat(),
            defaultValue = 0f,
            range = 0f..4096f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawBlackLevel", it) },
            onValueChangeFinished = onCommit,
            enabled = enabled,
        )
        AdjustmentSlider(
            label = s.whitePoint,
            value = params.rawWhitePoint.toFloat(),
            defaultValue = 16383f,
            range = 1f..65535f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawWhitePoint", it) },
            onValueChangeFinished = onCommit,
            enabled = enabled,
        )

        // Demosaic algorithm
        Box {
            OutlinedTextField(
                value = params.rawDemosaic,
                onValueChange = {},
                readOnly = true,
                label = { Text(s.demosaic) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                trailingIcon = { TextButton(onClick = { demoExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = demoExpanded, onDismissRequest = { demoExpanded = false }) {
                demosaics.forEach { algo ->
                    DropdownMenuItem(
                        text = { Text(algo) },
                        onClick = {
                            onUpdateString("rawDemosaic", algo)
                            demoExpanded = false
                        },
                    )
                }
            }
        }

        // Highlight reconstruction
        SectionHeader(title = s.highlightReconstruction)

        Text(
            text = s.highlightMethod,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            highlightMethods.forEach { (value, label) ->
                FilterChip(
                    selected = value == selectedHighlightMethod,
                    onClick = {
                        selectedHighlightMethod = value
                        onUpdateString("rawHighlightMethod", value)
                    },
                    label = { Text(label, maxLines = 1) },
                    enabled = enabled,
                )
            }
        }

        AdjustmentSlider(
            label = s.noiseReduction,
            value = params.rawNoiseReduction,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawNoiseReduction", it) },
            onValueChangeFinished = onCommit,
            enabled = enabled,
        )

        // Chromatic aberration
        SectionHeader(title = s.chromaAberration)

        AdjustmentSlider(
            label = s.chromaAberrationR,
            value = params.chromaAberrationR,
            defaultValue = 0f,
            range = -50f..50f,
            valueFormatter = { "%+.1f".format(it) },
            onValueChange = { onUpdate("chromaAberrationR", it) },
            onValueChangeFinished = onCommit,
            enabled = enabled,
        )
        AdjustmentSlider(
            label = s.chromaAberrationB,
            value = params.chromaAberrationB,
            defaultValue = 0f,
            range = -50f..50f,
            valueFormatter = { "%+.1f".format(it) },
            onValueChange = { onUpdate("chromaAberrationB", it) },
            onValueChangeFinished = onCommit,
            enabled = enabled,
        )
    }
}
