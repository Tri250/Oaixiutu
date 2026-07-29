package com.alcedo.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Alcedo shape definitions. Inspired by RapidRAW's generous corner radii:
 * --radius-md: 8px, --radius-lg: 15px — soft, modern feel for a professional editor.
 */
val AlcedoShapes = Shapes(
    extraSmall = RoundedCornerShape(DesignTokens.radiusXs),
    small = RoundedCornerShape(DesignTokens.radiusSm),
    medium = RoundedCornerShape(DesignTokens.radiusMd),
    large = RoundedCornerShape(DesignTokens.radiusLg),   // 15dp (RapidRAW)
    extraLarge = RoundedCornerShape(DesignTokens.radiusXl),
)

/** Panel container shape with a flat top and generous bottom radius. */
val PanelContainerShape = RoundedCornerShape(
    topStart = DesignTokens.radiusLg,     // 15dp (RapidRAW)
    topEnd = DesignTokens.radiusLg,
    bottomEnd = DesignTokens.radiusMd,
    bottomStart = DesignTokens.radiusMd,
)

/** Inspector / sidebar shape. */
val InspectorShape = RoundedCornerShape(
    topEnd = DesignTokens.radiusLg,
    bottomEnd = DesignTokens.radiusLg,
)

/** Pill shape for tags, ratings and toggle chips. */
val PillShape = RoundedCornerShape(DesignTokens.radiusPill)

/** Viewport and scope surfaces are square. */
val ViewportShape = RectangleShape

/** Thumbnail tile shape. */
val ThumbnailShape = RoundedCornerShape(DesignTokens.radiusSm)

/** Slider thumb: fully rounded (RapidRAW h-4 w-4 rounded-full). */
val SliderThumbCorner = RoundedCornerShape(50)
