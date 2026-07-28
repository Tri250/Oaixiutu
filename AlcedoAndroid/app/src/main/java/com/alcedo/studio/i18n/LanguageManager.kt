package com.alcedo.studio.i18n

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alcedo.studio.util.ContextProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's language preference in a DataStore-backed preferences
 * file. The chosen [Language] drives the active [com.alcedo.studio.i18n.Strings]
 * table and the app's [android.content.res.Configuration] locale.
 *
 * Reads happen on the IO dispatcher via DataStore; the single source of truth
 * is [language] (a cold [Flow]) so the UI recomposes when the preference
 * changes.
 */
private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "alcedo_language")

class LanguageManager {

    private val dataStore: DataStore<Preferences>?
        get() = runCatching { ContextProvider.requireContext().languageDataStore }.getOrNull()

    /** Cold flow of the currently selected language (defaults to system locale). */
    val language: Flow<Language> = dataStore?.data?.map { prefs ->
        Language.fromTag(prefs[LANGUAGE_KEY])
    } ?: kotlinx.coroutines.flow.flowOf(Language.fromLocale(java.util.Locale.getDefault()))

    /** The latest cached language, or the system default before the first read. */
    var current: Language = Language.fromLocale(java.util.Locale.getDefault())
        private set

    /** Suspend setter that persists the new language. */
    suspend fun setLanguage(language: Language) {
        current = language
        dataStore?.edit { prefs -> prefs[LANGUAGE_KEY] = language.tag }
    }
}

private val LANGUAGE_KEY = stringPreferencesKey("language_tag")
