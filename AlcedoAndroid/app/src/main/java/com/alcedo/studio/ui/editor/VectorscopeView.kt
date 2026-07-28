package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Vectorscope display showing chrominance distribution of the image.
 * Circular display with colour targets at the six primary/secondary hues.
 * 75% and 100% saturation rings shown as reference. Plots Cb/Cr samples
 * from the preview bitmap.
 */
@Composable
fun VectorscopeView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val samples = remember(bitmap) { computeChromaSamples(bitmap) }

    Box(modifier = modifier.background(AlcedoColors.Obsidian)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f * 0.9f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // 100% ring
            drawCircle(
                color = AlcedoColors.Outline,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            // 75% ring
            drawCircle(
                color = AlcedoColors.Divider,
                radius = r * 0.75f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            // 50% ring
            drawCircle(
                color = AlcedoColors.Divider.copy(alpha = 0.5f),
                radius = r * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5.dp.toPx()),
            )
            // 25% ring
            drawCircle(
                color = AlcedoColors.Divider.copy(alpha = 0.3f),
                radius = r * 0.25f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5.dp.toPx()),
            )
            // Center dot
            drawCircle(color = AlcedoColors.TextTertiary, radius = 2.dp.toPx(), center = Offset(cx, cy))

            // Color targets (at 75% ring)
            val targets = listOf(
                "R" to 0f to AlcedoColors.ChannelRed,
                "Yl" to 60f to Color(0xFFFFD24C),
                "Y" to 120f to AlcedoColors.ChannelGreen,
                "Cy" to 180f to Color(0xFF40E0D0),
                "B" to 240f to AlcedoColors.ChannelBlue,
                "Mg" to 300f to Color(0xFFB05CFF),
            )
            targets.forEach { (pair, color) ->
                val angle = Math.toRadians(pair.second - 90.0)
                val tx = cx + r * 0.75f * kotlin.math.cos(angle).toFloat()
                val ty = cy + r * 0.75f * kotlin.math.sin(angle).toFloat()
                drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(tx, ty))
                // Small target box
                drawRect(
                    color = color,
                    topLeft = Offset(tx - 4.dp.toPx(), ty - 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Skin-tone line (~123° from B-Y axis)
            val skinAngle = Math.toRadians(123.0 - 90.0)
            drawLine(
                color = AlcedoColors.Amber.copy(alpha = 0.4f),
                start = Offset(cx, cy),
                end = Offset(
                    (cx + r * kotlin.math.cos(skinAngle)).toFloat(),
                    (cy + r * kotlin.math.sin(skinAngle)).toFloat(),
                ),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )

            // Plot chroma samples
            samples.forEach { (cb, cr) ->
                val sx = cx + cb * r * 2f
                val sy = cy + cr * r * 2f
                drawCircle(
                    color = AlcedoColors.VectorscopeTrace.copy(alpha = 0.25f),
                    radius = 1.dp.toPx(),
                    center = Offset(sx, sy),
                )
            }
        }
        Text(
            text = s.vectorscope,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.spacingXs),
        )
        // Ring labels
        Text(
            text = "75%",
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = DesignTokens.spacingXs),
        )
    }
}

private fun computeChromaSamples(bitmap: Bitmap?): List<Pair<Float, Float>> {
    if (bitmap == null) return emptyList()
    val samples = mutableListOf<Pair<Float, Float>>()
    val step = (bitmap.width / 64).coerceAtLeast(1)
    val yStep = (bitmap.height / 64).coerceAtLeast(1)
    for (px in 0 until bitmap.width step step) {
        for (py in 0 until bitmap.height step yStep) {
            val pixel = bitmap.getPixel(px, py)
            val r = (pixel shr 16 and 0xFF) / 255f - 0.5f
            val g = (pixel shr 8 and 0xFF) / 255f - 0.5f
            val b = (pixel and 0xFF) / 255f - 0.5f
            val cb = -0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 0.5f * r - 0.418688f * g - 0.081312f * b
            samples += cb to cr
        }
    }
    return samples
}
