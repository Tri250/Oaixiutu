package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes

/**
 * Composition guide overlays (RapidRAW-style) drawn over the crop rectangle.
 */
enum class CompositionOverlayType(val displayName: String) {
    NONE("None"),
    THIRDS("Thirds"),
    DIAGONAL("Diagonal"),
    TRIANGLE("Triangle"),
    GOLDEN_SPIRAL("Golden Spiral"),
    PHI_GRID("Phi Grid"),
    ARMATURE("Armature");

    @Composable
    fun label(): String = when (this) {
        NONE -> stringRes { cropOverlayNone }
        THIRDS -> stringRes { cropOverlayThirds }
        DIAGONAL -> stringRes { cropOverlayDiagonal }
        TRIANGLE -> stringRes { cropOverlayTriangle }
        GOLDEN_SPIRAL -> stringRes { cropOverlayGoldenSpiral }
        PHI_GRID -> stringRes { cropOverlayPhiGrid }
        ARMATURE -> stringRes { cropOverlayArmature }
    }
}

/**
 * Draws the requested composition overlay inside [rect] (pixel coordinates).
 * Shared between the standalone [CompositionOverlay] composable and the crop
 * overlay so the guides always line up with the crop rectangle.
 */
internal fun DrawScope.drawCompositionOverlay(
    overlayType: CompositionOverlayType,
    rect: Rect
) {
    if (overlayType == CompositionOverlayType.NONE) return
    if (rect.width < 2f || rect.height < 2f) return

    val color = Color.White.copy(alpha = 0.4f)
    val stroke = 1.dp.toPx()

    when (overlayType) {
        CompositionOverlayType.THIRDS -> {
            for (i in 1..2) {
                val fx = rect.left + rect.width * i / 3f
                val fy = rect.top + rect.height * i / 3f
                drawLine(color, Offset(fx, rect.top), Offset(fx, rect.bottom), stroke)
                drawLine(color, Offset(rect.left, fy), Offset(rect.right, fy), stroke)
            }
        }

        CompositionOverlayType.DIAGONAL -> {
            drawLine(color, Offset(rect.left, rect.top), Offset(rect.right, rect.bottom), stroke)
            drawLine(color, Offset(rect.right, rect.top), Offset(rect.left, rect.bottom), stroke)
        }

        CompositionOverlayType.TRIANGLE -> {
            // Golden triangle: one diagonal plus perpendiculars dropped from the
            // other two corners onto that diagonal.
            val a = Offset(rect.left, rect.top)
            val b = Offset(rect.right, rect.bottom)
            drawLine(color, a, b, stroke)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val lenSq = dx * dx + dy * dy
            if (lenSq > 0f) {
                fun foot(p: Offset): Offset {
                    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
                    return Offset(a.x + t * dx, a.y + t * dy)
                }
                val tr = Offset(rect.right, rect.top)
                val bl = Offset(rect.left, rect.bottom)
                drawLine(color, tr, foot(tr), stroke)
                drawLine(color, bl, foot(bl), stroke)
            }
        }

        CompositionOverlayType.PHI_GRID -> {
            for (f in listOf(0.382f, 0.618f)) {
                val fx = rect.left + rect.width * f
                val fy = rect.top + rect.height * f
                drawLine(color, Offset(fx, rect.top), Offset(fx, rect.bottom), stroke)
                drawLine(color, Offset(rect.left, fy), Offset(rect.right, fy), stroke)
            }
        }

        CompositionOverlayType.ARMATURE -> {
            for (f in listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)) {
                val fx = rect.left + rect.width * f
                val fy = rect.top + rect.height * f
                drawLine(color, Offset(fx, rect.top), Offset(fx, rect.bottom), stroke)
                drawLine(color, Offset(rect.left, fy), Offset(rect.right, fy), stroke)
            }
        }

        CompositionOverlayType.GOLDEN_SPIRAL -> drawGoldenSpiral(rect, color, stroke)

        CompositionOverlayType.NONE -> {}
    }
}

/**
 * Approximate golden spiral built from quarter-circle arcs. The rectangle is
 * repeatedly subdivided by cutting off squares (side = the shorter edge); a
 * 90° arc is inscribed in each square and the dividing line is drawn. The cut
 * side cycles right → top → left → bottom so the arcs chain into a spiral.
 */
private fun DrawScope.drawGoldenSpiral(rect: Rect, color: Color, stroke: Float) {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    // Phase 0 = cut square on the right, 1 = top, 2 = left, 3 = bottom.
    var phase = if (right - left >= bottom - top) 0 else 1

    repeat(7) {
        val w = right - left
        val h = bottom - top
        if (w < 4f || h < 4f) return@repeat

        when (phase % 4) {
            0 -> {
                if (h > w) return@repeat
                val s = h
                val center = Offset(right, top)
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - s, center.y - s),
                    size = Size(s * 2f, s * 2f),
                    style = Stroke(width = stroke)
                )
                drawLine(color, Offset(right - s, top), Offset(right - s, bottom), stroke)
                right -= s
            }
            1 -> {
                if (w > h) return@repeat
                val s = w
                val center = Offset(left, top)
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - s, center.y - s),
                    size = Size(s * 2f, s * 2f),
                    style = Stroke(width = stroke)
                )
                drawLine(color, Offset(left, top + s), Offset(right, top + s), stroke)
                top += s
            }
            2 -> {
                if (h > w) return@repeat
                val s = h
                val center = Offset(left, bottom)
                drawArc(
                    color = color,
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - s, center.y - s),
                    size = Size(s * 2f, s * 2f),
                    style = Stroke(width = stroke)
                )
                drawLine(color, Offset(left + s, top), Offset(left + s, bottom), stroke)
                left += s
            }
            3 -> {
                if (w > h) return@repeat
                val s = w
                val center = Offset(right, bottom)
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - s, center.y - s),
                    size = Size(s * 2f, s * 2f),
                    style = Stroke(width = stroke)
                )
                drawLine(color, Offset(left, bottom - s), Offset(right, bottom - s), stroke)
                bottom -= s
            }
        }
        phase++
    }
}

/** Standalone composition overlay that fills its available space. */
@Composable
fun CompositionOverlay(
    overlayType: CompositionOverlayType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCompositionOverlay(overlayType, Rect(0f, 0f, size.width, size.height))
    }
}

/** Horizontal picker for the active composition guide. */
@Composable
fun CompositionOverlaySelector(
    selected: CompositionOverlayType,
    onSelect: (CompositionOverlayType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(CompositionOverlayType.entries.toList()) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = {
                    Text(type.label(), style = MaterialTheme.typography.labelSmall)
                }
            )
        }
    }
}
