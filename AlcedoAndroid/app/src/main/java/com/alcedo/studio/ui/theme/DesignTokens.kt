package com.alcedo.studio.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════
// 2026 Alcedo Design Tokens – 统一设计令牌系统
// 面向中国摄影师的操作体验优化
// ═══════════════════════════════════════════════════════════════════

// ── Spacing Tokens ────────────────────────────────────────────────
// 基于 4dp 网格系统，符合 Material Design 3 规范
// 摄影师使用场景：快速扫视 + 精确操作，需要足够的呼吸空间
object AlcedoSpacing {
    val xs = 4.dp       // 极小间距：图标与文字间、标签内
    val sm = 8.dp       // 小间距：同类控件间
    val md = 12.dp      // 中间距：卡片内边距、分组间距
    val lg = 16.dp      // 大间距：面板内边距、区块间距
    val xl = 20.dp      // 超大间距：主面板外边距
    val xxl = 24.dp     // 双倍大间距：段间距
}

// ── Radius Tokens ─────────────────────────────────────────────────
// 渐进式圆角，创造视觉层次
object AlcedoRadius {
    val xs = 6.dp       // 标签、芯片、开关
    val sm = 10.dp      // 按钮、输入框
    val md = 14.dp      // 卡片、面板
    val lg = 20.dp      // 模态框、大卡片
    val xl = 28.dp      // 全屏表面
}

// ── Animation Specs ───────────────────────────────────────────────
// 2026 摄影师偏好：快速响应 + 流畅过渡，不拖泥带水
// 参考 AlcedoEasing 系统，补充面板过渡动画规格
object AlcedoAnimation {
    // 面板切换：快速、果断
    val panelEnter = tween<Float>(
        durationMillis = 280,
        easing = AlcedoEasing.emphasizedDecelerate
    )
    val panelExit = tween<Float>(
        durationMillis = 200,
        easing = AlcedoEasing.emphasizedAccelerate
    )

    // 滑块值变化：即时但有微妙的弹性
    val sliderChange = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 卡片展开/折叠：流畅优雅
    val expandCollapse = tween<Float>(
        durationMillis = 300,
        easing = AlcedoEasing.emphasized
    )

    // 开关切换：快速的阻尼弹簧
    val switchToggle = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // 内容淡入：柔和的透明度过渡
    val contentFadeIn = tween<Float>(
        durationMillis = 250,
        easing = AlcedoEasing.emphasizedDecelerate
    )
    val contentFadeOut = tween<Float>(
        durationMillis = 150,
        easing = AlcedoEasing.emphasizedAccelerate
    )

    // 示波器面板弹出
    val scopeSlideIn = tween<Float>(
        durationMillis = 320,
        easing = AlcedoEasing.emphasizedDecelerate
    )
    val scopeSlideOut = tween<Float>(
        durationMillis = 240,
        easing = AlcedoEasing.emphasizedAccelerate
    )

    // 长按对比：瞬时切换
    val compareOverlay = tween<Float>(
        durationMillis = 80,
        easing = LinearEasing
    )

    // 标签切换指示器：平滑滑动
    val tabIndicator = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 复位按钮微动效：按下缩放
    val resetButtonPress = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // 颜色轮拖动：跟手无延迟
    val colorWheelDrag = tween<Float>(
        durationMillis = 16,  // 1 frame at 60fps
        easing = LinearEasing
    )
}

// ── Elevation Tokens ──────────────────────────────────────────────
// 极简的阴影层次，暗色主题下通过颜色深浅而非阴影表现层次
object AlcedoElevation {
    const val level0 = 0.0f   // 平面
    const val level1 = 1.0f   // 卡片
    const val level2 = 3.0f   // 浮动面板
    const val level3 = 6.0f   // 对话框
    const val level4 = 12.0f  // 模态框
}

// ── Icon Size Tokens ──────────────────────────────────────────────
// 统一图标尺寸，确保视觉一致性
object AlcedoIconSize {
    val xs = 12.dp    // 内联图标
    val sm = 16.dp    // 按钮内图标
    val md = 20.dp    // 标签图标
    val lg = 24.dp    // 工具栏图标
    val xl = 32.dp    // 特征图标
}

// ── Opacity Tokens ─────────────────────────────────────────────────
// 标准透明度层，替代硬编码 alpha 值
object AlcedoOpacity {
    const val disabled = 0.38f       // 禁用状态
    const val hint = 0.60f           // 提示文字
    const val divider = 0.12f        // 分割线
    const val scrim = 0.32f          // 遮罩
    const val glassBackground = 0.72f // 玻璃背景
    const val subtleOverlay = 0.04f  // 细微叠加
    const val mediumOverlay = 0.08f  // 中等叠加
    const val strongOverlay = 0.12f  // 强叠加
}

// ── Slider Dimensions ──────────────────────────────────────────────
// 滑块尺寸统一，适合摄影师精确操作
object AlcedoSlider {
    val trackHeight = 4.dp           // 轨道高度
    val thumbRadius = 10.dp          // 拇指半径（适合手指操作）
    val thumbRadiusActive = 14.dp    // 激活时拇指半径
    val labelSpacing = 8.dp          // 标签与滑块间距
    val valueLabelWidth = 48.dp      // 数值标签宽度
}