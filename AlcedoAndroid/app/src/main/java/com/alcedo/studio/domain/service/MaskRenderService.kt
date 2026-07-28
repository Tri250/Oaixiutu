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

    /** Render [mask] at [width]x[height] into an ALPHA_8 coverage bitmap. */
    fun render(mask: Mask, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        when (mask) {
            is RadialMask -> drawRadial(canvas, mask, width, height)
            is LinearGradientMask -> drawLinear(canvas, mask, width, height)
            is LuminanceRangeMask -> drawLuminance(canvas, mask, width, height)
            is ColorRangeMask -> drawColorRange(canvas, mask, width, height)
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

    private fun drawLuminance(canvas: Canvas, mask: LuminanceRangeMask, w: Int, h: Int) {
        // Without source pixels, draw a horizontal gradient between the bounds.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, mask.luminanceMin * h, 0f, mask.luminanceMax * h,
            applyAlpha(mask.opacity), Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun drawColorRange(canvas: Canvas, mask: ColorRangeMask, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = applyAlpha(mask.opacity) }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
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
