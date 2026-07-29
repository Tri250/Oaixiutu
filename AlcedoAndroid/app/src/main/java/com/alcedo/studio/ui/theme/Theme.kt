package com.alcedo.studio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Alcedo theme mode. Inspired by RapidRAW's three-theme system:
 * Dark, Light, and Grey — each with its own color personality.
 */
enum class AlcedoThemeMode {
    Dark,   // RapidRAW Dark: pure neutral blacks, white accent
    Light,  // RapidRAW Light: warm surfaces, terracotta accent
    Grey,   // RapidRAW Grey: mid-grey palette, light accent
}

/**
 * Alcedo theme. Supports three RapidRAW-inspired themes:
 * - Dark:  Pure neutral blacks, white accent — photography-first, UI recedes
 * - Light: Warm surfaces with terracotta/copper accent — soft, professional
 * - Grey:  Mid-grey palette with light accent — balanced, neutral
 *
 * Material3 dynamic color is available on Android 12+ when [dynamicColor] is true,
 * otherwise the canonical Alcedo scheme for the selected theme mode is used.
 */
@Composable
fun AlcedoTheme(
    themeMode: AlcedoThemeMode = AlcedoThemeMode.Dark,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            when (themeMode) {
                AlcedoThemeMode.Light -> dynamicLightColorScheme(context)
                else -> dynamicDarkColorScheme(context)
            }
        }
        themeMode == AlcedoThemeMode.Light -> AlcedoLightColorScheme
        themeMode == AlcedoThemeMode.Grey -> AlcedoGreyColorScheme
        else -> AlcedoDarkColorScheme
    }

    val extended = when (themeMode) {
        AlcedoThemeMode.Dark -> AlcedoExtendedColors(
            panelBackground = AlcedoColors.Surface,
            panelBackgroundRaised = AlcedoColors.CardActive,
            viewportBackground = AlcedoColors.PureBlack,
            inspectorBackground = AlcedoColors.BgSecondary,
            divider = AlcedoColors.Divider,
            accent = AlcedoColors.Accent,
            warmAccent = AlcedoColors.WarmAccent,
            borderColor = AlcedoColors.BorderColor,
            hoverColor = AlcedoColors.Accent,
            sliderTrackInactive = AlcedoColors.SliderTrackInactive,
        )
        AlcedoThemeMode.Light -> AlcedoExtendedColors(
            panelBackground = AlcedoColorsLight.Surface,
            panelBackgroundRaised = AlcedoColorsLight.CardActive,
            viewportBackground = AlcedoColorsLight.BgPrimary,
            inspectorBackground = AlcedoColorsLight.BgSecondary,
            divider = AlcedoColorsLight.Divider,
            accent = AlcedoColorsLight.Accent,
            warmAccent = AlcedoColorsLight.WarmAccent,
            borderColor = AlcedoColorsLight.BorderColor,
            hoverColor = AlcedoColorsLight.Accent,
            sliderTrackInactive = AlcedoColorsLight.SliderTrackInactive,
        )
        AlcedoThemeMode.Grey -> AlcedoExtendedColors(
            panelBackground = AlcedoColorsGrey.Surface,
            panelBackgroundRaised = AlcedoColorsGrey.CardActive,
            viewportBackground = AlcedoColorsGrey.BgPrimary,
            inspectorBackground = AlcedoColorsGrey.BgSecondary,
            divider = AlcedoColorsGrey.Divider,
            accent = AlcedoColorsGrey.Accent,
            warmAccent = AlcedoColorsGrey.WarmAccent,
            borderColor = AlcedoColorsGrey.BorderColor,
            hoverColor = AlcedoColorsGrey.Accent,
            sliderTrackInactive = AlcedoColorsGrey.SliderTrackInactive,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                // Set status/nav bar colors based on theme
                val statusBarColor = when (themeMode) {
                    AlcedoThemeMode.Dark -> AlcedoColors.PureBlack.toArgb()
                    AlcedoThemeMode.Light -> AlcedoColorsLight.BgPrimary.toArgb()
                    AlcedoThemeMode.Grey -> AlcedoColorsGrey.BgPrimary.toArgb()
                }
                val navBarColor = when (themeMode) {
                    AlcedoThemeMode.Dark -> AlcedoColors.BgPrimary.toArgb()
                    AlcedoThemeMode.Light -> AlcedoColorsLight.BgPrimary.toArgb()
                    AlcedoThemeMode.Grey -> AlcedoColorsGrey.BgPrimary.toArgb()
                }
                window.statusBarColor = statusBarColor
                window.navigationBarColor = navBarColor
                val isLight = themeMode == AlcedoThemeMode.Light
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    CompositionLocalProvider(LocalAlcedoExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlcedoTypography,
            shapes = AlcedoShapes,
            content = content,
        )
    }
}

/** CompositionLocal exposing the extended (non-M3) editor color tokens. */
val LocalAlcedoExtendedColors = staticCompositionLocalOf { AlcedoExtendedColors() }

/** Convenience accessor for the extended color tokens. */
object AlcedoTheme {
    val extendedColors: AlcedoExtendedColors
        @Composable get() = LocalAlcedoExtendedColors.current
}
