package com.alcedo.studio.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * 底部快捷操作栏 — 参考国内主流摄影App（醒图/美图秀秀/Lightroom Mobile）的交互模式：
 * 将撤销/重做/自动增强/对比/分享/重置等高频操作放置在拇指可达的底部区域。
 *
 * 设计原则：
 * - 拇指友好：底部浮动栏，位于面板上方，单手可触达
 * - 视觉反馈：按钮按下有缩放动画，活跃状态高亮
 * - 触觉反馈：集成 [HapticFeedback] 提供按键手感
 * - 智能显隐：有未保存更改时显示重置按钮，有历史记录时显示撤销/重做
 */
@Composable
fun QuickActionsBar(
    canUndo: Boolean,
    canRedo: Boolean,
    canReset: Boolean,
    isComparing: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAutoEnhance: (() -> Unit)?,
    onCompareToggle: () -> Unit,
    onShare: (() -> Unit)?,
    onReset: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHapticFeedback()
    val accent = AlcedoTheme.extendedColors.accent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .background(AlcedoColors.CardActive.copy(alpha = 0.95f))
            .padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 撤销
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = "撤销",
            enabled = canUndo,
            accent = accent,
            onClick = { haptics.click(); onUndo() },
        )

        // 重做
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = "重做",
            enabled = canRedo,
            accent = accent,
            onClick = { haptics.click(); onRedo() },
        )

        // 分隔
        Spacer(modifier = Modifier.width(2.dp))

        // 自动增强 (一键美化)
        onAutoEnhance?.let { handler ->
            QuickActionButton(
                icon = Icons.Filled.AutoFixHigh,
                label = "自动",
                enabled = true,
                accent = AlcedoColors.WarmAccent,
                highlightColor = AlcedoColors.WarmAccent,
                onClick = { haptics.click(); handler() },
            )
        }

        Spacer(modifier = Modifier.width(2.dp))

        // 对比 (长按/切换)
        QuickActionButton(
            icon = Icons.Outlined.Compare,
            label = "对比",
            enabled = true,
            accent = accent,
            isActive = isComparing,
            onClick = { haptics.click(); onCompareToggle() },
        )

        // 分享
        onShare?.let { handler ->
            QuickActionButton(
                icon = Icons.Outlined.Share,
                label = "分享",
                enabled = true,
                accent = accent,
                onClick = { haptics.click(); handler() },
            )
        }

        // 重置
        onReset?.let { handler ->
            QuickActionButton(
                icon = Icons.Outlined.DeleteSweep,
                label = "重置",
                enabled = canReset,
                accent = AlcedoColors.Danger,
                onClick = { haptics.click(); handler() },
            )
        }
    }
}

/**
 * 单个快捷操作按钮。带缩放动画反馈和两种状态颜色。
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    highlightColor: Color = accent,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> AlcedoColors.TextDisabled
            isActive -> highlightColor
            else -> AlcedoColors.TextSecondary
        },
        animationSpec = tween(DesignTokens.sliderColorMs),
        label = "iconColor",
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(DesignTokens.touchTargetMin),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}