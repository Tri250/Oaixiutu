package com.alcedo.studio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Alcedo color palette. A dark, professional photography editor theme built on
 * deep blacks with an accent blue (#4A9EFF) and a warm amber (#FFB347).
 */
object AlcedoColors {
    // ---- Brand accents ----
    val AccentBlue = Color(0xFF4A9EFF)
    val AccentBluePressed = Color(0xFF6FB0FF)
    val AccentBlueMuted = Color(0xFF2E5F8A)
    val Amber = Color(0xFFFFB347)
    val AmberPressed = Color(0xFFFFC76B)
    val AmberMuted = Color(0xFF8A6028)
    val Danger = Color(0xFFFF5C5C)
    val Success = Color(0xFF4CD08C)
    val Warning = Color(0xFFFFD24C)

    // ---- Neutrals (true blacks with subtle blue tint) ----
    val PureBlack = Color(0xFF000000)
    val Obsidian = Color(0xFF0A0C10)
    val Charcoal = Color(0xFF111418)
    val Graphite = Color(0xFF181C22)
    val Slate = Color(0xFF20262E)
    val Pewter = Color(0xFF2B333D)
    val Steel = Color(0xFF3A434F)
    val Ash = Color(0xFF5A6571)

    // ---- Foreground ----
    val TextPrimary = Color(0xFFECEFF4)
    val TextSecondary = Color(0xFFB8C0CC)
    val TextTertiary = Color(0xFF7A838F)
    val TextDisabled = Color(0xFF4A525C)
    val TextOnAccent = Color(0xFF0A0C10)

    // ---- Surface variants ----
    val SurfaceBase = Obsidian
    val SurfaceRaised = Charcoal
    val SurfaceOverlay = Graphite
    val SurfaceElevated = Slate
    val SurfaceHover = Color(0xFF232A32)
    val SurfaceSelected = Color(0xFF1E3A5C)
    val SurfaceScrim = Color(0x99000000)

    // ---- Dividers / borders ----
    val Divider = Color(0xFF1E232A)
    val Outline = Color(0xFF2C333C)
    val OutlineFocused = AccentBlue

    // ---- Scope / channel colors ----
    val ChannelRed = Color(0xFFFF5C5C)
    val ChannelGreen = Color(0xFF4CD08C)
    val ChannelBlue = Color(0xFF4A9EFF)
    val LumaTrace = Color(0xFFECEFF4)
    val VectorscopeTrace = Color(0xFFB8C0CC)

    // ---- Rating / flags ----
    val StarOn = Amber
    val FlagPick = AccentBlue
    val FlagReject = Danger
}

/** Light palette retained for completeness; the editor is dark-first. */
object AlcedoColorsLight {
    val AccentBlue = Color(0xFF1E66D6)
    val Amber = Color(0xFFC97A12)
    val SurfaceBase = Color(0xFFF6F7F9)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF111418)
    val TextSecondary = Color(0xFF3A434F)
}
