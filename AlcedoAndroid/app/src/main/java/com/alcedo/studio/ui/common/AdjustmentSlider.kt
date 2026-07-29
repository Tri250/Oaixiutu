package com.alcedo.studio.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * 增强型调节滑块 — 参考醒图/美图秀秀/Lightroom Mobile的滑块交互：
 * - **双击标签复位**：双击标签区域快速恢复到默认值
 * - **触觉反馈**：拖拽结束时触发振动反馈
 * - **动态颜色**：值已修改时数值高亮显示
 * - **更大的触摸目标**：为中国用户优化触控区域
 *
 * @param label        控制名称（如"曝光"）
 * @param value        当前值
 * @param defaultValue 默认值（"复位"目标）
 * @param range        值范围
 * @param onValueChange 实时更新回调
 * @param onValueChangeFinished 拖拽结束回调
 * @param valueFormatter 格式化函数
 * @param enabled      是否启用
 * @param unit         单位后缀
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
    val haptics = rememberHapticFeedback()
    val isModified = value != defaultValue
    val valueColor = if (isModified) AlcedoColors.TextPrimary else AlcedoColors.TextTertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 双击标签区域复位
                .pointerInput(label) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (enabled && isModified) {
                                haptics.click()
                                onValueChange(defaultValue)
                                onValueChangeFinished()
                            }
                        },
                    )
                },
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
            // 重置按钮（值修改后显示）
            IconButton(
                onClick = {
                    haptics.click()
                    onValueChange(defaultValue)
                    onValueChangeFinished()
                },
                enabled = enabled && isModified,
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "Reset $label" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (enabled && isModified) AlcedoColors.TextTertiary else AlcedoColors.TextDisabled,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = {
                haptics.commit()
                onValueChangeFinished()
            },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label slider" },
            colors = SliderDefaults.colors(
                thumbColor = AlcedoTheme.extendedColors.accent,
                activeTrackColor = AlcedoTheme.extendedColors.accent,
                inactiveTrackColor = AlcedoTheme.extendedColors.sliderTrackInactive,
                disabledThumbColor = AlcedoColors.TextDisabled,
                disabledActiveTrackColor = AlcedoColors.TextDisabled,
            ),
        )
    }
}

/**
 * 简化版滑块（不带默认值）。
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
    val haptics = rememberHapticFeedback()

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
                onValueChangeFinished = { haptics.commit() },
                valueRange = range,
                steps = steps.coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AlcedoTheme.extendedColors.accent,
                    activeTrackColor = AlcedoTheme.extendedColors.accent,
                    inactiveTrackColor = AlcedoTheme.extendedColors.sliderTrackInactive,
                ),
            )
        } else {
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                onValueChangeFinished = { haptics.commit() },
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = AlcedoTheme.extendedColors.accent,
                    activeTrackColor = AlcedoTheme.extendedColors.accent,
                    inactiveTrackColor = AlcedoTheme.extendedColors.sliderTrackInactive,
                ),
            )
        }
    }
}

/**
 * 可记忆的拖拽浮点状态。
 */
@Composable
fun rememberDraggableFloat(initial: Float): Pair<Float, (Float) -> Unit> {
    var state by remember { mutableFloatStateOf(initial) }
    return state to { state = it }
}