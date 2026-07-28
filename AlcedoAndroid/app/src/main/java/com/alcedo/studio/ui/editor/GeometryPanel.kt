package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Geometry panel. Crop aspect-ratio presets (Free, 1:1, 4:3, 3:2, 16:9, 2:1, 3:1),
 * rotation, flip toggles, composition guide, and perspective (horizontal/vertical)
 * correction with auto option. Crop coordinates and rotation map directly to
 * [AdjustmentParams]; the host renders the crop overlay.
 */
@Composable
fun GeometryPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    onFlipH: () -> Unit = {},
    onFlipV: () -> Unit = {},
    onAspectChange: (String) -> Unit = {},
    onAutoPerspective: () -> Unit = {},
    onApplyCrop: () -> Unit = {},
    onResetCrop: () -> Unit = {},
    onCommit: () -> Unit = {},
) {
    val s = Strings.res
    var selectedAspect by remember { mutableStateOf(s.aspectFree) }
    var selectedGuide by remember { mutableStateOf(s.ruleOfThirds) }

    val aspects = listOf(
        s.aspectFree, s.aspectOriginal, s.aspect1x1,
        s.aspect4x3, s.aspect3x2, s.aspect16x9,
        s.aspect2x1, s.aspect3x1,
    )

    val guides = listOf(
        s.ruleOfThirds, s.goldenRatio, s.diagonal, s.centerCross,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        // ---- Crop Section ----
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
                    selected = aspect == selectedAspect,
                    onClick = {
                        selectedAspect = aspect
                        onAspectChange(aspect)
                    },
                    label = { Text(aspect, maxLines = 1) },
                )
            }
        }

        // Composition guide
        Text(
            text = s.compositionGuide,
            style = MaterialTheme.typography.labelMedium,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.padding(top = DesignTokens.spacingXs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            guides.forEach { guide ->
                FilterChip(
                    selected = guide == selectedGuide,
                    onClick = { selectedGuide = guide },
                    label = { Text(guide, maxLines = 1) },
                )
            }
        }

        // Apply / Reset crop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Button(
                onClick = onApplyCrop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AlcedoColors.AccentBlue),
            ) {
                Text(s.applyCrop, color = AlcedoColors.TextOnAccent)
            }
            OutlinedButton(
                onClick = onResetCrop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlcedoColors.TextSecondary),
            ) {
                Text(s.reset)
            }
        }

        // ---- Rotate Section ----
        SectionHeader(title = s.rotate)
        AdjustmentSlider(
            label = s.rotate,
            value = params.rotation,
            defaultValue = 0f,
            range = -45f..45f,
            valueFormatter = { "%+.1f°".format(it) },
            onValueChange = { onUpdate("rotation", it) },
            onValueChangeFinished = onCommit,
        )

        // Flip + rotate 90° buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlipToggle(label = s.flipHorizontal, active = params.flipH, onClick = onFlipH, modifier = Modifier.weight(1f))
            FlipToggle(label = s.flipVertical, active = params.flipV, onClick = onFlipV, modifier = Modifier.weight(1f))
        }

        // ---- Perspective Section ----
        SectionHeader(title = s.perspective)

        OutlinedButton(
            onClick = onAutoPerspective,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlcedoColors.AccentBlue),
        ) {
            Text(s.perspectiveAuto)
        }

        AdjustmentSlider(
            label = s.perspectiveHorizontal,
            value = params.perspectiveH,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("perspectiveH", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.perspectiveVertical,
            value = params.perspectiveV,
            defaultValue = 0f,
            range = -100f..100f,
            valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("perspectiveV", it) },
            onValueChangeFinished = onCommit,
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
