package com.alcedo.studio.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens
import kotlinx.coroutines.delay

/**
 * 增强型调节滑块 — 参考 RapidRAW / 醒图 / Lightroom Mobile 的滑块交互：
 * - **双击标签复位**：双击标签区域快速恢复到默认值
 * - **触觉反馈**：拖拽结束时触发振动反馈
 * - **动态颜色**：值已修改时数值高亮显示
 * - **双极中心标记**：范围跨越 0（如 -100..100）时在轨道中央绘制刻度线，
 *   帮助用户快速定位"中性"位置（对应 RapidRAW 双极滑块的视觉锚点）
 * - **长按提示**：长按标签弹出提示气泡，说明可双击复位
 *   （触摸端等价于 RapidRAW 的悬停 Tooltip，TOOLTIP_DELAY=500ms）
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
    val s = Strings.res
    val isModified = value != defaultValue
    val valueColor = if (isModified) AlcedoColors.TextPrimary else AlcedoColors.TextTertiary
    // 双极范围：起点为负、终点为正（如曝光 -5..5、对比度 -100..100）
    val isBipolar = range.start < 0f && range.endInclusive > 0f
    // 长按提示气泡显隐（RapidRAW TOOLTIP_DELAY=500ms 在触摸端的等价物）
    var showHint by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(label) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (enabled && isModified) {
                                haptics.click()
                                onValueChange(defaultValue)
                                onValueChangeFinished()
                            }
                        },
                        onLongPress = {
                            // 长按弹出复位提示（触摸端 Tooltip）
                            haptics.longPress()
                            showHint = true
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
        // 长按提示气泡（等价于 RapidRAW GlobalTooltip）
        if (showHint) {
            SliderHintPopup(
                text = s.doubleTapToReset,
                onDismiss = { showHint = false },
            )
        }
        // 滑块轨道 + 双极中心标记叠加层
        Box(modifier = Modifier.fillMaxWidth()) {
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
            // 双极滑块中心刻度线（RapidRAW 双极滑块视觉锚点）
            if (isBipolar) {
                BipolarCenterTick(
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * 双极滑块的中心刻度线。绘制一条细短的竖线，标记"中性"位置（0）。
 * 颜色比轨道略亮，避免与拖动指示冲突。
 */
@Composable
private fun BipolarCenterTick(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tickColor = if (enabled) {
        AlcedoColors.TextTertiary
    } else {
        AlcedoColors.TextDisabled
    }
    val density = LocalDensity.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.sliderTrackHeight * 2.2f),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        // 刻度线高度略大于轨道高度
        val tickHeight = with(density) { DesignTokens.sliderTrackHeight.toPx() } * 1.6f
        val tickWidth = with(density) { 1.dp.toPx() }
        val centerY = canvasHeight / 2f
        val centerX = canvasWidth / 2f
        drawRect(
            color = tickColor,
            topLeft = Offset(centerX - tickWidth / 2f, centerY - tickHeight / 2f),
            size = Size(tickWidth, tickHeight),
        )
    }
}

/**
 * 滑块长按提示气泡。复刻 RapidRAW GlobalTooltip 的视觉风格：
 * 半透明背景 + 模糊边框 + 圆角 + 小号文字，2.5 秒后自动消失。
 */
@Composable
private fun SliderHintPopup(
    text: String,
    onDismiss: () -> Unit,
) {
    // 2.5 秒后自动消失（RapidRAW Tooltip 在 mouseout 时消失的触摸端等价物）
    LaunchedEffect(text) {
        delay(2500L)
        onDismiss()
    }
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = DesignTokens.spacingSm)
                .clip(RoundedCornerShape(DesignTokens.radiusMd))
                .background(AlcedoColors.Surface.copy(alpha = 0.92f))
                .border(
                    width = 1.dp,
                    color = AlcedoColors.TextSecondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                )
                .padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextPrimary,
            )
        }
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