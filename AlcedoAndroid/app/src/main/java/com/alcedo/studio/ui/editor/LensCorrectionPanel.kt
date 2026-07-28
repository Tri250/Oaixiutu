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
 * Lens correction panel. Toggles lens-profile correction and exposes the
 * distortion compensation slider. The lens profile is matched against the
 * camera/lens EXIF by the pipeline; manual distortion is a fallback.
 */
@Composable
fun LensCorrectionPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.lensCorrection)
        AdjustmentSlider(
            label = s.distortion,
            value = 0f,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("distortion", it) },
        )
        AdjustmentSlider(
            label = s.lensProfile,
            value = if (params.lensProfileEnabled) 1f else 0f,
            defaultValue = 0f,
            range = 0f..1f,
            valueFormatter = { if (it > 0.5f) "On" else "Off" },
            onValueChange = { onUpdate("lensProfileEnabled", it) },
        )
    }
}
