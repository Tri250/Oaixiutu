package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

enum class GamutOverlay(val label: String) {
    SRGB("sRGB"),
    P3("P3"),
    REC2020("Rec.2020"),
    ACES("ACES")
}

@Composable
fun ChromaticityView(
    chromaticityData: ChromaticityData,
    modifier: Modifier = Modifier,
    showGamut: Set<GamutOverlay> = setOf(GamutOverlay.SRGB),
    showSpectralLocus: Boolean = true,
    backgroundColor: Color = Color.Unspecified
) {
    // UX 修复: 背景色从主题派生,确保主题切换时一致
    val effectiveBg = if (backgroundColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        backgroundColor
    }
    val onScopeSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val w = size.width
            val h = size.height
            val displaySize = minOf(w, h)
            val offsetX = (w - displaySize) / 2f
            val offsetY = (h - displaySize) / 2f
            val padding = 8f
            val plotSize = displaySize - 2 * padding

            // Background
            drawRect(color = effectiveBg, size = size)

            // CIE xy diagram area: x ∈ [0, 0.8], y ∈ [0, 0.9]
            val xMin = 0f; val xMax = 0.8f
            val yMin = 0f; val yMax = 0.9f

            fun toScreenX(cx: Float): Float =
                offsetX + padding + ((cx - xMin) / (xMax - xMin)) * plotSize

            fun toScreenY(cy: Float): Float =
                offsetY + padding + plotSize - ((cy - yMin) / (yMax - yMin)) * plotSize

            // Draw spectral locus with colored fill
            if (showSpectralLocus) {
                val locus = ScopeAnalyzer.SPECTRAL_LOCUS
                if (locus.size >= 3) {
                    // Fill the spectral locus with the spectral colors
                    val locusPath = Path()
                    locusPath.moveTo(toScreenX(locus[0].first), toScreenY(locus[0].second))
                    for (i in 1 until locus.size) {
                        locusPath.lineTo(toScreenX(locus[i].first), toScreenY(locus[i].second))
                    }
                    // Close from last locus point back to first via the purple line (bottom)
                    locusPath.lineTo(toScreenX(locus.last().first), toScreenY(locus.last().second))
                    locusPath.lineTo(toScreenX(locus[0].first), toScreenY(locus[0].second))
                    locusPath.close()

                    // Draw filled spectral locus with gradient-like segments
                    for (i in 0 until locus.size - 1) {
                        val x1 = locus[i].first; val y1 = locus[i].second
                        val x2 = locus[i + 1].first; val y2 = locus[i + 1].second
                        val midX = (x1 + x2) / 2f
                        val midY = (y1 + y2) / 2f
                        val rgb = ScopeAnalyzer.xyToLinearRGB(midX, midY)
                        val r = ScopeAnalyzer.linearToSrgb(rgb[0])
                        val g = ScopeAnalyzer.linearToSrgb(rgb[1])
                        val b = ScopeAnalyzer.linearToSrgb(rgb[2])
                        drawLine(
                            color = Color(r, g, b, 0.8f),
                            start = Offset(toScreenX(x1), toScreenY(y1)),
                            end = Offset(toScreenX(x2), toScreenY(y2)),
                            strokeWidth = 2f
                        )
                    }

                    // Purple line (connect red end to blue end)
                    val blueEnd = locus.first()
                    val redEnd = locus.last()
                    val purpleLineSteps = 20
                    for (i in 0 until purpleLineSteps) {
                        val t1 = i.toFloat() / purpleLineSteps
                        val t2 = (i + 1).toFloat() / purpleLineSteps
                        val x1 = redEnd.first * (1 - t1) + blueEnd.first * t1
                        val y1 = redEnd.second * (1 - t1) + blueEnd.second * t1
                        val x2 = redEnd.first * (1 - t2) + blueEnd.first * t2
                        val y2 = redEnd.second * (1 - t2) + blueEnd.second * t2
                        val midT = (t1 + t2) / 2f
                        val midX = redEnd.first * (1 - midT) + blueEnd.first * midT
                        val midY = redEnd.second * (1 - midT) + blueEnd.second * midT
                        val rgb = ScopeAnalyzer.xyToLinearRGB(midX, midY)
                        val r = ScopeAnalyzer.linearToSrgb(rgb[0])
                        val g = ScopeAnalyzer.linearToSrgb(rgb[1])
                        val b = ScopeAnalyzer.linearToSrgb(rgb[2])
                        drawLine(
                            color = Color(r, g, b, 0.6f),
                            start = Offset(toScreenX(x1), toScreenY(y1)),
                            end = Offset(toScreenX(x2), toScreenY(y2)),
                            strokeWidth = 2f
                        )
                    }

                    // Faint fill inside the locus
                    drawPath(
                        path = locusPath,
                        color = onScopeSurface.copy(alpha = 0.04f)
                    )
                }
            }

            // Draw gamut triangles
            val gamutColors = mapOf(
                GamutOverlay.SRGB to onScopeSurface.copy(alpha = 0.7f),
                GamutOverlay.P3 to Color(0xFF4FC3F7).copy(alpha = 0.7f),
                GamutOverlay.REC2020 to Color(0xFFFFB74D).copy(alpha = 0.7f),
                GamutOverlay.ACES to Color(0xFFCE93D8).copy(alpha = 0.7f)
            )
            val gamutVertices = mapOf(
                GamutOverlay.SRGB to ScopeAnalyzer.SRGB_GAMUT,
                GamutOverlay.P3 to ScopeAnalyzer.P3_GAMUT,
                GamutOverlay.REC2020 to ScopeAnalyzer.REC2020_GAMUT,
                GamutOverlay.ACES to ScopeAnalyzer.ACES_GAMUT
            )

            for (overlay in showGamut) {
                val vertices = gamutVertices[overlay] ?: continue
                val color = gamutColors[overlay] ?: continue
                if (vertices.size < 3) continue

                val trianglePath = Path()
                trianglePath.moveTo(
                    toScreenX(vertices[0].first),
                    toScreenY(vertices[0].second)
                )
                for (i in 1 until vertices.size) {
                    trianglePath.lineTo(
                        toScreenX(vertices[i].first),
                        toScreenY(vertices[i].second)
                    )
                }
                trianglePath.close()

                drawPath(
                    path = trianglePath,
                    color = color.copy(alpha = 0.1f)
                )
                drawPath(
                    path = trianglePath,
                    color = color,
                    style = Stroke(width = 1f)
                )

                // Vertex dots
                for (v in vertices) {
                    drawCircle(
                        color = color,
                        radius = 2.5f,
                        center = Offset(toScreenX(v.first), toScreenY(v.second))
                    )
                }
            }

            // Plot image pixel chromaticities
            val points = chromaticityData.points
            if (points.isNotEmpty()) {
                for (pt in points) {
                    val sx = toScreenX(pt.x)
                    val sy = toScreenY(pt.y)
                    val rgb = ScopeAnalyzer.xyToLinearRGB(pt.x, pt.y)
                    val r = ScopeAnalyzer.linearToSrgb(rgb[0])
                    val g = ScopeAnalyzer.linearToSrgb(rgb[1])
                    val b = ScopeAnalyzer.linearToSrgb(rgb[2])
                    drawCircle(
                        color = Color(r, g, b, 0.5f),
                        radius = 1.5f,
                        center = Offset(sx, sy)
                    )
                }
            }

            // Draw axis border
            drawRect(
                color = onScopeSurface.copy(alpha = 0.1f),
                topLeft = Offset(offsetX + padding, offsetY + padding),
                size = Size(plotSize, plotSize),
                style = Stroke(width = 0.5f)
            )
        }

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CIE xy Chromaticity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GamutOverlay.entries.forEach { g ->
                val isActive = g in showGamut
                val color = when (g) {
                    GamutOverlay.SRGB -> Color.White
                    GamutOverlay.P3 -> Color(0xFF4FC3F7)
                    GamutOverlay.REC2020 -> Color(0xFFFFB74D)
                    GamutOverlay.ACES -> Color(0xFFCE93D8)
                }
                Text(
                    text = g.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) color else Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }
    }
}
