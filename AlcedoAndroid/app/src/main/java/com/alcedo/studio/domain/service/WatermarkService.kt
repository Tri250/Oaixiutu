package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File

/**
 * Type of watermark to render.
 */
enum class WatermarkType { TEXT, IMAGE, TEXT_WITH_LOGO }

/**
 * Anchor position of the watermark on the canvas.
 */
enum class WatermarkPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER, BOTTOM_CENTER
}

/**
 * Configuration for the custom watermark renderer.
 *
 * [color] is an ARGB int (e.g. [Color.WHITE]). [opacity] is in 0..1.
 * [fontSize] is interpreted as design points relative to a 1080px-wide reference
 * canvas, and is scaled proportionally to the actual target bitmap width so the
 * watermark looks consistent across image sizes.
 */
data class WatermarkConfig(
    val enabled: Boolean = false,
    val type: WatermarkType = WatermarkType.TEXT,
    val text: String = "© Photographer",
    val imagePath: String? = null,
    val fontSize: Float = 24f,
    val color: Int = Color.WHITE,
    val opacity: Float = 0.8f,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val margin: Float = 16f,
    val rotation: Float = 0f,
    val hasShadow: Boolean = true,
    val hasBorder: Boolean = false,
    val fontAsset: String? = null
)

/**
 * Real watermark renderer using android.graphics.Canvas / Paint.
 *
 * Supports three modes:
 *  - [WatermarkType.TEXT]: draws text with opacity, color, shadow, border and rotation.
 *  - [WatermarkType.IMAGE]: composites a PNG logo with the configured opacity.
 *  - [WatermarkType.TEXT_WITH_LOGO]: draws the logo to the left of the text and the
 *    text next to it, as a single grouped element positioned per [WatermarkConfig.position].
 */
class WatermarkService {

    /**
     * Apply the watermark to [bitmap] and return a new (or the same, if mutable) bitmap
     * with the watermark burned in. The input bitmap is never mutated.
     */
    fun applyWatermark(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        if (!config.enabled) return bitmap
        val target = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(target)
        render(canvas, target.width, target.height, config)
        return target
    }

    /**
     * Render the watermark onto a downscaled copy of [bitmap] for fast live preview.
     * The preview is capped at [PREVIEW_MAX_DIM] px on the long edge.
     */
    fun previewWatermark(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        if (!config.enabled) return bitmap
        val scaled = downscaleForPreview(bitmap)
        return applyWatermark(scaled, config)
    }

    // ----------------------------------------------------------------
    // Rendering
    // ----------------------------------------------------------------

    private fun render(canvas: Canvas, width: Int, height: Int, config: WatermarkConfig) {
        val scale = width / REFERENCE_WIDTH.coerceAtLeast(1f)
        val fontSizePx = config.fontSize * scale
        val marginPx = config.margin * scale

        val alpha = (config.opacity.coerceIn(0f, 1f) * 255).toInt()

        when (config.type) {
            WatermarkType.TEXT -> renderText(canvas, width, height, config, fontSizePx, marginPx, alpha)
            WatermarkType.IMAGE -> renderImage(canvas, width, height, config, marginPx, alpha)
            WatermarkType.TEXT_WITH_LOGO -> renderTextWithLogo(canvas, width, height, config, fontSizePx, marginPx, alpha)
        }
    }

    // ---------------- TEXT ----------------

    private fun renderText(
        canvas: Canvas, width: Int, height: Int,
        config: WatermarkConfig, fontSizePx: Float, marginPx: Float, alpha: Int
    ) {
        val text = config.text
        if (text.isBlank()) return

        val paint = buildTextPaint(config, fontSizePx, alpha)
        val textWidth = paint.measureText(text)
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val box = computeBox(textWidth, textHeight, width, height, marginPx, config.position)

        drawTextWithEffects(canvas, text, box, textHeight, paint, config, alpha)
    }

    // ---------------- IMAGE ----------------

    private fun renderImage(
        canvas: Canvas, width: Int, height: Int,
        config: WatermarkConfig, marginPx: Float, alpha: Int
    ) {
        val logo = config.imagePath?.let { loadLogo(it) } ?: return
        // Scale the logo to 20% of the target width by default, keep aspect ratio.
        val targetW = width * LOGO_RELATIVE_SIZE
        val scale = targetW / logo.width
        val targetH = logo.height * scale
        val box = computeBox(targetW, targetH, width, height, marginPx, config.position)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        drawWithRotation(canvas, box, config.rotation) { c ->
            c.drawBitmap(logo, null, RectF(box.left, box.top, box.left + box.width, box.top + box.height), paint)
        }
        logo.recycle()
    }

    // ---------------- TEXT + LOGO ----------------

    private fun renderTextWithLogo(
        canvas: Canvas, width: Int, height: Int,
        config: WatermarkConfig, fontSizePx: Float, marginPx: Float, alpha: Int
    ) {
        val text = config.text
        val logo = config.imagePath?.let { loadLogo(it) }

        val textPaint = buildTextPaint(config, fontSizePx, alpha)
        val textWidth = if (text.isNotBlank()) textPaint.measureText(text) else 0f
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val gap = fontSizePx * 0.4f
        val logoSize = textHeight // logo height matches text height

        val logoW = if (logo != null) {
            logo.width / logo.height.toFloat() * logoSize
        } else 0f
        val totalW = logoW + (if (logoW > 0f) gap else 0f) + textWidth
        val totalH = maxOf(textHeight, logoSize)

        val box = computeBox(totalW, totalH, width, height, marginPx, config.position)

        drawWithRotation(canvas, box, config.rotation) { c ->
            var cursorX = box.left
            if (logo != null) {
                val dst = RectF(cursorX, box.top + (totalH - logoSize) / 2f, cursorX + logoW, box.top + (totalH - logoSize) / 2f + logoSize)
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
                c.drawBitmap(logo, null, dst, p)
                cursorX += logoW + gap
                logo.recycle()
            }
            if (text.isNotBlank()) {
                val textBox = Box(cursorX, box.top, textWidth, textHeight)
                drawTextOnly(c, text, textBox, textHeight, textPaint, config, alpha)
            }
        }
    }

    // ----------------------------------------------------------------
    // Paint / drawing helpers
    // ----------------------------------------------------------------

    private fun buildTextPaint(config: WatermarkConfig, fontSizePx: Float, alpha: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx.coerceAtLeast(1f)
            color = config.color
            this.alpha = alpha
            typeface = Typeface.DEFAULT
            isFakeBoldText = true
            if (config.hasShadow) {
                // setShadowLayer requires a layer to take effect; radius relative to font size.
                setShadowLayer(fontSizePx * 0.12f, fontSizePx * 0.05f, fontSizePx * 0.05f, Color.argb((alpha * 0.6f).toInt().coerceAtLeast(0), 0, 0, 0))
            }
        }
    }

    private fun drawTextWithEffects(
        canvas: Canvas, text: String, box: Box, textHeight: Float,
        paint: Paint, config: WatermarkConfig, alpha: Int
    ) {
        drawWithRotation(canvas, box, config.rotation) { c ->
            drawTextOnly(c, text, box, textHeight, paint, config, alpha)
        }
    }

    private fun drawTextOnly(
        canvas: Canvas, text: String, box: Box, textHeight: Float,
        paint: Paint, config: WatermarkConfig, alpha: Int
    ) {
        // Border around text bounds
        if (config.hasBorder) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = paint.textSize * 0.06f
                color = config.color
                this.alpha = alpha
            }
            val pad = paint.textSize * 0.15f
            canvas.drawRect(
                RectF(box.left - pad, box.top - pad, box.left + box.width + pad, box.top + box.height + pad),
                borderPaint
            )
        }
        // Baseline = top - ascent (fontMetrics.ascent is negative)
        val fm = paint.fontMetrics
        val baseline = box.top - fm.ascent
        canvas.drawText(text, box.left, baseline, paint)
    }

    private inline fun drawWithRotation(canvas: Canvas, box: Box, rotation: Float, block: (Canvas) -> Unit) {
        if (rotation == 0f) {
            block(canvas)
            return
        }
        val saveCount = canvas.save()
        val cx = box.left + box.width / 2f
        val cy = box.top + box.height / 2f
        canvas.rotate(rotation, cx, cy)
        block(canvas)
        canvas.restoreToCount(saveCount)
    }

    /**
     * Computes the top-left placement box for an element of size [w]x[h] given the
     * configured [position] and [margin] from the canvas edges.
     */
    private fun computeBox(
        w: Float, h: Float, canvasW: Int, canvasH: Int,
        margin: Float, position: WatermarkPosition
    ): Box {
        val left = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> margin
            WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> canvasW - margin - w
            WatermarkPosition.CENTER, WatermarkPosition.BOTTOM_CENTER -> (canvasW - w) / 2f
        }
        val top = when (position) {
            WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> margin
            WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT, WatermarkPosition.BOTTOM_CENTER -> canvasH - margin - h
            WatermarkPosition.CENTER -> (canvasH - h) / 2f
        }
        return Box(left, top, w, h)
    }

    // ----------------------------------------------------------------
    // Logo loading
    // ----------------------------------------------------------------

    private fun loadLogo(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            val maxDim = 512 // Logo images don't need more than 512px
            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxDim)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)?.takeIf { !it.isRecycled }
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        val maxEdge = maxOf(width, height)
        while (maxEdge / sampleSize > maxDim) {
            sampleSize *= 2
        }
        return sampleSize
    }

    // ----------------------------------------------------------------
    // Preview downscaling
    // ----------------------------------------------------------------

    private fun downscaleForPreview(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= PREVIEW_MAX_DIM) return bitmap
        val scale = PREVIEW_MAX_DIM.toFloat() / longEdge
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private data class Box(val left: Float, val top: Float, val width: Float, val height: Float)

    companion object {
        private const val REFERENCE_WIDTH = 1080f
        private const val PREVIEW_MAX_DIM = 720
        private const val LOGO_RELATIVE_SIZE = 0.2f
    }
}
