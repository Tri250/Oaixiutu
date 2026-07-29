package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import com.alcedo.studio.data.model.BrushMask
import com.alcedo.studio.data.model.LinearGradientMask
import com.alcedo.studio.data.model.LuminanceRangeMask
import com.alcedo.studio.data.model.Mask
import com.alcedo.studio.data.model.RadialMask
import com.alcedo.studio.data.model.ColorRangeMask
import com.alcedo.studio.data.model.AiSubjectMask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rasterises masks into coverage bitmaps for the GPU pipeline. Parametric masks
 * (radial, linear, luminance, color) are drawn in Kotlin; brush and AI masks
 * carry their own coverage data. Output is an ALPHA_8 bitmap in image space.
 */
@Singleton
class MaskRenderService @Inject constructor() {

    /**
     * Render [mask] at [width]x[height] into an ALPHA_8 coverage bitmap.
     * Pass [source] (the image being edited) so luminance/color-range masks
     * can sample actual pixel values; parametric masks ignore it.
     */
    fun render(mask: Mask, width: Int, height: Int, source: Bitmap? = null): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        when (mask) {
            is RadialMask -> drawRadial(canvas, mask, width, height)
            is LinearGradientMask -> drawLinear(canvas, mask, width, height)
            is LuminanceRangeMask -> drawLuminance(bmp, mask, width, height, source)
            is ColorRangeMask -> drawColorRange(bmp, mask, width, height, source)
            is BrushMask -> drawBrush(canvas, mask, width, height)
            is AiSubjectMask -> drawSubject(canvas, mask, width, height)
        }
        return if (mask.invert) invert(bmp) else bmp
    }

    private fun drawRadial(canvas: Canvas, mask: RadialMask, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
            shader = android.graphics.RadialGradient(
                mask.centerX * w, mask.centerY * h,
                maxOf(mask.radiusX * w, mask.radiusY * h),
                applyAlpha(mask.opacity), Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val rect = RectF(
            (mask.centerX - mask.radiusX) * w, (mask.centerY - mask.radiusY) * h,
            (mask.centerX + mask.radiusX) * w, (mask.centerY + mask.radiusY) * h,
        )
        canvas.save()
        canvas.rotate(mask.rotation, mask.centerX * w, mask.centerY * h)
        canvas.drawRect(rect, paint)
        canvas.restore()
    }

    private fun drawLinear(canvas: Canvas, mask: LinearGradientMask, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            mask.startX * w, mask.startY * h, mask.endX * w, mask.endY * h,
            applyAlpha(mask.opacity), Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawLuminance(bmp: Bitmap, mask: LuminanceRangeMask, w: Int, h: Int, source: Bitmap?) {
        if (source == null) {
            // No source pixels available; fall back to a uniform opacity fill.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = applyAlpha(mask.opacity) }
            Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            return
        }
        val sw = source.width
        val sh = source.height
        val srcPixels = IntArray(sw * sh)
        source.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)
        val outPixels = IntArray(w * h)
        val minLum = (mask.luminanceMin * 255f).toInt().coerceIn(0, 255)
        val maxLum = (mask.luminanceMax * 255f).toInt().coerceIn(0, 255)
        val feather = (mask.feather * 255f).toInt().coerceIn(1, 255)
        val maxAlpha = applyAlpha(mask.opacity)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x.toFloat() * sw / w).toInt().coerceIn(0, sw - 1)
                val sy = (y.toFloat() * sh / h).toInt().coerceIn(0, sh - 1)
                val px = srcPixels[sy * sw + sx]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                val alpha = when {
                    lum in minLum..maxLum -> maxAlpha
                    lum >= minLum - feather && lum <= maxLum + feather -> {
                        val dist = if (lum < minLum) minLum - lum else lum - maxLum
                        (maxAlpha * (1f - dist.toFloat() / feather)).toInt().coerceIn(0, maxAlpha)
                    }
                    else -> 0
                }
                outPixels[y * w + x] = alpha shl 24
            }
        }
        bmp.setPixels(outPixels, 0, w, 0, 0, w, h)
    }

    private fun drawColorRange(bmp: Bitmap, mask: ColorRangeMask, w: Int, h: Int, source: Bitmap?) {
        if (source == null) {
            // No source pixels available; fall back to a uniform opacity fill.
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = applyAlpha(mask.opacity) }
            Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            return
        }
        val sw = source.width
        val sh = source.height
        val srcPixels = IntArray(sw * sh)
        source.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)
        val outPixels = IntArray(w * h)
        val centerHue = mask.centerHue
        val hueRange = mask.hueRange.coerceAtLeast(1f)
        val satMin = mask.saturationMin
        val feather = mask.feather.coerceAtLeast(0.001f)
        val maxAlpha = applyAlpha(mask.opacity)
        val hsv = FloatArray(3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x.toFloat() * sw / w).toInt().coerceIn(0, sw - 1)
                val sy = (y.toFloat() * sh / h).toInt().coerceIn(0, sh - 1)
                val px = srcPixels[sy * sw + sx]
                Color.RGBToHSV((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF, hsv)
                val hue = hsv[0] // 0-360
                val sat = hsv[1] // 0-1
                if (sat < satMin) {
                    outPixels[y * w + x] = 0
                    continue
                }
                // Hue distance accounting for 0/360 wraparound.
                var hueDist = kotlin.math.abs(hue - centerHue)
                if (hueDist > 180f) hueDist = 360f - hueDist
                val alpha = when {
                    hueDist <= hueRange -> {
                        val factor = 1f - (hueDist / hueRange)
                        (maxAlpha * factor).toInt().coerceIn(0, maxAlpha)
                    }
                    hueDist <= hueRange + feather * 180f -> {
                        val t = (hueDist - hueRange) / (feather * 180f)
                        (maxAlpha * (1f - t)).toInt().coerceIn(0, maxAlpha)
                    }
                    else -> 0
                }
                outPixels[y * w + x] = alpha shl 24
            }
        }
        bmp.setPixels(outPixels, 0, w, 0, 0, w, h)
    }

    private fun drawBrush(canvas: Canvas, mask: BrushMask, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyAlpha(mask.opacity)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.FILL
        }
        for (stroke in mask.strokes) {
            for (point in stroke.points) {
                if (point.size < 2) continue
                val cx = point[0] * w
                val cy = point[1] * h
                val r = stroke.radius * minOf(w, h)
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }

    private fun drawSubject(canvas: Canvas, mask: AiSubjectMask, w: Int, h: Int) {
        if (mask.polygon.isNotEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = applyAlpha(mask.opacity) }
            val path = android.graphics.Path()
            mask.polygon.firstOrNull()?.let { first ->
                path.moveTo(first[0] * w, first[1] * h)
                for (i in 1 until mask.polygon.size) {
                    val p = mask.polygon[i]
                    path.lineTo(p[0] * w, p[1] * h)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun applyAlpha(opacity: Float): Int = ((opacity.coerceIn(0f, 1f)) * 255).toInt().coerceIn(0, 255)

    private fun invert(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ALPHA_8)
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        for (i in px.indices) {
            val a = (px[i] ushr 24) and 0xFF
            px[i] = ((255 - a) shl 24)
        }
        out.setPixels(px, 0, src.width, 0, 0, src.width, src.height)
        return out
    }
}
