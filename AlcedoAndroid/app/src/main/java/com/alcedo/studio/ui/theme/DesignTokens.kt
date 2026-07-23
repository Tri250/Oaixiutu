package com.alcedo.studio.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.AlcedoEasing

// ═══════════════════════════════════════════════════════════════════
// 2026 Alcedo Design Tokens – 统一设计令牌系统
// 面向中国摄影师的操作体验优化
// 品牌风格: 精密、克制、呼吸感 — 2026 旗舰摄影 App 标准
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
    val xxxl = 32.dp    // 三倍大间距：面板标题区
}

// ── Radius Tokens ─────────────────────────────────────────────────
// 渐进式圆角，创造视觉层次
object AlcedoRadius {
    val xs = 6.dp       // 标签、芯片、开关
    val sm = 10.dp      // 按钮、输入框
    val md = 14.dp      // 卡片、面板
    val lg = 20.dp      // 模态框、大卡片
    val xl = 28.dp      // 全屏表面
    val full = 999.dp   // 胶囊形（全圆角）
}

// ── Stroke / Border Width Tokens ──────────────────────────────────
object AlcedoStroke {
    val thin = 0.5.dp   // 细微分割线
    val normal = 1.dp   // 标准边框
    val medium = 1.5.dp // 强调边框
    val thick = 2.dp    // 焦点边框
}

// ── Shadow Tokens ─────────────────────────────────────────────────
// 暗色主题下通过颜色深浅 + 微弱扩散表现层次
// 2026 趋势: 极简阴影,避免过度使用
data class ShadowSpec(val offsetY: Float, val blur: Float, val alpha: Float)
object AlcedoShadow {
    val level1 = ShadowSpec(1f, 2f, 0.08f)   // 卡片微浮
    val level2 = ShadowSpec(2f, 4f, 0.12f)   // 浮动面板
    val level3 = ShadowSpec(4f, 8f, 0.16f)   // 对话框
    val level4 = ShadowSpec(8f, 16f, 0.24f)  // 模态框
    // 拇指阴影: 滑块拇指使用
    val thumb = ShadowSpec(1f, 3f, 0.2f)
    val thumbActive = ShadowSpec(2f, 6f, 0.3f)
}

// ── Gradient Tokens ───────────────────────────────────────────────
// 2026 品牌渐变: 暖色系主色渐变 + 冷色系辅助渐变
// 用于滑块激活轨道、按钮、强调元素
object AlcedoGradient {
    // 主色青墨绿渐变: 青→亮青绿,用于主要交互元素
    val primaryWarm = listOf(Color(0xFF00D4AA), Color(0xFF4DFFCC))
    val primaryWarmVertical = listOf(Color(0xFF00D4AA), Color(0xFF009977))
    // 辅助冷渐变: 墨绿→青,用于示波器/信息元素
    val secondaryCool = listOf(Color(0xFF009977), Color(0xFF00D4AA))
    // 玻璃态高光: 白→透明,用于玻璃面板顶部
    val glassHighlight = listOf(Color(0x1AFFFFFF), Color(0x00FFFFFF))
    val glassHighlightVertical = listOf(Color(0x14FFFFFF), Color(0x00FFFFFF))
    // 暗色渐变: 用于面板底部过渡
    val panelFade = listOf(Color(0x00FFFFFF), Color(0x08FFFFFF))
}

// ── Glass Morphism Tokens ─────────────────────────────────────────
// 2026 玻璃态设计: 模糊+半透明+微边框
object AlcedoGlass {
    const val blurRadius = 24f        // 背景模糊强度(dp)
    const val panelOpacity = 0.85f    // 面板基础不透明度
    const val toolbarOpacity = 0.92f  // 工具栏不透明度
    const val borderAlpha = 0.08f     // 玻璃边框透明度
    const val highlightAlpha = 0.06f  // 玻璃高光透明度
}

// ── Motion Duration Tokens ────────────────────────────────────────
// 2026 动效哲学: 快进慢出,微交互 < 200ms, 面板切换 < 350ms
object AlcedoMotion {
    const val instant = 60     // 瞬时反馈 (颜色变化)
    const val micro = 120      // 微交互 (按钮按下、开关)
    const val quick = 200      // 快速过渡 (淡入淡出)
    const val smooth = 280     // 平滑过渡 (面板切换)
    const val elegant = 350    // 优雅过渡 (模态框)
    const val cinematic = 500  // 电影感 (启动画面)
}

// ── Animation Specs ───────────────────────────────────────────────
// 2026 摄影师偏好：快速响应 + 流畅过渡，不拖泥带水
// 参考 AlcedoEasing 系统，补充面板过渡动画规格
object AlcedoAnimation {
    // 面板切换：快速、果断
    val panelEnter = tween<Float>(
        durationMillis = 280,
        easing = AlcedoEasing.EmphasizedDecelerate
    )
    val panelExit = tween<Float>(
        durationMillis = 200,
        easing = AlcedoEasing.EmphasizedAccelerate
    )

    // 滑块值变化：即时但有微妙的弹性
    val sliderChange = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 卡片展开/折叠：流畅优雅
    val expandCollapse = tween<Float>(
        durationMillis = 300,
        easing = AlcedoEasing.Emphasized
    )

    // 开关切换：快速的阻尼弹簧
    val switchToggle = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // 内容淡入：柔和的透明度过渡
    val contentFadeIn = tween<Float>(
        durationMillis = 250,
        easing = AlcedoEasing.EmphasizedDecelerate
    )
    val contentFadeOut = tween<Float>(
        durationMillis = 150,
        easing = AlcedoEasing.EmphasizedAccelerate
    )

    // 示波器面板弹出
    val scopeSlideIn = tween<Float>(
        durationMillis = 320,
        easing = AlcedoEasing.EmphasizedDecelerate
    )
    val scopeSlideOut = tween<Float>(
        durationMillis = 240,
        easing = AlcedoEasing.EmphasizedAccelerate
    )

    // 长按对比：瞬时切换
    val compareOverlay = tween<Float>(
        durationMillis = 80,
        easing = LinearEasing
    )

    // 标签切换指示器：平滑滑动
    val tabIndicator = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // 复位按钮微动效：按下缩放
    val resetButtonPress = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // 颜色轮拖动：跟手无延迟
    val colorWheelDrag = tween<Float>(
        durationMillis = 16,  // 1 frame at 60fps
        easing = LinearEasing
    )

    // ── 2026 新增: 微交互动画 ───────────────────────────────────
    // 按钮按下缩放
    val buttonPressScale = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    // 滑块拇指弹入
    val sliderThumbPop = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // 数值闪烁 (修改时短暂高亮)
    val valueFlash = tween<Float>(
        durationMillis = 400,
        easing = AlcedoEasing.Emphasized
    )
    // 工具栏图标激活
    val toolbarIconActivate = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
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
    val trackHeightActive = 6.dp     // 激活轨道高度 (2026: 微扩张)
    val thumbRadius = 10.dp          // 拇指半径（适合手指操作）
    val thumbRadiusActive = 14.dp    // 激活时拇指半径
    val thumbShadowElevation = 3.dp  // 拇指阴影高度
    val labelSpacing = 8.dp          // 标签与滑块间距
    val valueLabelWidth = 48.dp      // 数值标签宽度
    val resetButtonSize = 28.dp      // 重置按钮尺寸
    val resetIconSize = 16.dp        // 重置图标尺寸
}