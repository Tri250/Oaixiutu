package com.alcedo.studio.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Custom Alcedo color tokens expressed as Material3 color schemes. The dark scheme is
 * the canonical editor theme: deep blacks with an accent blue (#4A9EFF) primary and a
 * warm amber (#FFB347) secondary used for ratings, warnings and highlights.
 */
val AlcedoDarkColorScheme = darkColorScheme(
    primary = AlcedoColors.AccentBlue,
    onPrimary = AlcedoColors.TextOnAccent,
    primaryContainer = AlcedoColors.AccentBlueMuted,
    onPrimaryContainer = AlcedoColors.TextPrimary,
    secondary = AlcedoColors.Amber,
    onSecondary = AlcedoColors.TextOnAccent,
    secondaryContainer = AlcedoColors.AmberMuted,
    onSecondaryContainer = AlcedoColors.TextPrimary,
    tertiary = AlcedoColors.Success,
    onTertiary = AlcedoColors.TextOnAccent,
    tertiaryContainer = Color(0xFF1E3D2C),
    onTertiaryContainer = AlcedoColors.TextPrimary,
    error = AlcedoColors.Danger,
    onError = AlcedoColors.TextPrimary,
    errorContainer = Color(0xFF5C1E1E),
    onErrorContainer = AlcedoColors.TextPrimary,
    background = AlcedoColors.SurfaceBase,
    onBackground = AlcedoColors.TextPrimary,
    surface = AlcedoColors.SurfaceRaised,
    onSurface = AlcedoColors.TextPrimary,
    surfaceVariant = AlcedoColors.SurfaceElevated,
    onSurfaceVariant = AlcedoColors.TextSecondary,
    surfaceTint = AlcedoColors.AccentBlue,
    inverseSurface = AlcedoColors.TextPrimary,
    inverseOnSurface = AlcedoColors.Obsidian,
    outline = AlcedoColors.Outline,
    outlineVariant = AlcedoColors.Divider,
    scrim = AlcedoColors.SurfaceScrim,
)

val AlcedoLightColorScheme = lightColorScheme(
    primary = AlcedoColorsLight.AccentBlue,
    onPrimary = Color.White,
    secondary = AlcedoColorsLight.Amber,
    onSecondary = Color.White,
    background = AlcedoColorsLight.SurfaceBase,
    onBackground = AlcedoColorsLight.TextPrimary,
    surface = AlcedoColorsLight.SurfaceRaised,
    onSurface = AlcedoColorsLight.TextPrimary,
    outline = Color(0xFFC4CAD2),
)

/**
 * Editor-specific extended tokens that are not part of the M3 scheme but are needed
 * for scopes, panels and viewport chrome. Exposed as a small holder so composables
 * can read both the M3 scheme and these custom tokens together.
 */
data class AlcedoExtendedColors(
    val panelBackground: Color = AlcedoColors.SurfaceOverlay,
    val panelBackgroundRaised: Color = AlcedoColors.SurfaceElevated,
    val viewportBackground: Color = AlcedoColors.PureBlack,
    val inspectorBackground: Color = AlcedoColors.Graphite,
    val divider: Color = AlcedoColors.Divider,
    val channelRed: Color = AlcedoColors.ChannelRed,
    val channelGreen: Color = AlcedoColors.ChannelGreen,
    val channelBlue: Color = AlcedoColors.ChannelBlue,
    val lumaTrace: Color = AlcedoColors.LumaTrace,
    val vectorscopeTrace: Color = AlcedoColors.VectorscopeTrace,
    val starOn: Color = AlcedoColors.StarOn,
    val flagPick: Color = AlcedoColors.FlagPick,
    val flagReject: Color = AlcedoColors.FlagReject,
    val sliderTrackInactive: Color = AlcedoColors.Ash.copy(alpha = AlphaTokens.trackInactive),
    val overlayScrim: Color = AlcedoColors.SurfaceScrim,
)
