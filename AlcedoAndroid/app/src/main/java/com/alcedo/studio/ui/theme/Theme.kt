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
 * Alcedo theme. The app is dark-first; Material3 dynamic color is available on
 * Android 12+ when [dynamicColor] is true, otherwise the canonical Alcedo dark
 * scheme (deep blacks, accent blue #4A9EFF, amber #FFB347) is used.
 */
@Composable
fun AlcedoTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDark = darkTheme || isSystemInDarkTheme()

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> AlcedoDarkColorScheme
        else -> AlcedoLightColorScheme
    }

    val extended = AlcedoExtendedColors()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = AlcedoColors.PureBlack.toArgb()
                window.navigationBarColor = AlcedoColors.Obsidian.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
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
