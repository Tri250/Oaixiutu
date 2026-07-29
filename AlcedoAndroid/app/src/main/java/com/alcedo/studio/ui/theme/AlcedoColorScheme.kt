package com.alcedo.studio.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Alcedo color schemes inspired by RapidRAW's three-theme system.
 *
 * Dark theme:  Pure neutral blacks, white accent — the UI recedes, the image dominates.
 * Light theme: Warm surfaces with terracotta/copper accent — soft, professional.
 * Grey theme:  Mid-grey palette with light accent — balanced, neutral.
 */

// ============================================================================
// Dark Theme (RapidRAW Dark: rgb(24,24,24) base, white accent)
// ============================================================================
val AlcedoDarkColorScheme = darkColorScheme(
    primary = AlcedoColors.Accent,                    // White
    onPrimary = AlcedoColors.TextOnAccent,             // Black on white
    primaryContainer = AlcedoColors.AccentMuted,       // Muted white
    onPrimaryContainer = AlcedoColors.TextPrimary,
    secondary = AlcedoColors.WarmAccent,               // Warm terracotta
    onSecondary = AlcedoColors.TextOnAccent,
    secondaryContainer = AlcedoColors.WarmAccentMuted,
    onSecondaryContainer = AlcedoColors.TextPrimary,
    tertiary = AlcedoColors.Success,
    onTertiary = AlcedoColors.TextOnAccent,
    tertiaryContainer = Color(0xFF1E3D2C),
    onTertiaryContainer = AlcedoColors.TextPrimary,
    error = AlcedoColors.Danger,
    onError = AlcedoColors.TextPrimary,
    errorContainer = Color(0xFF5C1E1E),
    onErrorContainer = AlcedoColors.TextPrimary,
    background = AlcedoColors.BgPrimary,               // rgb(24,24,24)
    onBackground = AlcedoColors.TextPrimary,
    surface = AlcedoColors.BgSecondary,                // rgb(35,35,35)
    onSurface = AlcedoColors.TextPrimary,
    surfaceVariant = AlcedoColors.Surface,             // rgb(28,28,28)
    onSurfaceVariant = AlcedoColors.TextSecondary,
    surfaceTint = AlcedoColors.Accent,                 // White tint
    inverseSurface = AlcedoColors.TextPrimary,
    inverseOnSurface = AlcedoColors.BgPrimary,
    outline = AlcedoColors.Outline,                    // rgb(45,45,45)
    outlineVariant = AlcedoColors.Divider,
    scrim = AlcedoColors.SurfaceScrim,
)

// ============================================================================
// Light Theme (RapidRAW Light: rgb(245,245,245) base, terracotta accent)
// ============================================================================
val AlcedoLightColorScheme = lightColorScheme(
    primary = AlcedoColorsLight.Accent,                // Terracotta rgb(198,142,110)
    onPrimary = AlcedoColorsLight.TextOnAccent,        // White on terracotta
    primaryContainer = AlcedoColorsLight.AccentMuted,
    onPrimaryContainer = AlcedoColorsLight.TextPrimary,
    secondary = AlcedoColorsLight.WarmAccent,
    onSecondary = Color.White,
    background = AlcedoColorsLight.BgPrimary,          // rgb(245,245,245)
    onBackground = AlcedoColorsLight.TextPrimary,      // rgb(20,20,20)
    surface = AlcedoColorsLight.BgSecondary,           // rgb(255,255,255)
    onSurface = AlcedoColorsLight.TextPrimary,
    surfaceVariant = AlcedoColorsLight.Surface,        // rgb(241,241,241)
    onSurfaceVariant = AlcedoColorsLight.TextSecondary,// rgb(108,108,108)
    outline = AlcedoColorsLight.Outline,               // rgb(224,224,224)
    outlineVariant = AlcedoColorsLight.Divider,
)

// ============================================================================
// Grey Theme (RapidRAW Grey: rgb(112,112,112) base, light accent)
// ============================================================================
val AlcedoGreyColorScheme = darkColorScheme(
    primary = AlcedoColorsGrey.Accent,                 // rgb(220,220,220)
    onPrimary = AlcedoColorsGrey.TextOnAccent,          // Dark on light accent
    primaryContainer = AlcedoColorsGrey.AccentMuted,
    onPrimaryContainer = AlcedoColorsGrey.TextPrimary,
    secondary = AlcedoColorsGrey.WarmAccent,            // Terracotta
    onSecondary = AlcedoColorsGrey.TextOnAccent,
    secondaryContainer = AlcedoColorsGrey.WarmAccent,
    onSecondaryContainer = AlcedoColorsGrey.TextPrimary,
    tertiary = AlcedoColorsGrey.Success,
    onTertiary = AlcedoColorsGrey.TextOnAccent,
    error = AlcedoColorsGrey.Danger,
    onError = AlcedoColorsGrey.TextPrimary,
    background = AlcedoColorsGrey.BgPrimary,           // rgb(112,112,112)
    onBackground = AlcedoColorsGrey.TextPrimary,
    surface = AlcedoColorsGrey.BgSecondary,             // rgb(118,118,118)
    onSurface = AlcedoColorsGrey.TextPrimary,
    surfaceVariant = AlcedoColorsGrey.Surface,          // rgb(108,108,108)
    onSurfaceVariant = AlcedoColorsGrey.TextSecondary,
    surfaceTint = AlcedoColorsGrey.Accent,
    inverseSurface = AlcedoColorsGrey.TextPrimary,
    inverseOnSurface = AlcedoColorsGrey.BgPrimary,
    outline = AlcedoColorsGrey.Outline,                 // rgb(138,138,138)
    outlineVariant = AlcedoColorsGrey.Divider,
    scrim = AlcedoColors.SurfaceScrim,
)

/**
 * Editor-specific extended tokens that are not part of the M3 scheme but are needed
 * for scopes, panels and viewport chrome. Exposed as a small holder so composables
 * can read both the M3 scheme and these custom tokens together.
 */
data class AlcedoExtendedColors(
    val panelBackground: Color = AlcedoColors.Surface,
    val panelBackgroundRaised: Color = AlcedoColors.CardActive,
    val viewportBackground: Color = AlcedoColors.PureBlack,
    val inspectorBackground: Color = AlcedoColors.BgSecondary,
    val divider: Color = AlcedoColors.Divider,
    val channelRed: Color = AlcedoColors.ChannelRed,
    val channelGreen: Color = AlcedoColors.ChannelGreen,
    val channelBlue: Color = AlcedoColors.ChannelBlue,
    val lumaTrace: Color = AlcedoColors.LumaTrace,
    val vectorscopeTrace: Color = AlcedoColors.VectorscopeTrace,
    val starOn: Color = AlcedoColors.StarOn,
    val flagPick: Color = AlcedoColors.FlagPick,
    val flagReject: Color = AlcedoColors.FlagReject,
    val sliderTrackInactive: Color = AlcedoColors.SliderTrackInactive,
    val overlayScrim: Color = AlcedoColors.SurfaceScrim,
    val accent: Color = AlcedoColors.Accent,
    val warmAccent: Color = AlcedoColors.WarmAccent,
    val borderColor: Color = AlcedoColors.BorderColor,
    val hoverColor: Color = AlcedoColors.Accent,       // RapidRAW: hover = accent
)
