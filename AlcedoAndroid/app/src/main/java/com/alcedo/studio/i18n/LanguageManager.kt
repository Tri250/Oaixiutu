package com.alcedo.studio.i18n

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

    // 国内用户默认使用简体中文
    private val _currentLanguage = MutableStateFlow(Language.CHINESE_SIMPLIFIED)
    val currentLanguage: StateFlow<Language> = _currentLanguage

    private val _followSystem = MutableStateFlow(true)
    val followSystem: StateFlow<Boolean> = _followSystem

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val followSystem = prefs?.getBoolean(KEY_FOLLOW_SYSTEM, true) ?: true
        val savedCode = prefs?.getString(KEY_LANGUAGE_CODE, null)

        _followSystem.value = followSystem

        val resolvedLanguage = when {
            // 用户显式选择过语言
            !followSystem && savedCode != null -> Language.fromCode(savedCode)
            // 跟随系统：识别系统 locale；未识别时回退到简体中文
            followSystem -> Language.fromSystemLocale(context.resources.configuration.locales[0])
            // 默认回退到简体中文
            else -> Language.CHINESE_SIMPLIFIED
        }

        _currentLanguage.value = resolvedLanguage
        Strings.update(getStringResources(resolvedLanguage))
        // 同步应用到 AppCompatDelegate，使系统级资源（应用名、通知等）使用对应语言
        applyLocaleToApp(resolvedLanguage.code)
    }

    fun setLanguage(language: Language) {
        _followSystem.value = false
        _currentLanguage.value = language
        Strings.update(getStringResources(language))
        prefs?.edit()
            ?.putBoolean(KEY_FOLLOW_SYSTEM, false)
            ?.putString(KEY_LANGUAGE_CODE, language.code)
            ?.apply()
        applyLocaleToApp(language.code)
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
        // 跟随系统：清空应用级 locale 覆盖，让系统决定资源语言
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    fun onSystemLocaleChanged(context: Context) {
        if (_followSystem.value) {
            val systemLang = Language.fromSystemLocale(context.resources.configuration.locales[0])
            _currentLanguage.value = systemLang
            Strings.update(getStringResources(systemLang))
        }
    }

    fun applyLocaleToApp(context: Context, languageCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    private fun applyLocaleToApp(languageCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getStringResources(language: Language): StringResources {
        return when (language) {
            Language.ENGLISH -> EnglishStrings()
            Language.CHINESE_SIMPLIFIED -> ChineseSimplifiedStrings()
            // 暂未提供独立翻译的语言回退到简体中文，适合国内用户使用
            Language.CHINESE_TRADITIONAL -> ChineseSimplifiedStrings()
            Language.JAPANESE -> ChineseSimplifiedStrings()
            Language.KOREAN -> ChineseSimplifiedStrings()
            Language.GERMAN -> ChineseSimplifiedStrings()
            Language.FRENCH -> ChineseSimplifiedStrings()
            Language.SPANISH -> ChineseSimplifiedStrings()
        }
    }
}
