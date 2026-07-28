package com.alcedo.studio.i18n

/**
 * Singleton accessor for the active [StringRes] table. Screens read UI strings
 * through [Strings.res] so the language can be switched at runtime by updating
 * [language]; the active table defaults to the system locale's best match
 * until the persisted preference is loaded.
 *
 * The [LanguageManager] updates the language once the DataStore preference is
 * read; until then composables fall back to the system default.
 *
 * Usage: `Strings.res.exposure`, `Strings.res.tabAlbum`.
 */
object Strings {

    @Volatile
    var language: Language = Language.fromLocale(java.util.Locale.getDefault())
        private set

    @Volatile
    private var table: StringRes = tableFor(language)

    /** The active string table. */
    val res: StringRes get() = table

    /**
     * Switch the active language. Called by the settings screen / app startup
     * once the persisted preference (or system default) is known.
     */
    fun setLanguage(newLanguage: Language) {
        language = newLanguage
        table = tableFor(newLanguage)
    }

    private fun tableFor(language: Language): StringRes = when (language) {
        Language.CHINESE -> StringResourcesZhCn
        Language.ENGLISH -> StringResourcesEn
    }
}
