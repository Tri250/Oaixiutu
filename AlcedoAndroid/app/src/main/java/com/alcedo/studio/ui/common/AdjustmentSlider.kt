package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * A labeled adjustment slider with a numeric readout and a reset-to-default
 * button. This is the canonical control used across all editor panels.
 *
 * The slider emits the final value via [onValueChangeFinished] (committed) and
 * a live value via [onValueChange] (for live preview). The reset icon restores
 * [defaultValue] and reports it through [onValueChange] + [onValueChangeFinished].
 *
 * @param label    Human-readable control name (e.g. "Exposure").
 * @param value     Current value.
 * @param defaultValue Value considered "reset" (shown muted when value equals it).
 * @param range     Inclusive value range.
 * @param onValueChange Live updates while dragging.
 * @param onValueChangeFinished Fired when the drag ends / reset is pressed.
 * @param valueFormatter Converts the float to a display string.
 * @param enabled   Whether the control is interactive.
 * @param unit      Optional unit suffix appended to the readout.
 */
@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.2f".format(it) },
    unit: String = "",
    enabled: Boolean = true,
) {
    val isModified = value != defaultValue
    val valueColor = if (isModified) AlcedoColors.TextPrimary else AlcedoColors.TextTertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) AlcedoColors.TextSecondary else AlcedoColors.TextDisabled,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueFormatter(value) + unit,
                style = AlcedoMonoStyle,
                color = if (enabled) valueColor else AlcedoColors.TextDisabled,
                textAlign = TextAlign.End,
            )
            IconButton(
                onClick = {
                    onValueChange(defaultValue)
                    onValueChangeFinished()
                },
                enabled = enabled && isModified,
                modifier = Modifier
                    .size(24.dp)              // 20dp→24dp: larger touch target for Chinese users
                    .semantics { contentDescription = "Reset $label" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (enabled && isModified) AlcedoColors.TextTertiary else AlcedoColors.TextDisabled,
                    modifier = Modifier.size(16.dp),  // 14dp→16dp: larger icon for clarity
                )
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label slider" },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = AlcedoColors.Ash.copy(alpha = 0.18f),
                disabledThumbColor = AlcedoColors.TextDisabled,
                disabledActiveTrackColor = AlcedoColors.TextDisabled,
            ),
        )
    }
}

/**
 * Convenience for sliders that drive a single committed value with no separate
 * live/finished distinction (the host applies changes immediately).
 */
@Composable
fun AdjustmentSliderSimple(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.2f".format(value),
                style = AlcedoMonoStyle,
                color = AlcedoColors.TextPrimary,
                textAlign = TextAlign.End,
            )
        }
        if (step != null) {
            val steps = ((range.endInclusive - range.start) / step).toInt() - 1
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps.coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = AlcedoColors.Ash.copy(alpha = 0.18f),
                ),
            )
        } else {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = AlcedoColors.Ash.copy(alpha = 0.18f),
                ),
            )
        }
    }
}

/**
 * A remembered draggable float state used by custom trackball/curve controls.
 */
@Composable
fun rememberDraggableFloat(initial: Float): Pair<Float, (Float) -> Unit> {
    var state by remember { mutableFloatStateOf(initial) }
    return state to { state = it }
}
