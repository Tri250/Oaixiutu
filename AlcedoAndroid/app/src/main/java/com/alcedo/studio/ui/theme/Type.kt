package com.alcedo.studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// ── Mono 字体族: 用于代码、技术数值等场景 ───────────────────────
// 使用系统等宽字体,避免打包额外字体文件增加 APK 体积
val AlcedoMonoFontFamily = FontFamily.Monospace

// ═══════════════════════════════════════════════════════════════════
// 15-Level Font Role System – 参照 AlcedoStudio app_theme.hpp
// ═══════════════════════════════════════════════════════════════════
// 三个字体族: UI(OPPO Sans) / Data(OPPO Sans, tabular) / Mono(等宽)
// 每个角色对应明确的语义用途,确保跨面板/跨组件视觉一致性
// ═══════════════════════════════════════════════════════════════════

enum class AlcedoFontRole {
    // ── UI 家族: 面板标签、按钮、导航等界面文字 ───────────────
    UiBody,          // 14sp Normal – 面板正文、描述文字
    UiBodyStrong,    // 14sp Medium – 强调正文、重要标签
    UiCaption,       // 12sp Normal – 辅助说明、次要信息
    UiCaptionStrong, // 12sp Medium – 强调辅助说明
    UiTitle,         // 16sp Medium – 面板标题、分组标题
    UiHeadline,      // 20sp SemiBold – 大标题、对话框标题
    UiOverline,      // 11sp Medium – 分类标签、小节标题(大写)
    UiHint,          // 13sp Normal Italic – 提示文字、占位符

    // ── Data 家族: 数值显示、参数、技术数据 ───────────────────
    DataBody,        // 14sp Normal – 普通数值、参数标签
    DataBodyStrong,  // 14sp Medium – 强调数值
    DataCaption,     // 12sp Normal – 辅助数值、单位
    DataNumeric,     // 18sp SemiBold – 主要参数数值(曝光/色温等)
    DataOverlay,     // 24sp Bold – 叠加层数值(直方图/示波器)

    // ── Mono 家族: 代码、EXIF、技术文本 ───────────────────────
    MonoBody,        // 13sp Normal – EXIF信息、技术文本
    MonoCaption      // 11sp Normal – 小号代码、元数据标签
}

object AlcedoFontRoles {
    // ── UI 家族 ───────────────────────────────────────────────────
    val uiBody = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.6.sp,   // 1.4x – 中文正文呼吸感
        letterSpacing = 0.25.sp
    )

    val uiBodyStrong = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.6.sp,   // 1.4x
        letterSpacing = 0.1.sp
    )

    val uiCaption = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,     // 1.33x
        letterSpacing = 0.4.sp
    )

    val uiCaptionStrong = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,     // 1.33x
        letterSpacing = 0.25.sp
    )

    val uiTitle = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.6.sp,   // 1.35x – 面板标题行高,中文呼吸感
        letterSpacing = 0.15.sp
    )

    val uiHeadline = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,     // 1.3x
        letterSpacing = 0.sp
    )

    val uiOverline = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,     // 1.45x
        letterSpacing = 0.5.sp
    )

    val uiHint = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.2.sp,   // 1.4x
        letterSpacing = 0.25.sp
    )

    // ── Data 家族 ─────────────────────────────────────────────────
    // 使用 fontFeatureSettings = "tnum" 确保等宽数字,
    // 摄影编辑器中数值不会因字符宽度不同而跳动
    val dataBody = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.9.sp,   // 1.35x
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    val dataBodyStrong = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.9.sp,   // 1.35x
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    val dataCaption = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,     // 1.33x
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    val dataNumeric = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 21.6.sp,   // 1.2x – 紧凑行高,突出数值
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    val dataOverlay = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.8.sp,   // 1.2x
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center
    )

    // ── Mono 家族 ─────────────────────────────────────────────────
    val monoBody = TextStyle(
        fontFamily = AlcedoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.2.sp,   // 1.4x
        letterSpacing = 0.sp
    )

    val monoCaption = TextStyle(
        fontFamily = AlcedoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.6.sp,   // 1.33x
        letterSpacing = 0.sp
    )

    /** 按 FontRole 枚举获取对应 TextStyle */
    fun resolve(role: AlcedoFontRole): TextStyle = when (role) {
        AlcedoFontRole.UiBody          -> uiBody
        AlcedoFontRole.UiBodyStrong    -> uiBodyStrong
        AlcedoFontRole.UiCaption       -> uiCaption
        AlcedoFontRole.UiCaptionStrong -> uiCaptionStrong
        AlcedoFontRole.UiTitle         -> uiTitle
        AlcedoFontRole.UiHeadline      -> uiHeadline
        AlcedoFontRole.UiOverline      -> uiOverline
        AlcedoFontRole.UiHint          -> uiHint
        AlcedoFontRole.DataBody        -> dataBody
        AlcedoFontRole.DataBodyStrong  -> dataBodyStrong
        AlcedoFontRole.DataCaption     -> dataCaption
        AlcedoFontRole.DataNumeric     -> dataNumeric
        AlcedoFontRole.DataOverlay     -> dataOverlay
        AlcedoFontRole.MonoBody        -> monoBody
        AlcedoFontRole.MonoCaption     -> monoCaption
    }
}

// ═══════════════════════════════════════════════════════════════════
// Material 3 Typography – 映射到 Font Role System
// ═══════════════════════════════════════════════════════════════════
// 保持 Material3 Typography 向后兼容,同时内部映射到 15 级角色系统
// ═══════════════════════════════════════════════════════════════════

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

    // ── Headline → UiHeadline ──────────────────────────────────────
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
    headlineSmall = AlcedoFontRoles.uiHeadline,  // 20sp SemiBold → UiHeadline

    // ── Title → UiTitle ────────────────────────────────────────────
    // lineHeight = 1.35x fontSize – 面板标题行高,中文呼吸感更好
    titleLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,       // 1.36x – 中文标题呼吸感
        letterSpacing = 0.sp
    ),
    titleMedium = AlcedoFontRoles.uiTitle,  // 16sp Medium → UiTitle
    titleSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,       // 1.4x – 面板标题,中文更舒适
        letterSpacing = 0.1.sp
    ),

    // ── Body → UiBody / UiBodyStrong ───────────────────────────────
    // fontSize +1sp 改善中文可读性, lineHeight = 1.4x 优化中文行距呼吸感
    bodyLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,       // 1.4x – 优化中文行距
        letterSpacing = 0.5.sp
    ),
    bodyMedium = AlcedoFontRoles.uiBody,  // 14sp Normal → UiBody
    bodySmall = AlcedoFontRoles.uiCaption,  // 12sp Normal → UiCaption

    // ── Label → UiCaptionStrong / UiOverline ──────────────────────
    // lineHeight = 1.5x – 宽松标签行高，适配按钮与芯片
    // 国内摄影师暗色环境偏好: 标签字重稍粗,确保暗色下可读性
    labelLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,       // 1.5x
        letterSpacing = 0.1.sp
    ),
    labelMedium = AlcedoFontRoles.uiCaptionStrong,  // 12sp Medium → UiCaptionStrong
    labelSmall = AlcedoFontRoles.uiOverline  // 11sp Medium → UiOverline
)

// Keep legacy reference for compatibility
val Typography = AlcedoTypography
