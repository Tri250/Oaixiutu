package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Color panel: saturation, vibrance and tint sliders. The per-hue HSL bands
 * live in [HlsProfilePanel]; this panel covers the global colour controls.
 */
@Composable
fun ColorPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.panelColor)
        AdjustmentSlider(
            label = s.saturation, value = params.saturation, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("saturation", it) }, onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.vibrance, value = params.vibrance, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("vibrance", it) }, onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.tint, value = params.tint, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("tint", it) }, onValueChangeFinished = onCommit,
        )
    }
}
