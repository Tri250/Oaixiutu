package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.common.formatSliderValue

@Composable
fun BasicPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Exposure
        SectionHeader(title = "Light") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Exposure",
                    value = params.exposure,
                    range = -5f..5f,
                    onValueChange = { onParamsChanged(params.copy(exposure = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Contrast",
                    value = params.contrast,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(contrast = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Highlights",
                    value = params.highlights,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(highlights = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Shadows",
                    value = params.shadows,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(shadows = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Midtones",
                    value = params.midtones,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(midtones = it)) },
                    defaultValue = 0f
                )
            }
        }

        // White Balance
        SectionHeader(title = "White Balance") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Temperature",
                    value = params.whiteBalanceTemp,
                    range = 2000f..15000f,
                    onValueChange = { onParamsChanged(params.copy(whiteBalanceTemp = it)) },
                    defaultValue = 6500f,
                    valueDisplayTransform = { "%.0fK".format(it) }
                )
                AdjustmentSlider(
                    label = "Tint",
                    value = params.whiteBalanceTint,
                    range = -100f..100f,
                    onValueChange = { onParamsChanged(params.copy(whiteBalanceTint = it)) },
                    defaultValue = 0f
                )
            }
        }

        // Color
        SectionHeader(title = "Color") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Saturation",
                    value = params.saturation,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(saturation = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Vibrance",
                    value = params.vibrance,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(vibrance = it)) },
                    defaultValue = 0f
                )
            }
        }

        // Split Toning
        SectionHeader(title = "Split Toning") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Highlight Hue",
                    value = params.tintHighlightHue,
                    range = 0f..360f,
                    onValueChange = { onParamsChanged(params.copy(tintHighlightHue = it)) },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.0f°".format(it) }
                )
                AdjustmentSlider(
                    label = "Highlight Strength",
                    value = params.tintHighlightStrength,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(tintHighlightStrength = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Shadow Hue",
                    value = params.tintShadowHue,
                    range = 0f..360f,
                    onValueChange = { onParamsChanged(params.copy(tintShadowHue = it)) },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.0f°".format(it) }
                )
                AdjustmentSlider(
                    label = "Shadow Strength",
                    value = params.tintShadowStrength,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(tintShadowStrength = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Balance",
                    value = params.tintBalance,
                    range = -1f..1f,
                    onValueChange = { onParamsChanged(params.copy(tintBalance = it)) },
                    defaultValue = 0f
                )
            }
        }
    }
}