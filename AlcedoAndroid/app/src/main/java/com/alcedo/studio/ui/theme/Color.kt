package com.alcedo.studio.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════
// Alcedo Brand Tone Constants – 品牌色调常量
// ═══════════════════════════════════════════════════════════════════
// 参照 AlcedoStudio app_theme.hpp 的 tone* 属性
// 这些常量用于代码中直接引用品牌色调,不依赖主题切换
// 主题感知的颜色请使用 AlcedoColorScheme.toneGold 等
// ═══════════════════════════════════════════════════════════════════

object AlcedoBrandTone {
    // ── 五大品牌色调 ──────────────────────────────────────────────
    // Gold: 暖金色调,奢华质感,用于高端主题
    val gold = Color(0xFFD4A843)
    val goldDark = Color(0xFF594600)

    // Wine: 酒红/勃艮第色调,优雅深邃,用于创意主题
    val wine = Color(0xFFAD1457)
    val wineDark = Color(0xFF764D5C)

    // Steel: 冷钢蓝色调,专业理性,用于技术主题
    val steel = Color(0xFF4FC3F7)
    val steelDark = Color(0xFF3E5C76)

    // Graphite: 石墨绿色调,自然克制,用于极简主题
    val graphite = Color(0xFF66BB6A)
    val graphiteDark = Color(0xFF4A634A)

    // Mist: 雾银灰色调,柔和细腻,用于柔和主题
    val mist = Color(0xFF90A4AE)
    val mistDark = Color(0xFF4A5464)

    // ── 语义色调 ─────────────────────────────────────────────────
    // 这些色调在所有主题中保持一致,用于特定的语义场景
    val danger = Color(0xFFBA1A1A)         // 危险/错误色
    val dangerTint = Color(0x33BA1A1A)     // 危险色淡色版
    val success = Color(0xFF4CAF50)        // 成功/确认色
    val warning = Color(0xFFFF9800)        // 警告色
    val info = Color(0xFF4FC3F7)           // 信息色

    // ── 中性色 ───────────────────────────────────────────────────
    // 暗色主题下的中性色
    val neutralWhite = Color(0xFFFFFFFF)
    val neutral100 = Color(0xFFF5F5F5)
    val neutral200 = Color(0xFFE0E0E0)
    val neutral300 = Color(0xFFBDBDBD)
    val neutral400 = Color(0xFF9E9E9E)
    val neutral500 = Color(0xFF757575)
    val neutral600 = Color(0xFF616161)
    val neutral700 = Color(0xFF424242)
    val neutral800 = Color(0xFF303030)
    val neutral900 = Color(0xFF1A1A1A)
    val neutralBlack = Color(0xFF000000)
}

// ═══════════════════════════════════════════════════════════════════
// 主题颜色已迁移至 AlcedoColorScheme.kt，通过 ThemeManager 统一管理。
// 旧的硬编码颜色常量已全部移除（UX 修复：消除死代码，避免颜色来源不一致）。
// 新代码请使用 MaterialTheme.colorScheme 或 LocalAlcedoColorScheme()。
// 需要直接引用品牌色调的代码,请使用 AlcedoBrandTone 对象。
// ═══════════════════════════════════════════════════════════════════
