package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Color wheels panel: Lift, Gamma and Gain trackballs. Each trackball is a
 * circular hue wheel; dragging inside it sets the hue (angle) and saturation
 * (distance from centre). A luminance slider sits under each wheel. The three
 * wheels map to [AdjustmentParams] lift/gamma/gain hue/sat/lum fields.
 */
@Composable
fun ColorWheelView(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd)) {
        SectionHeader(title = s.colorWheels)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            TrackballColumn(
                label = s.lift,
                hue = params.liftHue,
                sat = params.liftSat,
                lum = params.liftLum,
                size = DesignTokens.panelWidthCompact / 4,
                onHueChange = { onUpdate("liftHue", it) },
                onSatChange = { onUpdate("liftSat", it) },
                onLumChange = { onUpdate("liftLum", it) },
                modifier = Modifier.weight(1f),
            )
            TrackballColumn(
                label = s.gamma,
                hue = params.gammaHue,
                sat = params.gammaSat,
                lum = params.gammaLum,
                size = DesignTokens.panelWidthCompact / 4,
                onHueChange = { onUpdate("gammaHue", it) },
                onSatChange = { onUpdate("gammaSat", it) },
                onLumChange = { onUpdate("gammaLum", it) },
                modifier = Modifier.weight(1f),
            )
            TrackballColumn(
                label = s.gain,
                hue = params.gainHue,
                sat = params.gainSat,
                lum = params.gainLum,
                size = DesignTokens.panelWidthCompact / 4,
                onHueChange = { onUpdate("gainHue", it) },
                onSatChange = { onUpdate("gainSat", it) },
                onLumChange = { onUpdate("gainLum", it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackballColumn(
    label: String,
    hue: Float,
    sat: Float,
    lum: Float,
    size: Dp,
    onHueChange: (Float) -> Unit,
    onSatChange: (Float) -> Unit,
    onLumChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
    ) {
        Trackball(
            hue = hue,
            sat = sat,
            size = size,
            onHueChange = onHueChange,
            onSatChange = onSatChange,
        )
        AdjustmentSlider(
            label = label + " " + com.alcedo.studio.i18n.Strings.res.luminanceAmount,
            value = lum, defaultValue = 0f, range = -1f..1f,
            valueFormatter = { "%+.2f".format(it) },
            onValueChange = onLumChange,
        )
    }
}

/** A single hue wheel trackball with a draggable indicator. */
@Composable
private fun Trackball(
    hue: Float,
    sat: Float,
    size: Dp,
    onHueChange: (Float) -> Unit,
    onSatChange: (Float) -> Unit,
) {
    var radiusPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = Modifier
            .size(size)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    radiusPx = minOf(this.size.width, this.size.height) / 2f
                    detectDragGestures { change, _ ->
                        val center = Offset(this.size.width / 2f, this.size.height / 2f)
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(radiusPx)
                        val angle = ((kotlin.math.atan2(dy.toDouble(), dx.toDouble()) * 180.0 / Math.PI + 360.0) % 360.0).toFloat()
                        onHueChange(angle)
                        onSatChangeChange(dist / radiusPx, onSatChange)
                    }
                },
        ) {
            val r = this.size.minDimension / 2f
            val sweepBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFF5C5C), Color(0xFFFFD24C), Color(0xFF4CD08C),
                    Color(0xFF40E0D0), Color(0xFF4A9EFF), Color(0xFFB05CFF),
                    Color(0xFFFF5C5C),
                ),
            )
            drawCircle(brush = sweepBrush, radius = r, center = this.center)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.0f), Color.Black.copy(alpha = 0.65f)),
                    center = this.center,
                    radius = r,
                ),
                radius = r,
                center = this.center,
            )
            drawCircle(
                color = AlcedoColors.LumaTrace,
                radius = r,
                center = this.center,
                style = Stroke(width = 1.dp.toPx()),
            )
            // Indicator
            val angleRad = Math.toRadians(hue.toDouble())
            val dist = sat.coerceIn(0f, 1f) * r
            val ix = (this.center.x + dist * kotlin.math.cos(angleRad)).toFloat()
            val iy = (this.center.y + dist * kotlin.math.sin(angleRad)).toFloat()
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(ix, iy))
            drawCircle(
                color = Color.Black,
                radius = 5.dp.toPx(),
                center = Offset(ix, iy),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

private fun onSatChangeChange(value: Float, onSatChange: (Float) -> Unit) {
    onSatChange(value)
}
