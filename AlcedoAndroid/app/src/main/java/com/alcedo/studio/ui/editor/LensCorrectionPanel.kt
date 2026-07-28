package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Lens correction panel. Toggles lens-profile correction, exposes the
 * distortion compensation slider, vignette correction (amount + midpoint),
 * and chromatic aberration sliders (red/blue). The lens profile is matched
 * against the camera/lens EXIF by the pipeline; manual controls are fallback.
 */
@Composable
fun LensCorrectionPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    onToggleLensProfile: (Boolean) -> Unit = {},
    onCommit: () -> Unit = {},
) {
    val s = Strings.res
    var profileExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.lensCorrection)

        // Lens profile toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.lensProfile,
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = params.lensProfileEnabled,
                onCheckedChange = onToggleLensProfile,
            )
        }

        // Distortion
        SectionHeader(title = s.distortion)
        AdjustmentSlider(
            label = s.distortion,
            value = params.distortion,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("distortion", it) },
            onValueChangeFinished = onCommit,
        )

        // Vignette
        SectionHeader(title = s.vignette)
        AdjustmentSlider(
            label = s.vignetteAmount,
            value = params.vignetteAmount,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("vignetteAmount", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.vignetteMidpoint,
            value = params.vignetteMidpoint,
            defaultValue = 50f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("vignetteMidpoint", it) },
            onValueChangeFinished = onCommit,
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
        )
        AdjustmentSlider(
            label = s.chromaAberrationB,
            value = params.chromaAberrationB,
            defaultValue = 0f,
            range = -50f..50f,
            valueFormatter = { "%+.1f".format(it) },
            onValueChange = { onUpdate("chromaAberrationB", it) },
            onValueChangeFinished = onCommit,
        )
    }
}
