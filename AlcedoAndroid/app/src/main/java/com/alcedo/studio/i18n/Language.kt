package com.alcedo.studio.i18n

enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String
) {
    ENGLISH("en", "English", "English"),
    CHINESE_SIMPLIFIED("zh-CN", "Chinese Simplified", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "Chinese Traditional", "繁體中文"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    GERMAN("de", "German", "Deutsch"),
    FRENCH("fr", "French", "Français"),
    SPANISH("es", "Spanish", "Español");

    companion object {
        // 国内用户默认使用简体中文：未识别的 locale 统一回退到简体中文
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: CHINESE_SIMPLIFIED
        }

        fun fromSystemLocale(locale: java.util.Locale): Language {
            val lang = locale.language
            val country = locale.country
            return when {
                lang == "zh" && country == "TW" -> CHINESE_TRADITIONAL
                lang == "zh" -> CHINESE_SIMPLIFIED
                lang == "ja" -> JAPANESE
                lang == "ko" -> KOREAN
                lang == "de" -> GERMAN
                lang == "fr" -> FRENCH
                lang == "es" -> SPANISH
                lang == "en" -> ENGLISH
                // 其它未识别的语言（含空 locale）默认回退到简体中文，适合国内用户
                else -> CHINESE_SIMPLIFIED
            }
        }
    }
}
