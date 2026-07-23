package com.alcedo.studio.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSlider
import com.alcedo.studio.ui.theme.AlcedoSpacing

/**
 * 2026 旗舰版 AdjustmentSlider
 *
 * 设计升级:
 * - 玻璃态轨道背景 (Glass Morphism inactive track)
 * - 渐变激活轨道 (Gradient active track)
 * - 拇指按压弹性缩放 (Animated thumb expansion)
 * - 精确数值弹出面板 (Numeric value popup)
 * - 渐变激活轨道
 * - 微动效反馈
 *
 * 摄影操作优化:
 * - 长按弹出精确数值输入
 * - 双击滑块重置为默认值
 * - 激活轨道微扩张(4dp→6dp),视觉反馈明确
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    defaultValue: Float,
    modifier: Modifier = Modifier,
    valueDisplayTransform: (Float) -> String = { "%.2f".format(it) },
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val isModified = value != defaultValue
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val thumbSize by animateDpAsState(
        targetValue = if (isDragged) AlcedoSlider.thumbRadiusActive * 2
        else AlcedoSlider.thumbRadius * 2,
        animationSpec = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbSize"
    )
    val trackHeight by animateDpAsState(
        targetValue = if (isDragged) AlcedoSlider.trackHeightActive
        else AlcedoSlider.trackHeight,
        animationSpec = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "trackHeight"
    )

    val labelColor by animateFloatAsState(
        targetValue = if (enabled && isModified) 1f else 0f,
        animationSpec = AlcedoAnimation.valueFlash,
        label = "labelFlash"
    )
    val blendedLabelColor = lerpColor(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        labelColor
    )

    var showInputDialog by remember { mutableStateOf(false) }
    // 稳定性: 拖动结束后通过 DisposableEffect 通知 finished,避免 onValueChange 被调用过多次
    val wasDragged = remember { mutableStateOf(false) }
    LaunchedEffect(isDragged) {
        if (wasDragged.value && !isDragged) {
            onValueChangeFinished?.invoke()
        }
        wasDragged.value = isDragged
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onLongPress = { showInputDialog = true },
                            onDoubleTap = { onValueChange(defaultValue) }
                        )
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 2026: 标签文字 — 半粗体,14sp,行高20sp
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (enabled) blendedLabelColor
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 2026: 数值显示 — 使用次要容器色背景,微圆角,更精致
                Text(
                    valueDisplayTransform(value),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isModified && enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                // 2026: 重置按钮 — 圆形触控目标,弹性缩放动画
                Box(
                    modifier = Modifier
                        .size(AlcedoSlider.resetButtonSize)
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled && isModified,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onValueChange(defaultValue)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringRes { sliderReset },
                        modifier = Modifier.size(AlcedoSlider.resetIconSize),
                        tint = if (isModified && enabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = AlcedoOpacity.disabled)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AlcedoSlider.labelSpacing))

        // 2026: 滑块 — 玻璃态轨道 + 渐变激活 + 拇指阴影
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onLongPress = { showInputDialog = true },
                            onDoubleTap = { onValueChange(defaultValue) }
                        )
                    }
                },
            thumb = {
                // 2026: 拇指 — 圆形 + 阴影 + 内发光
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(
                            elevation = if (isDragged) AlcedoSlider.thumbShadowElevation + 2.dp
                            else AlcedoSlider.thumbShadowElevation,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            },
            track = { sliderState ->
                // 2026: 轨道 — 圆角矩形 + 渐变激活
                // Clamp fraction to [0f, 1f] to prevent IllegalArgumentException from fillMaxWidth
                val rangeSpan = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val rawFraction = if (rangeSpan > 0f) {
                    (sliderState.value - sliderState.valueRange.start) / rangeSpan
                } else 0f
                val fraction = rawFraction.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(AlcedoRadius.full))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(trackHeight)
                                .clip(RoundedCornerShape(AlcedoRadius.full))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent
            )
        )
    }

    // 2026: 长按弹出精确数值输入对话框 — 圆角大卡片风格
    if (showInputDialog) {
        var inputValue by remember { mutableStateOf(String.format("%.2f", value)) }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            shape = RoundedCornerShape(AlcedoRadius.lg),
            title = {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text("输入数值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(AlcedoRadius.sm)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = inputValue.toFloatOrNull()
                    if (parsed != null) {
                        onValueChange(parsed.coerceIn(range.start, range.endInclusive))
                    }
                    showInputDialog = false
                }) {
                    Text(
                        "确定",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// 2026: 颜色插值辅助函数
private fun lerpColor(a: Color, b: Color, fraction: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction
    )
}

// 2026: 透明度常量,与 DesignTokens 保持一致
private object AlcedoOpacity {
    const val disabled = 0.38f
}