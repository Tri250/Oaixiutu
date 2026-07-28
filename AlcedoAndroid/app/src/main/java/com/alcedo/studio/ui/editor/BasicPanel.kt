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
 * Tone (Basic) panel. Hosts the exposure, contrast, highlights, shadows,
 * whites and blacks sliders plus a clarity/sharpen pair. Each slider pushes a
 * live value to the pipeline via [onUpdate] and commits on release via
 * [onCommit].
 */
@Composable
fun BasicPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.panelTone)

        AdjustmentSlider(
            label = s.exposure, value = params.exposure, defaultValue = 0f,
            range = -2f..2f, valueFormatter = { "%.2f".format(it) },
            onValueChange = { onUpdate("exposure", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.contrast, value = params.contrast, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("contrast", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.highlights, value = params.highlights, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("highlights", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.shadows, value = params.shadows, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("shadows", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.whites, value = params.whites, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("whites", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.blacks, value = params.blacks, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("blacks", it) },
            onValueChangeFinished = onCommit,
        )

        SectionHeader(title = s.clarity + " / " + s.sharpen)
        AdjustmentSlider(
            label = s.clarity, value = params.clarity, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("clarity", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.sharpen, value = params.sharpen, defaultValue = 0f,
            range = 0f..100f, valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("sharpen", it) },
            onValueChangeFinished = onCommit,
        )
    }
}
