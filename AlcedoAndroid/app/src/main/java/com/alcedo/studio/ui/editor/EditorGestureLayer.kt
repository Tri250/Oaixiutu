package com.alcedo.studio.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.rememberHapticFeedback
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * 编辑器手势层 — 参考国内主流摄影App（醒图/美图秀秀/Lightroom Mobile）的交互模式。
 *
 * 提供以下手势增强：
 * - **滑动切换照片**：左右滑动切换上/下一张照片（带方向指示器动画）
 * - **双击复位**：双击图片区域复位所有调整参数到默认值
 * - **触觉反馈**：所有手势操作均有触觉反馈
 *
 * 使用方式：
 * ```kotlin
 * EditorGestureLayer(
 *     canSwipeLeft = hasPreviousPhoto,
 *     canSwipeRight = hasNextPhoto,
 *     onSwipeLeft = { viewModel.previousPhoto() },
 *     onSwipeRight = { viewModel.nextPhoto() },
 *     onDoubleTapReset = { viewModel.resetAdjustments() },
 * ) {
 *     // 图片预览内容
 *     ZoomableImageView(...)
 * }
 * ```
 */
@Composable
fun EditorGestureLayer(
    canSwipeLeft: Boolean,
    canSwipeRight: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDoubleTapReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHapticFeedback()

    // Swipe indicator state
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }

    // Swipe indicator animation
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (swipeDirection != null) (swipeProgress * 0.8f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(150),
    )

    // Swipe background color animation
    val swipeBgColor by animateColorAsState(
        targetValue = when (swipeDirection) {
            SwipeDirection.LEFT -> AlcedoColors.AccentBlue.copy(alpha = 0.15f)
            SwipeDirection.RIGHT -> AlcedoColors.AccentBlue.copy(alpha = 0.15f)
            null -> Color.Transparent
        },
        animationSpec = tween(200),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(swipeBgColor)
            // Horizontal swipe to switch photos
            .pointerInput(canSwipeLeft, canSwipeRight) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeProgress = 0f },
                    onDragEnd = {
                        if (swipeProgress > 0.3f) {
                            when (swipeDirection) {
                                SwipeDirection.LEFT -> {
                                    if (canSwipeLeft) {
                                        haptics.commit()
                                        onSwipeLeft()
                                    }
                                }
                                SwipeDirection.RIGHT -> {
                                    if (canSwipeRight) {
                                        haptics.commit()
                                        onSwipeRight()
                                    }
                                }
                                null -> {}
                            }
                        }
                        swipeDirection = null
                        swipeProgress = 0f
                    },
                    onDragCancel = {
                        swipeDirection = null
                        swipeProgress = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        swipeProgress = (kotlin.math.abs(dragAmount) / 300f).coerceIn(0f, 1f)
                        swipeDirection = if (dragAmount > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                    },
                )
            }
            // Double tap to reset adjustments
            .pointerInput(onDoubleTapReset) {
                if (onDoubleTapReset != null) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptics.click()
                            onDoubleTapReset()
                        },
                    )
                }
            },
    ) {
        // Main content
        content()

        // Swipe direction indicator overlay
        if (swipeDirection != null && indicatorAlpha > 0.05f) {
            val isLeft = swipeDirection == SwipeDirection.LEFT
            val alignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
            val icon = if (isLeft) Icons.AutoMirrored.Filled.KeyboardArrowLeft
                else Icons.AutoMirrored.Filled.KeyboardArrowRight

            Box(
                modifier = Modifier
                    .align(alignment)
                    .alpha(indicatorAlpha)
                    .padding(horizontal = DesignTokens.spacingMd)
                    .background(
                        AlcedoColors.SurfaceScrim,
                        RoundedCornerShape(DesignTokens.radiusFull),
                    )
                    .padding(DesignTokens.spacingSm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isLeft) "上一张" else "下一张",
                    tint = AlcedoColors.AccentBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private enum class SwipeDirection { LEFT, RIGHT }