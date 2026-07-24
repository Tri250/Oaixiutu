package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.ui.theme.AlcedoEditorColors

/**
 * Channel selection for the waveform scope.
 * Luminance/Red/Green/Blue display single-channel data;
 * Overlay stacks R/G/B with transparency; RGBParade shows three side-by-side.
 */
enum class ScopeChannel(val label: String) {
    LUMINANCE("L"),
    RED("R"),
    GREEN("G"),
    BLUE("B"),
    OVERLAY("RGB"),
    RGB_PARADE("Parade")
}

/**
 * Professional waveform scope — displays luminance or per-channel brightness
 * distribution across the horizontal axis of the image.
 *
 * Each column in the waveform represents the corresponding column of pixels.
 * Vertical axis: brightness (0 IRE at top → 100 IRE at bottom).
 * Pixel intensity = data density (how many pixels share that brightness in
 * that column).
 *
 * Designed for professional photo editing: IRE grid, labels, and
 * Alcedo Design System colors.
 */
@Composable
fun WaveformScope(
    waveformData: WaveformData,
    modifier: Modifier = Modifier,
    channel: ScopeChannel = ScopeChannel.RGB_PARADE,
    backgroundColor: Color = Color.Unspecified
) {
    val effectiveBg = if (backgroundColor == Color.Unspecified) {
        AlcedoEditorColors.scopeBackground
    } else {
        backgroundColor
    }

    val textMeasurer = rememberTextMeasurer()
    val ireLabelStyle = TextStyle(
        color = Color.White.copy(alpha = 0.35f),
        fontSize = 8.sp
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            val labelWidth = 22f  // space for IRE labels on the left
            val padding = 4f
            val graphLeft = labelWidth + padding
            val graphTop = padding
            val graphW = w - graphLeft - padding
            val graphH = h - 2 * padding

            // Background
            drawRect(color = effectiveBg, size = size)

            // IRE grid lines and labels
            val ireSteps = listOf(0, 10, 25, 50, 75, 100)
            for (ire in ireSteps) {
                val y = graphTop + graphH * (1f - ire / 100f)
                val lineAlpha = when (ire) {
                    0, 100 -> 0.15f
                    50 -> 0.10f
                    else -> 0.06f
                }
                drawLine(
                    color = Color.White.copy(alpha = lineAlpha),
                    start = Offset(graphLeft, y),
                    end = Offset(graphLeft + graphW, y),
                    strokeWidth = if (ire == 50) 0.8f else 0.5f
                )
                // IRE label
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$ire",
                    topLeft = Offset(2f, y - 5f),
                    style = ireLabelStyle
                )
            }

            val graphRect = Rect(graphLeft, graphTop, graphLeft + graphW, graphTop + graphH)

            when (channel) {
                ScopeChannel.LUMINANCE -> {
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.luminance },
                        color = AlcedoEditorColors.scopeWaveform,
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                ScopeChannel.RED -> {
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.r },
                        color = AlcedoEditorColors.scopeParadeR,
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                ScopeChannel.GREEN -> {
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.g },
                        color = AlcedoEditorColors.scopeParadeG,
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                ScopeChannel.BLUE -> {
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.b },
                        color = AlcedoEditorColors.scopeParadeB,
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                ScopeChannel.OVERLAY -> {
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.r },
                        color = AlcedoEditorColors.scopeParadeR.copy(alpha = 0.55f),
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.g },
                        color = AlcedoEditorColors.scopeParadeG.copy(alpha = 0.50f),
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.b },
                        color = AlcedoEditorColors.scopeParadeB.copy(alpha = 0.50f),
                        rect = graphRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                ScopeChannel.RGB_PARADE -> {
                    val gap = 3f
                    val paradeWidth = (graphW - 2 * gap) / 3f
                    val paradeHeight = graphH

                    // R
                    val rRect = Rect(graphLeft, graphTop, graphLeft + paradeWidth, graphTop + paradeHeight)
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.r },
                        color = AlcedoEditorColors.scopeParadeR,
                        rect = rRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    // Channel label
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "R",
                        topLeft = Offset(rRect.left + 2f, rRect.top + 2f),
                        style = TextStyle(color = AlcedoEditorColors.scopeParadeR.copy(alpha = 0.7f), fontSize = 8.sp)
                    )

                    // G
                    val gRect = Rect(
                        graphLeft + paradeWidth + gap, graphTop,
                        graphLeft + 2 * paradeWidth + gap, graphTop + paradeHeight
                    )
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.g },
                        color = AlcedoEditorColors.scopeParadeG,
                        rect = gRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "G",
                        topLeft = Offset(gRect.left + 2f, gRect.top + 2f),
                        style = TextStyle(color = AlcedoEditorColors.scopeParadeG.copy(alpha = 0.7f), fontSize = 8.sp)
                    )

                    // B
                    val bRect = Rect(
                        graphLeft + 2 * paradeWidth + 2 * gap, graphTop,
                        graphLeft + 3 * paradeWidth + 2 * gap, graphTop + paradeHeight
                    )
                    drawWaveformChannel(
                        data = waveformData,
                        channelSelector = { it.b },
                        color = AlcedoEditorColors.scopeParadeB,
                        rect = bRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "B",
                        topLeft = Offset(bRect.left + 2f, bRect.top + 2f),
                        style = TextStyle(color = AlcedoEditorColors.scopeParadeB.copy(alpha = 0.7f), fontSize = 8.sp)
                    )
                }
            }

            // Border
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(graphLeft, graphTop),
                size = Size(graphW, graphH),
                style = Stroke(width = 1f)
            )
        }

        // Channel selector labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ScopeChannel.entries.forEach { ch ->
                val isSelected = channel == ch
                val color = when (ch) {
                    ScopeChannel.LUMINANCE -> AlcedoEditorColors.scopeWaveform
                    ScopeChannel.RED -> AlcedoEditorColors.scopeParadeR
                    ScopeChannel.GREEN -> AlcedoEditorColors.scopeParadeG
                    ScopeChannel.BLUE -> AlcedoEditorColors.scopeParadeB
                    ScopeChannel.OVERLAY -> Color.White
                    ScopeChannel.RGB_PARADE -> Color.White
                }
                Text(
                    text = ch.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) color else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Draw a 2D waveform density plot within the given rect.
 * x-axis = image column, y-axis = brightness level (0=top=100IRE, bottom=0IRE).
 * Pixel opacity = data intensity (frequency).
 */
private fun DrawScope.drawWaveformChannel(
    data: WaveformData,
    channelSelector: (WaveformData) -> FloatArray,
    color: Color,
    rect: Rect,
    dataCols: Int,
    dataRows: Int
) {
    if (dataCols <= 0 || dataRows <= 0) return
    val channelData = channelSelector(data)
    if (channelData.isEmpty()) return

    val graphW = rect.width
    val graphH = rect.height
    val cellW = graphW / dataCols
    val cellH = graphH / dataRows

    for (col in 0 until dataCols) {
        for (row in 0 until dataRows) {
            val intensity = channelData[col * dataRows + row]
            if (intensity > 0.02f) {
                val x = rect.left + col * cellW
                val y = rect.top + row * cellH
                drawRect(
                    color = color.copy(alpha = intensity.coerceIn(0f, 1f) * 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(cellW.coerceAtLeast(1f), cellH.coerceAtLeast(1f))
                )
            }
        }
    }
}
