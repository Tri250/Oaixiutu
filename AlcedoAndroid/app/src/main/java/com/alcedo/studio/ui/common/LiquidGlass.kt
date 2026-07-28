package com.alcedo.studio.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoShapes

/**
 * A glass-morphism surface: a translucent dark background with a subtle
 * top-to-bottom highlight and a hairline border. Used for floating toolbars,
 * overlays and the bottom panel switcher in the editor.
 *
 * Pass [content] as the child composition.
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape = AlcedoShapes.medium,
    blurRadius: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AlcedoColors.Graphite.copy(alpha = 0.82f),
                        AlcedoColors.Charcoal.copy(alpha = 0.92f),
                    ),
                ),
            )
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.02f),
                    ),
                ),
                shape = shape,
            ),
    ) {
        content()
    }
}
