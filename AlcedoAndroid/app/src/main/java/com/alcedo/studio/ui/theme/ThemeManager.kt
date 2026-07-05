package com.alcedo.studio.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeManager {
    private const val PREFS_NAME = "alcedo_theme_prefs"
    private const val KEY_THEME_VARIANT = "theme_variant"
    private const val KEY_DARK_MODE = "dark_mode" // "system", "light", "dark"

    private val _themeVariant = MutableStateFlow(AlcedoThemeVariant.DEEP_SPACE)
    val themeVariant: StateFlow<AlcedoThemeVariant> = _themeVariant

    private val _darkMode = MutableStateFlow("dark")
    val darkMode: StateFlow<String> = _darkMode

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVariant = prefs?.getString(KEY_THEME_VARIANT, AlcedoThemeVariant.DEEP_SPACE.name)
            ?: AlcedoThemeVariant.DEEP_SPACE.name
        _themeVariant.value = AlcedoThemeVariant.fromName(savedVariant)
        _darkMode.value = prefs?.getString(KEY_DARK_MODE, "dark") ?: "dark"
    }

    fun setThemeVariant(variant: AlcedoThemeVariant) {
        _themeVariant.value = variant
        prefs?.edit()?.putString(KEY_THEME_VARIANT, variant.name)?.apply()
    }

    fun setDarkMode(mode: String) {
        _darkMode.value = mode
        prefs?.edit()?.putString(KEY_DARK_MODE, mode)?.apply()
    }

    fun getAlcedoColorScheme(variant: AlcedoThemeVariant, darkTheme: Boolean): AlcedoColorScheme {
        return when (variant) {
            AlcedoThemeVariant.DEEP_SPACE -> if (darkTheme) DeepSpaceDarkColors else DeepSpaceLightColors
            AlcedoThemeVariant.HASSELBLAD -> if (darkTheme) HasselbladDarkColors else HasselbladLightColors
            AlcedoThemeVariant.GOLD -> if (darkTheme) GoldDarkColors else GoldLightColors
            AlcedoThemeVariant.WINE -> if (darkTheme) WineDarkColors else WineLightColors
            AlcedoThemeVariant.STEEL -> if (darkTheme) SteelDarkColors else SteelLightColors
            AlcedoThemeVariant.GRAPHITE -> if (darkTheme) GraphiteDarkColors else GraphiteLightColors
            AlcedoThemeVariant.MIST -> if (darkTheme) MistDarkColors else MistLightColors
            AlcedoThemeVariant.DYNAMIC -> if (darkTheme) DeepSpaceDarkColors else DeepSpaceLightColors
        }
    }
}
