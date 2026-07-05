package com.alcedo.studio.ui.theme

import androidx.compose.ui.graphics.Color

data class AlcedoColorScheme(
    val bgDeep: Color,
    val bgBase: Color,
    val bgPanel: Color,
    val bgCanvas: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val scrim: Color,
    val accent: Color,
    val onAccent: Color,
    // Liquid Glass – frosted-glass tint colors
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color
)

// ═══════════════════════════════════════════════════════════════════
// Hasselblad Orange – Inspired by the iconic Hasselblad camera brand
// Warm burnt-orange primary, premium photographic feel, 2026 flagship
// ═══════════════════════════════════════════════════════════════════
val HasselbladDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF0E0906), bgBase = Color(0xFF18110C), bgPanel = Color(0xFF221A14), bgCanvas = Color(0xFF1C1510),
    primary = Color(0xFFFF7A3D), onPrimary = Color(0xFF3A0E00), primaryContainer = Color(0xFF5C2A10), onPrimaryContainer = Color(0xFFFFB999),
    secondary = Color(0xFFE0C8B8), onSecondary = Color(0xFF3E2E20), secondaryContainer = Color(0xFF574435), onSecondaryContainer = Color(0xFFFDE4D2),
    tertiary = Color(0xFFD4C8A0), onTertiary = Color(0xFF38300E), tertiaryContainer = Color(0xFF504622), onTertiaryContainer = Color(0xFFF0E2B8),
    surface = Color(0xFF18110C), onSurface = Color(0xFFF0E4DA), surfaceVariant = Color(0xFF524438), onSurfaceVariant = Color(0xFFD8C8B8),
    surfaceContainerLowest = Color(0xFF120C07), surfaceContainerLow = Color(0xFF201914), surfaceContainer = Color(0xFF251D16), surfaceContainerHigh = Color(0xFF302820), surfaceContainerHighest = Color(0xFF3B322A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA09286), outlineVariant = Color(0xFF524438),
    inverseSurface = Color(0xFFF0E4DA), inverseOnSurface = Color(0xFF302820), inversePrimary = Color(0xFF8B4E2C),
    scrim = Color(0xFF000000), accent = Color(0xFFFF6D2E), onAccent = Color(0xFF3A0E00),
    glassTint = Color(0x40FF7A3D), glassBorder = Color(0x33FFB999), glassHighlight = Color(0x1AFFFFFF)
)

val HasselbladLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFFFF3EB), bgBase = Color(0xFFFFF7F2), bgPanel = Color(0xFFF5EAE0), bgCanvas = Color(0xFFFFFAF6),
    primary = Color(0xFF8B4E2C), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFFFB999), onPrimaryContainer = Color(0xFF3A0E00),
    secondary = Color(0xFF735A48), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFFDE4D2), onSecondaryContainer = Color(0xFF2A1C10),
    tertiary = Color(0xFF5A4F28), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFF0E2B8), onTertiaryContainer = Color(0xFF171408),
    surface = Color(0xFFFFF7F2), onSurface = Color(0xFF1E1612), surfaceVariant = Color(0xFFF2E4D8), onSurfaceVariant = Color(0xFF524438),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDF0E6), surfaceContainer = Color(0xFFF7EBE0), surfaceContainerHigh = Color(0xFFF1E5DA), surfaceContainerHighest = Color(0xFFEBDFD4),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF857468), outlineVariant = Color(0xFFD8C8B8),
    inverseSurface = Color(0xFF302820), inverseOnSurface = Color(0xFFF0E4DA), inversePrimary = Color(0xFFFF7A3D),
    scrim = Color(0xFF000000), accent = Color(0xFFD84315), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x288B4E2C), glassBorder = Color(0x1AFF7A3D), glassHighlight = Color(0x33FFFFFF)
)

// Gold - Warm golden tones, luxurious feel
val GoldDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF0F0D08), bgBase = Color(0xFF1A1710), bgPanel = Color(0xFF252018), bgCanvas = Color(0xFF1E1B14),
    primary = Color(0xFFD4A843), onPrimary = Color(0xFF1A1500), primaryContainer = Color(0xFF3D3000), onPrimaryContainer = Color(0xFFF2D96A),
    secondary = Color(0xFFC9BFA0), onSecondary = Color(0xFF312B16), secondaryContainer = Color(0xFF48412B), onSecondaryContainer = Color(0xFFE6DABB),
    tertiary = Color(0xFFA4CFA0), onTertiary = Color(0xFF0E390F), tertiaryContainer = Color(0xFF265023), onTertiaryContainer = Color(0xFFBFEBBA),
    surface = Color(0xFF1A1710), onSurface = Color(0xFFE8E2D0), surfaceVariant = Color(0xFF4B4636), onSurfaceVariant = Color(0xFFCFC6AD),
    surfaceContainerLowest = Color(0xFF14120B), surfaceContainerLow = Color(0xFF221F17), surfaceContainer = Color(0xFF26231B), surfaceContainerHigh = Color(0xFF312D25), surfaceContainerHighest = Color(0xFF3C382F),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF99907A), outlineVariant = Color(0xFF4B4636),
    inverseSurface = Color(0xFFE8E2D0), inverseOnSurface = Color(0xFF312D25), inversePrimary = Color(0xFF594600),
    scrim = Color(0xFF000000), accent = Color(0xFFFFD54F), onAccent = Color(0xFF1A1500),
    glassTint = Color(0x40D4A843), glassBorder = Color(0x33F2D96A), glassHighlight = Color(0x1AFFFFFF)
)

val GoldLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFFFF8E1), bgBase = Color(0xFFFFF9EC), bgPanel = Color(0xFFF5EFE0), bgCanvas = Color(0xFFFFFBF0),
    primary = Color(0xFF594600), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFF2D96A), onPrimaryContainer = Color(0xFF1A1500),
    secondary = Color(0xFF665E40), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE6DABB), onSecondaryContainer = Color(0xFF21200A),
    tertiary = Color(0xFF3D6439), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFBFEBBA), onTertiaryContainer = Color(0xFF002202),
    surface = Color(0xFFFFF9EC), onSurface = Color(0xFF1C1B14), surfaceVariant = Color(0xFFE8E0CC), onSurfaceVariant = Color(0xFF4B4636),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F2E2), surfaceContainer = Color(0xFFF2ECD8), surfaceContainerHigh = Color(0xFFECE7D2), surfaceContainerHighest = Color(0xFFE6E1CC),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7D7664), outlineVariant = Color(0xFFD0C8B0),
    inverseSurface = Color(0xFF312D25), inverseOnSurface = Color(0xFFE8E2D0), inversePrimary = Color(0xFFD4A843),
    scrim = Color(0xFF000000), accent = Color(0xFFFF8F00), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x28594600), glassBorder = Color(0x1AD4A843), glassHighlight = Color(0x33FFFFFF)
)

// Wine - Deep burgundy/purple, rich and elegant
val WineDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF10080C), bgBase = Color(0xFF1A1016), bgPanel = Color(0xFF25181F), bgCanvas = Color(0xFF1E131A),
    primary = Color(0xFFE0B4C8), onPrimary = Color(0xFF44212F), primaryContainer = Color(0xFF5D3746), onPrimaryContainer = Color(0xFFFDD5E5),
    secondary = Color(0xFFD3BFC6), onSecondary = Color(0xFF38292E), secondaryContainer = Color(0xFF4F3F44), onSecondaryContainer = Color(0xFFF0DAE1),
    tertiary = Color(0xFFE4BF90), onTertiary = Color(0xFF422B0A), tertiaryContainer = Color(0xFF5C4120), onTertiaryContainer = Color(0xFFFFDDB2),
    surface = Color(0xFF1A1016), onSurface = Color(0xFFEEDDE3), surfaceVariant = Color(0xFF504248), onSurfaceVariant = Color(0xFFD4C2C8),
    surfaceContainerLowest = Color(0xFF140B10), surfaceContainerLow = Color(0xFF221820), surfaceContainer = Color(0xFF271D24), surfaceContainerHigh = Color(0xFF32272E), surfaceContainerHighest = Color(0xFF3D3239),
    error = Color(0xFFBFA1A1), onError = Color(0xFF450002), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9D8D93), outlineVariant = Color(0xFF504248),
    inverseSurface = Color(0xFFEEDDE3), inverseOnSurface = Color(0xFF32272E), inversePrimary = Color(0xFF764D5C),
    scrim = Color(0xFF000000), accent = Color(0xFFAD1457), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x40E0B4C8), glassBorder = Color(0x33FDD5E5), glassHighlight = Color(0x1AFFFFFF)
)

val WineLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFFFEBF0), bgBase = Color(0xFFFFF0F3), bgPanel = Color(0xFFF5E2E8), bgCanvas = Color(0xFFFFF5F7),
    primary = Color(0xFF764D5C), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFFDD5E5), onPrimaryContainer = Color(0xFF2E0A18),
    secondary = Color(0xFF665560), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFF0DAE1), onSecondaryContainer = Color(0xFF231520),
    tertiary = Color(0xFF6D5B3B), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFFFDDB2), onTertiaryContainer = Color(0xFF261800),
    surface = Color(0xFFFFF0F3), onSurface = Color(0xFF1F0B14), surfaceVariant = Color(0xFFF0DEE4), onSurfaceVariant = Color(0xFF504248),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDE8EE), surfaceContainer = Color(0xFFF7E2E8), surfaceContainerHigh = Color(0xFFF1DDE3), surfaceContainerHighest = Color(0xFFEBD7DD),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF827279), outlineVariant = Color(0xFFD4C2C8),
    inverseSurface = Color(0xFF32272E), inverseOnSurface = Color(0xFFEEDDE3), inversePrimary = Color(0xFFE0B4C8),
    scrim = Color(0xFF000000), accent = Color(0xFFAD1457), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x28764D5C), glassBorder = Color(0x1AE0B4C8), glassHighlight = Color(0x33FFFFFF)
)

// Steel - Cool blue-gray, professional and clean
val SteelDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF080C10), bgBase = Color(0xFF10141A), bgPanel = Color(0xFF181E26), bgCanvas = Color(0xFF141920),
    primary = Color(0xFFA8C8E8), onPrimary = Color(0xFF0F2E46), primaryContainer = Color(0xFF27445E), onPrimaryContainer = Color(0xFFCCE5FF),
    secondary = Color(0xFFB4C5D4), onSecondary = Color(0xFF1F2D3A), secondaryContainer = Color(0xFF364350), onSecondaryContainer = Color(0xFFD0E0F0),
    tertiary = Color(0xFFD4C4E0), onTertiary = Color(0xFF352E40), tertiaryContainer = Color(0xFF4D4458), onTertiaryContainer = Color(0xFFF0DFFC),
    surface = Color(0xFF10141A), onSurface = Color(0xFFE0E2E8), surfaceVariant = Color(0xFF42474F), onSurfaceVariant = Color(0xFFC2C7D0),
    surfaceContainerLowest = Color(0xFF0B0E14), surfaceContainerLow = Color(0xFF181D24), surfaceContainer = Color(0xFF1C2128), surfaceContainerHigh = Color(0xFF272C33), surfaceContainerHighest = Color(0xFF32363E),
    error = Color(0xFFBFA1A1), onError = Color(0xFF450002), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8C919A), outlineVariant = Color(0xFF42474F),
    inverseSurface = Color(0xFFE0E2E8), inverseOnSurface = Color(0xFF272C33), inversePrimary = Color(0xFF3E5C76),
    scrim = Color(0xFF000000), accent = Color(0xFF4FC3F7), onAccent = Color(0xFF002E42),
    glassTint = Color(0x40A8C8E8), glassBorder = Color(0x33CCE5FF), glassHighlight = Color(0x1AFFFFFF)
)

val SteelLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFE8EDF3), bgBase = Color(0xFFF0F3F8), bgPanel = Color(0xFFDCE3EC), bgCanvas = Color(0xFFF4F7FB),
    primary = Color(0xFF3E5C76), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFCCE5FF), onPrimaryContainer = Color(0xFF031B2E),
    secondary = Color(0xFF505D6B), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD0E0F0), onSecondaryContainer = Color(0xFF0C1B2B),
    tertiary = Color(0xFF65597A), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFF0DFFC), onTertiaryContainer = Color(0xFF1E1730),
    surface = Color(0xFFF0F3F8), onSurface = Color(0xFF11161C), surfaceVariant = Color(0xFFDDE3EC), onSurfaceVariant = Color(0xFF42474F),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7FAFF), surfaceContainer = Color(0xFFF1F4F9), surfaceContainerHigh = Color(0xFFEBEEF4), surfaceContainerHighest = Color(0xFFE5E8EE),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF72787F), outlineVariant = Color(0xFFC2C7D0),
    inverseSurface = Color(0xFF272C33), inverseOnSurface = Color(0xFFE0E2E8), inversePrimary = Color(0xFFA8C8E8),
    scrim = Color(0xFF000000), accent = Color(0xFF0288D1), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x283E5C76), glassBorder = Color(0x1AA8C8E8), glassHighlight = Color(0x33FFFFFF)
)

// Graphite - Dark charcoal, minimal and modern
val GraphiteDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF0A0A0A), bgBase = Color(0xFF141414), bgPanel = Color(0xFF1C1C1C), bgCanvas = Color(0xFF181818),
    primary = Color(0xFFB0C4B0), onPrimary = Color(0xFF1C351C), primaryContainer = Color(0xFF324B32), onPrimaryContainer = Color(0xFFCCE0CC),
    secondary = Color(0xFFB8B8B8), onSecondary = Color(0xFF242424), secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = Color(0xFFD4D4D4),
    tertiary = Color(0xFFA8B8D0), onTertiary = Color(0xFF10243A), tertiaryContainer = Color(0xFF283A50), onTertiaryContainer = Color(0xFFC4D4EC),
    surface = Color(0xFF141414), onSurface = Color(0xFFE4E4E4), surfaceVariant = Color(0xFF444444), onSurfaceVariant = Color(0xFFC4C4C4),
    surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF1C1C1C), surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF2A2A2A), surfaceContainerHighest = Color(0xFF353535),
    error = Color(0xFFCF6679), onError = Color(0xFF1A0002), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E8E8E), outlineVariant = Color(0xFF444444),
    inverseSurface = Color(0xFFE4E4E4), inverseOnSurface = Color(0xFF2A2A2A), inversePrimary = Color(0xFF4A634A),
    scrim = Color(0xFF000000), accent = Color(0xFF66BB6A), onAccent = Color(0xFF0A2E0A),
    glassTint = Color(0x40B0C4B0), glassBorder = Color(0x33CCE0CC), glassHighlight = Color(0x1AFFFFFF)
)

val GraphiteLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFF0F0F0), bgBase = Color(0xFFF5F5F5), bgPanel = Color(0xFFE8E8E8), bgCanvas = Color(0xFFF8F8F8),
    primary = Color(0xFF4A634A), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFCCE0CC), onPrimaryContainer = Color(0xFF082008),
    secondary = Color(0xFF555555), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD4D4D4), onSecondaryContainer = Color(0xFF181818),
    tertiary = Color(0xFF3E5470), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFC4D4EC), onTertiaryContainer = Color(0xFF061B2E),
    surface = Color(0xFFF5F5F5), onSurface = Color(0xFF141414), surfaceVariant = Color(0xFFDCDCDC), onSurfaceVariant = Color(0xFF444444),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F8F8), surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFECECEC), surfaceContainerHighest = Color(0xFFE6E6E6),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF747474), outlineVariant = Color(0xFFC4C4C4),
    inverseSurface = Color(0xFF2A2A2A), inverseOnSurface = Color(0xFFE4E4E4), inversePrimary = Color(0xFFB0C4B0),
    scrim = Color(0xFF000000), accent = Color(0xFF2E7D32), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x284A634A), glassBorder = Color(0x1AB0C4B0), glassHighlight = Color(0x33FFFFFF)
)

// Mist - Light silver-gray, soft and subtle
val MistDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF0E0F10), bgBase = Color(0xFF181A1C), bgPanel = Color(0xFF222426), bgCanvas = Color(0xFF1C1E20),
    primary = Color(0xFFC0C8D4), onPrimary = Color(0xFF1A2028), primaryContainer = Color(0xFF323840), onPrimaryContainer = Color(0xFFD8E0EC),
    secondary = Color(0xFFC4C4C8), onSecondary = Color(0xFF1E1E22), secondaryContainer = Color(0xFF38383C), onSecondaryContainer = Color(0xFFDCDCE0),
    tertiary = Color(0xFFC8C0D0), onTertiary = Color(0xFF241E2C), tertiaryContainer = Color(0xFF3E3848), onTertiaryContainer = Color(0xFFE4DCEC),
    surface = Color(0xFF181A1C), onSurface = Color(0xFFE8EAEC), surfaceVariant = Color(0xFF44484C), onSurfaceVariant = Color(0xFFC8CCD0),
    surfaceContainerLowest = Color(0xFF121416), surfaceContainerLow = Color(0xFF202224), surfaceContainer = Color(0xFF242628), surfaceContainerHigh = Color(0xFF2F3132), surfaceContainerHighest = Color(0xFF393B3D),
    error = Color(0xFFBFA1A1), onError = Color(0xFF450002), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF929699), outlineVariant = Color(0xFF44484C),
    inverseSurface = Color(0xFFE8EAEC), inverseOnSurface = Color(0xFF2F3132), inversePrimary = Color(0xFF4A5464),
    scrim = Color(0xFF000000), accent = Color(0xFF90A4AE), onAccent = Color(0xFF1A2028),
    glassTint = Color(0x40C0C8D4), glassBorder = Color(0x33D8E0EC), glassHighlight = Color(0x1AFFFFFF)
)

val MistLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFE8EAEC), bgBase = Color(0xFFF0F1F3), bgPanel = Color(0xFFDCDEE2), bgCanvas = Color(0xFFF4F5F7),
    primary = Color(0xFF4A5464), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD8E0EC), onPrimaryContainer = Color(0xFF081B2E),
    secondary = Color(0xFF585860), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFDCDCE0), onSecondaryContainer = Color(0xFF16161A),
    tertiary = Color(0xFF5A5468), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFE4DCEC), onTertiaryContainer = Color(0xFF1A1424),
    surface = Color(0xFFF0F1F3), onSurface = Color(0xFF181A1C), surfaceVariant = Color(0xFFDEE0E4), onSurfaceVariant = Color(0xFF44484C),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F8FA), surfaceContainer = Color(0xFFF1F2F4), surfaceContainerHigh = Color(0xFFEBECEE), surfaceContainerHighest = Color(0xFFE5E6E8),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74787C), outlineVariant = Color(0xFFC8CCD0),
    inverseSurface = Color(0xFF2F3132), inverseOnSurface = Color(0xFFE8EAEC), inversePrimary = Color(0xFFC0C8D4),
    scrim = Color(0xFF000000), accent = Color(0xFF607D8B), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x284A5464), glassBorder = Color(0x1AC0C8D4), glassHighlight = Color(0x33FFFFFF)
)

// ═══════════════════════════════════════════════════════════════════
// DeepSpace – Deep space black with white text, inspired by PixelCake
// Pure professional photography aesthetic, 2026 flagship redesign
// ═══════════════════════════════════════════════════════════════════
val DeepSpaceDarkColors = AlcedoColorScheme(
    bgDeep = Color(0xFF050507), bgBase = Color(0xFF0A0A0F), bgPanel = Color(0xFF111118), bgCanvas = Color(0xFF08080C),
    primary = Color(0xFFB0CFFF), onPrimary = Color(0xFF002569), primaryContainer = Color(0xFF1A2A4A), onPrimaryContainer = Color(0xFFB0CFFF),
    secondary = Color(0xFFA8B0C0), onSecondary = Color(0xFF1A1F28), secondaryContainer = Color(0xFF2A2F38), onSecondaryContainer = Color(0xFFD0D8E8),
    tertiary = Color(0xFFC8B8E0), onTertiary = Color(0xFF2A1A40), tertiaryContainer = Color(0xFF3A2A50), onTertiaryContainer = Color(0xFFE0D0F0),
    surface = Color(0xFF0A0A0F), onSurface = Color(0xFFF0F0F5), surfaceVariant = Color(0xFF1C1C24), onSurfaceVariant = Color(0xFFC0C0CC),
    surfaceContainerLowest = Color(0xFF050507), surfaceContainerLow = Color(0xFF0E0E14), surfaceContainer = Color(0xFF14141C), surfaceContainerHigh = Color(0xFF1C1C24), surfaceContainerHighest = Color(0xFF24242E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF60606C), outlineVariant = Color(0xFF2C2C36),
    inverseSurface = Color(0xFFF0F0F5), inverseOnSurface = Color(0xFF14141C), inversePrimary = Color(0xFF4A6FA5),
    scrim = Color(0xFF000000), accent = Color(0xFFB0CFFF), onAccent = Color(0xFF002569),
    glassTint = Color(0x30FFFFFF), glassBorder = Color(0x20FFFFFF), glassHighlight = Color(0x15FFFFFF)
)

val DeepSpaceLightColors = AlcedoColorScheme(
    bgDeep = Color(0xFFE8E8EC), bgBase = Color(0xFFF0F0F5), bgPanel = Color(0xFFE0E0E8), bgCanvas = Color(0xFFF4F4F8),
    primary = Color(0xFF4A6FA5), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFB0CFFF), onPrimaryContainer = Color(0xFF002569),
    secondary = Color(0xFF555B68), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD0D8E8), onSecondaryContainer = Color(0xFF181C24),
    tertiary = Color(0xFF655080), onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFE0D0F0), onTertiaryContainer = Color(0xFF261838),
    surface = Color(0xFFF0F0F5), onSurface = Color(0xFF14141C), surfaceVariant = Color(0xFFDCDCE4), onSurfaceVariant = Color(0xFF44444C),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F6FA), surfaceContainer = Color(0xFFF0F0F4), surfaceContainerHigh = Color(0xFFEAEAEF), surfaceContainerHighest = Color(0xFFE4E4EA),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF74747E), outlineVariant = Color(0xFFC4C4CC),
    inverseSurface = Color(0xFF24242E), inverseOnSurface = Color(0xFFF0F0F5), inversePrimary = Color(0xFFB0CFFF),
    scrim = Color(0xFF000000), accent = Color(0xFF4A6FA5), onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0x28FFFFFF), glassBorder = Color(0x1A000000), glassHighlight = Color(0x33FFFFFF)
)
