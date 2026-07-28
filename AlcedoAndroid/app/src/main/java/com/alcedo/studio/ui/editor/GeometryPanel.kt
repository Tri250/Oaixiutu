package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Geometry panel. Crop aspect-ratio presets, rotation, flip toggles and
 * perspective (horizontal/vertical) correction. Crop coordinates and rotation
 * map directly to [AdjustmentParams]; the host renders the crop overlay.
 */
@Composable
fun GeometryPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    onFlipH: () -> Unit = {},
    onFlipV: () -> Unit = {},
    onAspectChange: (String) -> Unit = {},
) {
    val s = Strings.res
    val aspects = listOf(
        s.aspectFree, s.aspectOriginal, s.aspect1x1,
        s.aspect4x3, s.aspect3x2, s.aspect16x9,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.crop)

        Text(
            text = s.aspectRatio,
            style = MaterialTheme.typography.labelMedium,
            color = AlcedoColors.TextTertiary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            aspects.forEach { aspect ->
                FilterChip(
                    selected = false,
                    onClick = { onAspectChange(aspect) },
                    label = { Text(aspect, maxLines = 1) },
                )
            }
        }

        SectionHeader(title = s.rotate)
        AdjustmentSlider(
            label = s.rotate,
            value = params.rotation,
            defaultValue = 0f,
            range = -45f..45f,
            valueFormatter = { "%+.1f°".format(it) },
            onValueChange = { onUpdate("rotation", it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            FlipToggle(label = s.flipHorizontal, active = params.flipH, onClick = onFlipH, modifier = Modifier.weight(1f))
            FlipToggle(label = s.flipVertical, active = params.flipV, onClick = onFlipV, modifier = Modifier.weight(1f))
        }

        SectionHeader(title = s.perspective)
        AdjustmentSlider(
            label = s.perspectiveHorizontal,
            value = params.perspectiveH,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("perspectiveH", it) },
        )
        AdjustmentSlider(
            label = s.perspectiveVertical,
            value = params.perspectiveV,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("perspectiveV", it) },
        )
    }
}

@Composable
private fun FlipToggle(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
}
