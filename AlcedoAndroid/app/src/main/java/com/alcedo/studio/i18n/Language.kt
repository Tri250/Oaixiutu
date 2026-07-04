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
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: ENGLISH
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
                else -> ENGLISH
            }
        }
    }
}
