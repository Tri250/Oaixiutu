package com.alcedo.studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alcedo.studio.R

// ═══════════════════════════════════════════════════════════════════
// 2026 Flagship Typography System – OPPO Sans + Noto Sans SC fallback
// ═══════════════════════════════════════════════════════════════════
// OPPO Sans:理性人文主义设计哲学,几何骨架+东方书法笔意
// 字面率86%-88%,高于思源黑体,文本块密度均衡、呼吸感强
// Noto Sans SC 作为兜底字体确保生僻字覆盖
// ═══════════════════════════════════════════════════════════════════

private val OppoSansFamily = FontFamily(
    Font(R.font.opposans_regular, FontWeight.Normal),
    Font(R.font.opposans_medium, FontWeight.Medium),
    Font(R.font.opposans_bold, FontWeight.Bold),
)

// 主字体:OPPO Sans 优先,系统 Sans 兜底(覆盖生僻字/特殊符号)
val AlcedoFontFamily = FontFamily(
    Font(R.font.opposans_regular, FontWeight.Normal),
    Font(R.font.opposans_medium, FontWeight.Medium),
    Font(R.font.opposans_bold, FontWeight.Bold),
)

val AlcedoTypography = Typography(
    // ── Display: Hero / splash-screen headlines ────────────────────
    displayLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        lineHeight = 72.sp,       // 1.26x – tight, cinematic
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 45.sp,
        lineHeight = 58.sp,       // 1.29x
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 48.sp,       // 1.33x
        letterSpacing = 0.sp
    ),

    // ── Headline: Section headers, top-level titles ─────────────────
    headlineLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 42.sp,       // 1.31x
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 38.sp,       // 1.36x
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 34.sp,       // 1.42x
        letterSpacing = 0.sp
    ),

    // ── Title: Card titles, panel headers ───────────────────────────
    // lineHeight = 1.35x fontSize – 面板标题行高,中文呼吸感更好
    titleLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,       // 1.36x – 中文标题呼吸感
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,       // 1.33x
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,       // 1.4x – 面板标题,中文更舒适
        letterSpacing = 0.1.sp
    ),

    // ── Body: Primary reading content ───────────────────────────────
    // fontSize +1sp 改善中文可读性, lineHeight = 1.4x 优化中文行距呼吸感
    bodyLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,       // 1.4x – 优化中文行距
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,       // 1.4x
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,       // 1.4x
        letterSpacing = 0.4.sp
    ),

    // ── Label: Buttons, tags, chips, navigation items ───────────────
    // lineHeight = 1.5x – 宽松标签行高，适配按钮与芯片
    // 国内摄影师暗色环境偏好: 标签字重稍粗,确保暗色下可读性
    labelLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,       // 1.5x
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,       // 1.5x
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 17.sp,       // 1.5x
        letterSpacing = 0.5.sp
    )
)

// Keep legacy reference for compatibility
val Typography = AlcedoTypography
