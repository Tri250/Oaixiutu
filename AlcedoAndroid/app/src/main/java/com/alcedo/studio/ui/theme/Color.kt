package com.alcedo.studio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Alcedo color palette. Inspired by RapidRAW's minimal, photography-first design:
 * pure neutral darks (no blue tint), white as the primary accent in dark mode,
 * warm terracotta/copper as the secondary accent, and a full grey theme.
 *
 * Design philosophy: the UI should recede, letting the image dominate.
 * Accents are reserved for interactive elements and subtle highlights.
 */

// ============================================================================
// Dark Theme (RapidRAW-inspired: pure neutral blacks, white accent)
// ============================================================================
object AlcedoColors {
    // ---- Primary accent: white (RapidRAW Dark) ----
    val Accent = Color(0xFFFFFFFF)              // White accent for dark theme
    val AccentPressed = Color(0xFFE0E0E0)       // Slightly off-white for pressed state
    val AccentMuted = Color(0xFFBDBDBD)         // Muted accent for inactive elements

    // ---- Secondary accent: warm terracotta/copper (RapidRAW Light) ----
    val WarmAccent = Color(0xFFC68E6E)          // rgb(198,142,110) - RapidRAW Light accent
    val WarmAccentPressed = Color(0xFFD4A48C)   // Lighter pressed state
    val WarmAccentMuted = Color(0xFF8A6248)     // Muted warm accent

    // ---- Semantic colors ----
    val Danger = Color(0xFFFF5C5C)
    val Success = Color(0xFF4CD08C)
    val Warning = Color(0xFFFFD24C)
    val Info = Color(0xFF60A5FA)                // Blue-400 equivalent

    // ---- Neutrals: pure neutral blacks (no blue tint) ----
    // RapidRAW uses rgb(24,24,24), rgb(35,35,35), rgb(28,28,28), rgb(43,43,43)
    val PureBlack = Color(0xFF000000)
    val BgPrimary = Color(0xFF181818)           // rgb(24,24,24) → 0x18 for dark base
    val BgSecondary = Color(0xFF232323)         // rgb(35,35,35) → raised surface
    val Surface = Color(0xFF1C1C1C)             // rgb(28,28,28) → card/panel surface
    val CardActive = Color(0xFF2B2B2B)          // rgb(43,43,43) → active card state
    val SurfaceHover = Color(0xFF333333)        // Slightly lighter hover
    val SurfaceSelected = Color(0xFF3A3A3A)     // Selected state
    val SurfaceScrim = Color(0x99000000)        // Overlay scrim

    // ---- Foreground (RapidRAW dark text tokens) ----
    val TextPrimary = Color(0xFFE8EAED)         // rgb(232,234,237)
    val TextSecondary = Color(0xFF9E9E9E)       // rgb(158,158,158)
    val TextTertiary = Color(0xFF757575)        // Between secondary and disabled
    val TextDisabled = Color(0xFF4A4A4A)
    val TextOnAccent = Color(0xFF000000)        // Black text on white accent

    // ---- Dividers / borders ----
    val BorderColor = Color(0xFF2D2D2D)         // rgb(45,45,45) - RapidRAW border
    val Divider = Color(0xFF2D2D2D)             // Same as border - subtle
    val Outline = Color(0xFF2D2D2D)
    val OutlineFocused = Color(0xFFFFFFFF)       // White focus ring

    // ---- Scope / channel colors ----
    val ChannelRed = Color(0xFFFF5C5C)
    val ChannelGreen = Color(0xFF4CD08C)
    val ChannelBlue = Color(0xFF60A5FA)
    val LumaTrace = Color(0xFFE8EAED)
    val VectorscopeTrace = Color(0xFF9E9E9E)

    // ---- Rating / flags ----
    val StarOn = Color(0xFFFFD24C)              // Warning yellow for stars
    val FlagPick = Color(0xFFFFFFFF)             // White accent for pick
    val FlagReject = Danger

    // ---- Slider track ----
    val SliderTrackInactive = Color(0xFF3A3A3A) // Visible but subdued inactive track

    // ---- Backward-compatible aliases (old names → new RapidRAW-inspired values) ----
    // These ensure existing code compiles while adopting the new design language.
    val AccentBlue = Accent                       // White accent (was blue)
    val AccentBluePressed = AccentPressed         // Off-white pressed (was light blue)
    val AccentBlueMuted = AccentMuted             // Muted white (was muted blue)
    val Amber = WarmAccent                        // Terracotta (was amber #FFB347)
    val AmberPressed = WarmAccentPressed          // Lighter terracotta (was light amber)
    val AmberMuted = WarmAccentMuted              // Muted terracotta (was muted amber)
    val Obsidian = BgPrimary                      // rgb(24,24,24) base (was blue-tinted)
    val Charcoal = BgSecondary                    // rgb(35,35,35) raised (was blue-tinted)
    val Graphite = Surface                        // rgb(28,28,28) surface (was blue-tinted)
    val Slate = CardActive                        // rgb(43,43,43) active (was blue-tinted)
    val Pewter = SurfaceHover                     // Hover state (was blue-tinted)
    val Steel = SurfaceSelected                   // Selected state (was blue-tinted)
    val Ash = Color(0xFF5A5A5A)                  // Neutral mid-grey (was blue-tinted)
    val SurfaceBase = BgPrimary                   // Alias: base surface
    val SurfaceRaised = BgSecondary               // Alias: raised surface
    val SurfaceOverlay = Surface                  // Alias: overlay surface
    val SurfaceElevated = CardActive              // Alias: elevated surface
}

// ============================================================================
// Light Theme (RapidRAW-inspired: warm surfaces, terracotta accent)
// ============================================================================
object AlcedoColorsLight {
    // RapidRAW Light: rgb(245,245,245) bg, rgb(198,142,110) accent
    val Accent = Color(0xFFC68E6E)              // rgb(198,142,110) - warm terracotta
    val AccentPressed = Color(0xFFB37A58)       // Darker pressed state
    val AccentMuted = Color(0xFF8A6248)         // Muted warm accent

    val WarmAccent = Color(0xFFC68E6E)
    val Danger = Color(0xFFD32F2F)
    val Success = Color(0xFF2E7D32)

    // RapidRAW Light surfaces
    val BgPrimary = Color(0xFFF5F5F5)           // rgb(245,245,245)
    val BgSecondary = Color(0xFFFFFFFF)          // rgb(255,255,255)
    val Surface = Color(0xFFF1F1F1)              // rgb(241,241,241)
    val CardActive = Color(0xFFFAFAFA)           // rgb(250,250,250)
    val SurfaceHover = Color(0xFFE8E8E8)

    val TextPrimary = Color(0xFF141414)           // rgb(20,20,20)
    val TextSecondary = Color(0xFF6C6C6C)        // rgb(108,108,108)
    val TextTertiary = Color(0xFF9E9E9E)
    val TextOnAccent = Color(0xFFFFFFFF)          // White text on terracotta

    val BorderColor = Color(0xFFE0E0E0)           // rgb(224,224,224)
    val Divider = Color(0xFFE0E0E0)
    val Outline = Color(0xFFE0E0E0)

    val SliderTrackInactive = Color(0xFFD0D0D0)

    // ---- Backward-compatible aliases ----
    val AccentBlue = Accent                       // Terracotta in light theme
    val SurfaceBase = BgPrimary                   // Alias: base surface
    val SurfaceRaised = BgSecondary               // Alias: raised surface
}

// ============================================================================
// Grey Theme (RapidRAW-inspired: mid-grey palette, light accent)
// ============================================================================
object AlcedoColorsGrey {
    // RapidRAW Grey: rgb(112,112,112) bg, rgb(220,220,220) accent
    val Accent = Color(0xFFDCDCDC)              // rgb(220,220,220) - light grey accent
    val AccentPressed = Color(0xFFC8C8C8)       // Darker pressed state
    val AccentMuted = Color(0xFF999999)         // Muted accent

    val WarmAccent = Color(0xFFC68E6E)          // Warm accent still available
    val Danger = Color(0xFFFF5C5C)
    val Success = Color(0xFF4CD08C)

    // RapidRAW Grey surfaces
    val BgPrimary = Color(0xFF707070)           // rgb(112,112,112)
    val BgSecondary = Color(0xFF767676)          // rgb(118,118,118)
    val Surface = Color(0xFF6C6C6C)              // rgb(108,108,108)
    val CardActive = Color(0xFF858585)           // rgb(133,133,133)
    val SurfaceHover = Color(0xFF8A8A8A)

    val TextPrimary = Color(0xFFF0F0F0)           // rgb(240,240,240)
    val TextSecondary = Color(0xFFB4B4B4)        // rgb(180,180,180)
    val TextTertiary = Color(0xFF999999)
    val TextOnAccent = Color(0xFF2D2D2D)          // Dark text on light accent

    val BorderColor = Color(0xFF8A8A8A)           // rgb(138,138,138)
    val Divider = Color(0xFF8A8A8A)
    val Outline = Color(0xFF8A8A8A)

    val SliderTrackInactive = Color(0xFF666666)
}
