package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import com.alcedo.studio.data.model.WatermarkConfig
import com.alcedo.studio.data.model.WatermarkPosition
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rasterises a text or image watermark onto an exported bitmap according to a
 * [WatermarkConfig]. Positioning and scaling are normalised to the image.
 */
@Singleton
class WatermarkService @Inject constructor() {

    /** Apply [config] to [bitmap], returning a new watermarked bitmap. */
    fun apply(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        if (!config.enabled) return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        if (config.imagePath != null) {
            drawImageWatermark(canvas, result.width, result.height, config)
        } else {
            drawTextWatermark(canvas, result.width, result.height, config)
        }
        return result
    }

    private fun drawTextWatermark(canvas: Canvas, w: Int, h: Int, config: WatermarkConfig) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyAlpha(Color.WHITE, config.opacity)
            textSize = config.fontSize * (w.coerceAtMost(h) / 1024f).coerceAtLeast(0.5f) * 2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(textSize * 0.04f, 0f, 0f, applyAlpha(Color.BLACK, config.opacity * 0.6f))
        }
        val textWidth = paint.measureText(config.text)
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val pos = resolveRect(w, h, textWidth, textHeight, config.position, config.scale)
        canvas.drawText(config.text, pos.left, pos.top - fm.ascent, paint)
    }

    private fun drawImageWatermark(canvas: Canvas, w: Int, h: Int, config: WatermarkConfig) {
        val opts = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val wm = runCatching {
            android.graphics.BitmapFactory.decodeFile(config.imagePath, opts)
        }.getOrNull() ?: return
        val maxDim = (w.coerceAtMost(h) * config.scale).coerceAtLeast(32f)
        val scale = maxDim / wm.width.coerceAtLeast(wm.height)
        val sw = (wm.width * scale).toInt().coerceAtLeast(1)
        val sh = (wm.height * scale).toInt().coerceAtLeast(1)
        val pos = resolveRect(w, h, sw.toFloat(), sh.toFloat(), config.position, config.scale)
        val src = Rect(0, 0, wm.width, wm.height)
        val dst = Rect(pos.left.toInt(), pos.top.toInt(), pos.left.toInt() + sw, pos.top.toInt() + sh)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (config.opacity * 255).toInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(wm, src, dst, paint)
        wm.recycle()
    }

    private fun resolveRect(
        w: Int, h: Int, itemW: Float, itemH: Float,
        position: WatermarkPosition, scale: Float,
    ): RectF {
        val margin = (w.coerceAtMost(h) * 0.02f)
        val left: Float
        val top: Float
        when (position) {
            WatermarkPosition.TOP_LEFT -> { left = margin; top = margin }
            WatermarkPosition.TOP_RIGHT -> { left = w - itemW - margin; top = margin }
            WatermarkPosition.BOTTOM_LEFT -> { left = margin; top = h - itemH - margin }
            WatermarkPosition.BOTTOM_RIGHT -> { left = w - itemW - margin; top = h - itemH - margin }
            WatermarkPosition.CENTER -> { left = (w - itemW) / 2f; top = (h - itemH) / 2f }
        }
        return RectF(left, top, left + itemW, top + itemH)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
