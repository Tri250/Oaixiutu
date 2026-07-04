package com.alcedo.studio.i18n

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton that manages the current language, loads string resources,
 * and persists the language selection.
 */
object LanguageManager {
    private const val PREFS_NAME = "alcedo_language_prefs"
    private const val KEY_LANGUAGE_CODE = "language_code"
    private const val KEY_FOLLOW_SYSTEM = "follow_system"

    private val _currentLanguage = MutableStateFlow(Language.ENGLISH)
    val currentLanguage: StateFlow<Language> = _currentLanguage

    private val _followSystem = MutableStateFlow(true)
    val followSystem: StateFlow<Boolean> = _followSystem

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val followSystem = prefs?.getBoolean(KEY_FOLLOW_SYSTEM, true) ?: true
        val savedCode = prefs?.getString(KEY_LANGUAGE_CODE, null)

        _followSystem.value = followSystem

        if (followSystem) {
            val systemLang = Language.fromSystemLocale(context.resources.configuration.locales[0])
            _currentLanguage.value = systemLang
            Strings.update(getStringResources(systemLang))
        } else if (savedCode != null) {
            val lang = Language.fromCode(savedCode)
            _currentLanguage.value = lang
            Strings.update(getStringResources(lang))
        } else {
            val systemLang = Language.fromSystemLocale(context.resources.configuration.locales[0])
            _currentLanguage.value = systemLang
            Strings.update(getStringResources(systemLang))
        }
    }

    fun setLanguage(language: Language) {
        _followSystem.value = false
        _currentLanguage.value = language
        Strings.update(getStringResources(language))
        prefs?.edit()
            ?.putBoolean(KEY_FOLLOW_SYSTEM, false)
            ?.putString(KEY_LANGUAGE_CODE, language.code)
            ?.apply()
    }

    fun setFollowSystem(context: Context) {
        _followSystem.value = true
        val systemLang = Language.fromSystemLocale(context.resources.configuration.locales[0])
        _currentLanguage.value = systemLang
        Strings.update(getStringResources(systemLang))
        prefs?.edit()
            ?.putBoolean(KEY_FOLLOW_SYSTEM, true)
            ?.remove(KEY_LANGUAGE_CODE)
            ?.apply()
    }

    fun onSystemLocaleChanged(context: Context) {
        if (_followSystem.value) {
            val systemLang = Language.fromSystemLocale(context.resources.configuration.locales[0])
            _currentLanguage.value = systemLang
            Strings.update(getStringResources(systemLang))
        }
    }

    fun getStringResources(language: Language): StringResources {
        return when (language) {
            Language.ENGLISH -> EnglishStrings()
            Language.CHINESE_SIMPLIFIED -> ChineseSimplifiedStrings()
            Language.CHINESE_TRADITIONAL -> EnglishStrings() // fallback to English
            Language.JAPANESE -> EnglishStrings() // fallback to English
            Language.KOREAN -> EnglishStrings() // fallback to English
            Language.GERMAN -> EnglishStrings() // fallback to English
            Language.FRENCH -> EnglishStrings() // fallback to English
            Language.SPANISH -> EnglishStrings() // fallback to English
        }
    }
}
