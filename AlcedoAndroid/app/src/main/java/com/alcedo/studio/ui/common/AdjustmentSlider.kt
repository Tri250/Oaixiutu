package com.alcedo.studio.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes

@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    defaultValue: Float,
    modifier: Modifier = Modifier,
    valueDisplayTransform: (Float) -> String = { "%.2f".format(it) },
    enabled: Boolean = true
) {
    val isModified = value != defaultValue
    val labelColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    else if (isModified) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface

    var showInputDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onLongPress = { showInputDialog = true }
                        )
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    valueDisplayTransform(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isModified && enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(4.dp))
                // 重置按钮 — 更大触控目标,暖色强调
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled && isModified) {
                            onValueChange(defaultValue)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringRes { sliderReset },
                        modifier = Modifier.size(16.dp),
                        tint = if (isModified && enabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onLongPress = { showInputDialog = true }
                        )
                    }
                },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )
    }

    // 长按弹出精确数值输入对话框
    if (showInputDialog) {
        var inputValue by remember { mutableStateOf(String.format("%.2f", value)) }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text("输入数值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = inputValue.toFloatOrNull()
                    if (parsed != null) {
                        onValueChange(parsed.coerceIn(range.start, range.endInclusive))
                    }
                    showInputDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("取消") }
            }
        )
    }
}
