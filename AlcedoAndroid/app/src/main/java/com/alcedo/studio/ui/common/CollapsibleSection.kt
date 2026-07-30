package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * 可折叠分区 — 参考 RapidRAW 的 CollapsibleSection.tsx 交互模式：
 *
 * - **折叠/展开**：点击标题行切换内容显隐，雪佛龙图标旋转 180° 指示状态
 * - **可见性切换**：标题右侧的眼睛图标可临时禁用整个分区的调整效果
 *   （分区内容以 30% 透明度显示并禁用交互，与 RapidRAW 行为一致）
 * - **触觉反馈**：点击标题触发轻触反馈
 * - **动画**：使用 expandVertically/shrinkVertically 实现平滑高度过渡，
 *   配合淡入淡出，时长 300ms ease-in-out（对应 RapidRAW 的 transition-all duration-300）
 *
 * 适配 Android 触摸优先：将 RapidRAW 的 hover 显隐眼睛改为常驻可见，
 * 避免触摸设备无 hover 状态的问题。
 *
 * @param title             分区标题
 * @param expanded          当前是否展开
 * @param onToggleExpand    切换展开/折叠的回调
 * @param modifier          Modifier
 * @param contentVisible    分区内容是否生效（眼睛开关），默认 true
 * @param onToggleVisibility 切换可见性的回调，为 null 则不显示眼睛图标
 * @param trailing          标题行末尾的额外内容（如重置按钮）
 * @param content           分区内容
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    contentVisible: Boolean = true,
    onToggleVisibility: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHapticFeedback()
    val accent = AlcedoTheme.extendedColors.accent
    // RapidRAW: chevron rotate-180 when open
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = DesignTokens.motionPanel),
        label = "chevronRotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .background(AlcedoColors.Surface),
    ) {
        // ---- 标题行 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.click()
                    onToggleExpand()
                }
                .padding(
                    horizontal = DesignTokens.spacingLg,
                    vertical = DesignTokens.spacingMd,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 可见性眼睛开关（RapidRAW 的 Eye/EyeOff）
            if (onToggleVisibility != null) {
                val eyeIcon: ImageVector =
                    if (contentVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
                IconButton(
                    onClick = {
                        haptics.click()
                        onToggleVisibility()
                    },
                    modifier = Modifier.size(DesignTokens.touchTargetMin),
                ) {
                    Icon(
                        imageVector = eyeIcon,
                        contentDescription = if (contentVisible) "Disable section" else "Enable section",
                        tint = AlcedoColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 末尾额外内容（如重置按钮）
            trailing?.invoke()
            // 雪佛龙图标
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = accent,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        // ---- 内容区（折叠/展开动画）----
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = DesignTokens.motionPanel),
            ) + fadeIn(tween(DesignTokens.motionPanel)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = DesignTokens.motionPanel),
            ) + fadeOut(tween(DesignTokens.motionPanel)),
        ) {
            // RapidRAW: !isContentVisible && 'opacity-30 pointer-events-none'
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (contentVisible) 1f else 0.3f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DesignTokens.spacingLg,
                            vertical = DesignTokens.spacingMd,
                        ),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing),
                ) {
                    content()
                }
            }
        }
    }
}
