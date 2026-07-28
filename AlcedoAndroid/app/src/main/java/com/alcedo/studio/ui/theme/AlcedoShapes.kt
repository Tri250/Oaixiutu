package com.alcedo.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Alcedo shape definitions. Panels use subtle, small radii so the editor reads as a
 * precision tool rather than a consumer app.
 */
val AlcedoShapes = Shapes(
    extraSmall = RoundedCornerShape(DesignTokens.radiusXs),
    small = RoundedCornerShape(DesignTokens.radiusSm),
    medium = RoundedCornerShape(DesignTokens.radiusMd),
    large = RoundedCornerShape(DesignTokens.radiusLg),
    extraLarge = RoundedCornerShape(DesignTokens.radiusXl),
)

/** Panel container shape with a flat top and small bottom radius. */
val PanelContainerShape = RoundedCornerShape(
    topStart = DesignTokens.radiusLg,
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

val SliderThumbCorner = RoundedCornerShape(50)
