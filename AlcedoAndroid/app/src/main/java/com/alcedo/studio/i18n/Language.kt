package com.alcedo.studio.i18n

import java.util.Locale

/**
 * Supported UI languages. Each value maps to a [Locale] and a tag used by
 * [LanguageManager] to persist the user's choice.
 */
enum class Language(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh-CN", "简体中文");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** Resolve a persisted tag (or system default) to a supported language. */
        fun fromTag(tag: String?): Language {
            if (tag.isNullOrBlank()) {
                return fromLocale(Locale.getDefault())
            }
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH
        }

        /** Pick the best supported match for an arbitrary [locale]. */
        fun fromLocale(locale: Locale): Language {
            val lang = locale.language.lowercase()
            return when (lang) {
                "zh" -> CHINESE
                else -> ENGLISH
            }
        }
    }
}
