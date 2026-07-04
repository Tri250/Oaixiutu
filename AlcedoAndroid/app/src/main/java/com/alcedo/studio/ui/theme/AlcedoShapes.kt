package com.alcedo.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════
// 2026 Flagship Shape System
// ═══════════════════════════════════════════════════════════════════
// Progressively larger radii create visual hierarchy:
//   xs  → tags, chips, inline badges
//   s   → buttons, text fields, small cards
//   m   → cards, panels, dialog content
//   l   → modal sheets, large cards
//   xl  → full-screen surfaces, hero containers
// ═══════════════════════════════════════════════════════════════════

val AlcedoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
