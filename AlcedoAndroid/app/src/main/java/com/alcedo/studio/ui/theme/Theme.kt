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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════
// Composition Locals – 主题感知的全局访问点
// ═══════════════════════════════════════════════════════════════════

/**
 * 当前 AlcedoColorScheme 的 CompositionLocal。
 * 通过 LocalAlcedoColors.current 访问,自动响应主题切换。
 */
val LocalAlcedoColors = staticCompositionLocalOf { DeepSpaceDarkColors }

/**
 * 当前 AlcedoFontRoles 的 CompositionLocal。
 * 通过 LocalAlcedoFontRoles.current 访问字体角色系统。
 */
val LocalAlcedoFontRoles = staticCompositionLocalOf { AlcedoFontRoles }

// ═══════════════════════════════════════════════════════════════════
// Color Scheme Conversion – AlcedoColorScheme → Material3 ColorScheme
// ═══════════════════════════════════════════════════════════════════

fun AlcedoColorScheme.toMaterialColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = bgBase,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    scrim = scrim
)

fun AlcedoColorScheme.toMaterialLightColorScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = bgBase,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    inversePrimary = inversePrimary,
    scrim = scrim
)

// ═══════════════════════════════════════════════════════════════════
// Convenience Accessors – 便捷访问函数
// ═══════════════════════════════════════════════════════════════════

/**
 * Retrieve the current [AlcedoColorScheme] from the active theme variant.
 * 优先使用 LocalAlcedoColors.current,此函数保留向后兼容。
 */
@Composable
fun LocalAlcedoColorScheme(): AlcedoColorScheme = LocalAlcedoColors.current

/**
 * 获取当前主题下的指定字体角色 TextStyle。
 * 用法: val style = alcedoFontRole(AlcedoFontRole.UiTitle)
 */
@Composable
fun alcedoFontRole(role: AlcedoFontRole): TextStyle =
    LocalAlcedoFontRoles.current.resolve(role)

// ═══════════════════════════════════════════════════════════════════
// AlcedoTheme – 主主题 Composable
// ═══════════════════════════════════════════════════════════════════

@Composable
fun AlcedoTheme(
    content: @Composable () -> Unit
) {
    val variant by ThemeManager.themeVariant.collectAsStateWithLifecycle()
    val darkMode by ThemeManager.darkMode.collectAsStateWithLifecycle()

    val darkTheme = when (darkMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val isDynamic = variant == AlcedoThemeVariant.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val alcedoColors = ThemeManager.getAlcedoColorScheme(variant, darkTheme)

    val colorScheme = when {
        isDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (darkTheme) alcedoColors.toMaterialColorScheme()
            else alcedoColors.toMaterialLightColorScheme()
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Transparent status bar for edge-to-edge mode
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 通过 CompositionLocalProvider 向子树提供 Alcedo 令牌
    CompositionLocalProvider(
        LocalAlcedoColors provides alcedoColors,
        LocalAlcedoFontRoles provides AlcedoFontRoles
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlcedoTypography,
            shapes = AlcedoShapes,
            content = content
        )
    }
}
