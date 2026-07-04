package com.alcedo.studio.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Composable function that returns the current localized string value.
 * Drop-in replacement for hardcoded strings, similar to `stringResource()`
 * but from our custom i18n system.
 *
 * Usage:
 *   Text(stringRes { navAlbum })
 *   Text(stringRes { albumImagesCount.format("12") })
 */
@Composable
fun stringRes(selector: StringResources.() -> String): String {
    // Collect language changes to trigger recomposition
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    // Access the string through the selector
    return Strings.current.selector()
}

/**
 * Direct access to current string resources for non-composable contexts.
 * Prefer stringRes {} in Composable functions.
 */
val strings: StringResources
    get() = Strings.current
