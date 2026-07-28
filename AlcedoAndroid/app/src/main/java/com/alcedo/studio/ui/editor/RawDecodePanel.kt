package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * RAW decode panel. Black level, white point, demosaic algorithm and
 * noise-reduction strength. These only affect RAW sources; non-RAW images
 * show the controls disabled.
 */
@Composable
fun RawDecodePanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val s = Strings.res
    var demoExpanded by remember { mutableStateOf(false) }
    val demosaics = listOf("AHD", "DCB", "Amaze", "RCD", "PPG")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.rawDecode)

        AdjustmentSlider(
            label = s.blackLevel,
            value = params.rawBlackLevel.toFloat(),
            defaultValue = 0f,
            range = 0f..4096f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawBlackLevel", it) },
            enabled = enabled,
        )
        AdjustmentSlider(
            label = s.whitePoint,
            value = params.rawWhitePoint.toFloat(),
            defaultValue = 16383f,
            range = 1f..65535f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawWhitePoint", it) },
            enabled = enabled,
        )

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
                        onClick = { demoExpanded = false },
                    )
                }
            }
        }

        SectionHeader(title = s.highlightReconstruction)
        AdjustmentSlider(
            label = s.noiseReduction,
            value = params.rawNoiseReduction,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("rawNoiseReduction", it) },
            enabled = enabled,
        )
    }
}
