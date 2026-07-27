package com.alcedo.studio.i18n

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "LanguageManager"

object LanguageManager {
    private const val PREFS_NAME = "alcedo_language_prefs"
    private const val KEY_LANGUAGE_CODE = "language_code"
    private const val KEY_FOLLOW_SYSTEM = "follow_system"

    private val _currentLanguage = MutableStateFlow(Language.CHINESE_SIMPLIFIED)
    val currentLanguage: StateFlow<Language> = _currentLanguage

    private val _followSystem = MutableStateFlow(true)
    val followSystem: StateFlow<Boolean> = _followSystem

    private var prefs: SharedPreferences? = null
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val followSystem = prefs?.getBoolean(KEY_FOLLOW_SYSTEM, true) ?: true
                val savedCode = prefs?.getString(KEY_LANGUAGE_CODE, null)

                _followSystem.value = followSystem

                val resolvedLanguage = when {
                    !followSystem && savedCode != null -> Language.fromCode(savedCode)
                    followSystem -> Language.fromSystemLocale(getSystemLocale(context))
                    else -> Language.CHINESE_SIMPLIFIED
                }

                _currentLanguage.value = resolvedLanguage
                runCatching { Strings.update(getStringResources(resolvedLanguage)) }
                    .onFailure { Log.e(TAG, "Failed to update string resources", it) }
                runCatching { applyLocaleToApp(resolvedLanguage.code) }
                    .onFailure { Log.e(TAG, "Failed to apply locale", it) }
                initialized = true
            } catch (e: Throwable) {
                Log.e(TAG, "LanguageManager initialization failed, using defaults", e)
                _currentLanguage.value = Language.CHINESE_SIMPLIFIED
                _followSystem.value = true
                initialized = true
            }
        }
    }

    fun setLanguage(language: Language) {
        if (!initialized) {
            Log.w(TAG, "setLanguage called before initialize, setting default")
            _currentLanguage.value = language
            return
        }
        _followSystem.value = false
        _currentLanguage.value = language
        runCatching { Strings.update(getStringResources(language)) }
        runCatching {
            prefs?.edit()
                ?.putBoolean(KEY_FOLLOW_SYSTEM, false)
                ?.putString(KEY_LANGUAGE_CODE, language.code)
                ?.apply()
        }
        runCatching { applyLocaleToApp(language.code) }
    }

    fun setFollowSystem(context: Context) {
        if (!initialized) return
        _followSystem.value = true
        val systemLang = Language.fromSystemLocale(getSystemLocale(context))
        _currentLanguage.value = systemLang
        runCatching { Strings.update(getStringResources(systemLang)) }
        runCatching {
            prefs?.edit()
                ?.putBoolean(KEY_FOLLOW_SYSTEM, true)
                ?.remove(KEY_LANGUAGE_CODE)
                ?.apply()
        }
        runCatching {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    fun onSystemLocaleChanged(context: Context) {
        if (!initialized) return
        if (_followSystem.value) {
            val systemLang = Language.fromSystemLocale(getSystemLocale(context))
            _currentLanguage.value = systemLang
            runCatching { Strings.update(getStringResources(systemLang)) }
        }
    }

    private fun getSystemLocale(context: Context): java.util.Locale {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get system locale, fallback to default", e)
            java.util.Locale.CHINA
        }
    }

    private fun applyLocaleToApp(languageCode: String) {
        try {
            val appLocale = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to apply locale to app", e)
        }
    }

    fun getStringResources(language: Language): StringResources {
        return when (language) {
            Language.ENGLISH -> runCatching { EnglishStrings() }
                .getOrElse { ChineseSimplifiedStrings() }
            Language.CHINESE_SIMPLIFIED -> ChineseSimplifiedStrings()
            Language.CHINESE_TRADITIONAL,
            Language.JAPANESE,
            Language.KOREAN,
            Language.GERMAN,
            Language.FRENCH,
            Language.SPANISH -> ChineseSimplifiedStrings()
        }
    }
}
