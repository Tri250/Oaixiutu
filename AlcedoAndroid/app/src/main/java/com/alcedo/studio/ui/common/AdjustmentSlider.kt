package com.alcedo.studio.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    defaultValue: Float = range.start + (range.endInclusive - range.start) / 2f,
    showReset: Boolean = true,
    valueDisplayTransform: (Float) -> String = { formatSliderValue(it) },
    steps: Int = 0
) {
    var showResetAnimation by remember { mutableStateOf(false) }
    val isAtDefault = remember(value) { value == defaultValue }
    val view = LocalView.current

    LaunchedEffect(showResetAnimation) {
        if (showResetAnimation) {
            delay(1000)
            showResetAnimation = false
        }
    }

    val sliderColor by animateColorAsState(
        targetValue = if (isAtDefault) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.primary,
        label = "sliderColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (showReset && !isAtDefault) {
                                onValueChange(defaultValue)
                                showResetAnimation = true
                                HapticFeedback.click(view)
                            }
                        }
                    )
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                if (showResetAnimation) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = valueDisplayTransform(value),
                style = MaterialTheme.typography.bodySmall,
                color = if (isAtDefault) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                val wasAtMin = value == range.start
                val wasAtMax = value == range.endInclusive
                onValueChange(newValue)
                if (newValue == range.start && !wasAtMin) {
                    HapticFeedback.sliderStop(view)
                } else if (newValue == range.endInclusive && !wasAtMax) {
                    HapticFeedback.sliderStop(view)
                }
            },
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = sliderColor,
                activeTrackColor = sliderColor
            )
        )
    }
}

@Composable
fun AdjustmentSliderWithReset(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Float = range.start + (range.endInclusive - range.start) / 2f,
    valueDisplayTransform: (Float) -> String = { formatSliderValue(it) }
) {
    var isDefault by remember(value) { mutableStateOf(value == defaultValue) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueDisplayTransform(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDefault) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary
                )
                if (!isDefault) {
                    IconButton(
                        onClick = {
                            onValueChange(defaultValue)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset $label",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun formatSliderValue(value: Float): String {
    return when {
        value == value.toLong().toFloat() -> "%.0f".format(value)
        kotlin.math.abs(value) < 0.01f -> "0"
        kotlin.math.abs(value) >= 10f -> "%.1f".format(value)
        else -> "%.2f".format(value)
    }
}