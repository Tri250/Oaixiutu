package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipelineService {

    private val nativeBridge = NativePipelineBridge()

    suspend fun applyPipeline(bitmap: Bitmap, params: PipelineParams): Bitmap = withContext(Dispatchers.Default) {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap

        // Apply adjustments in order
        result = applyExposure(result, params.exposure)
        result = applyContrast(result, params.contrast)
        result = applySaturation(result, params.saturation)
        result = applyWhiteBalance(result, params.whiteBalanceTemp, params.whiteBalanceTint)
        result = applyHighlightsShadows(result, params.highlights, params.shadows)
        result = applySharpen(result, params.sharpenAmount)
        result = applyVibrance(result, params.vibrance)

        // Geometry
        if (params.geometryRotate != 0f || params.geometryCropLeft != 0f ||
            params.geometryCropTop != 0f || params.geometryCropRight != 1f ||
            params.geometryCropBottom != 1f
        ) {
            result = applyCropRotate(result, params)
        }

        result
    }

    private fun applyExposure(bitmap: Bitmap, exposure: Float): Bitmap {
        if (exposure == 0f) return bitmap
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                1f, 0f, 0f, 0f, exposure * 25f,
                0f, 1f, 0f, 0f, exposure * 25f,
                0f, 0f, 1f, 0f, exposure * 25f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, matrix)
    }

    private fun applyContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        if (contrast == 0f) return bitmap
        val scale = 1f + contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, matrix)
    }

    private fun applySaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        if (saturation == 0f) return bitmap
        val matrix = ColorMatrix().apply {
            setSaturation(1f + saturation)
        }
        return applyColorMatrix(bitmap, matrix)
    }

    private fun applyWhiteBalance(bitmap: Bitmap, temp: Float, tint: Float): Bitmap {
        // Simplified white balance using color matrix
        val tempScale = temp / 6500f
        val matrix = ColorMatrix().apply {
            set(floatArrayOf(
                tempScale.coerceIn(0.5f, 2f), 0f, 0f, 0f, tint * 5f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, (2f - tempScale).coerceIn(0.5f, 2f), 0f, -tint * 5f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, matrix)
    }

    private fun applyHighlightsShadows(bitmap: Bitmap, highlights: Float, shadows: Float): Bitmap {
        if (highlights == 0f && shadows == 0f) return bitmap
        val matrix = ColorMatrix().apply {
            // Simplified tone curve approximation
            set(floatArrayOf(
                1f + highlights * 0.2f, 0f, 0f, 0f, shadows * 15f,
                0f, 1f + highlights * 0.2f, 0f, 0f, shadows * 15f,
                0f, 0f, 1f + highlights * 0.2f, 0f, shadows * 15f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(bitmap, matrix)
    }

    private fun applySharpen(bitmap: Bitmap, amount: Float): Bitmap {
        // Placeholder: real sharpen requires convolution
        return bitmap
    }

    private fun applyVibrance(bitmap: Bitmap, vibrance: Float): Bitmap {
        return applySaturation(bitmap, vibrance * 0.5f)
    }

    private fun applyCropRotate(bitmap: Bitmap, params: PipelineParams): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val cropLeft = (params.geometryCropLeft * width).toInt()
        val cropTop = (params.geometryCropTop * height).toInt()
        val cropRight = (params.geometryCropRight * width).toInt()
        val cropBottom = (params.geometryCropBottom * height).toInt()

        val cropped = Bitmap.createBitmap(
            bitmap,
            cropLeft.coerceIn(0, width),
            cropTop.coerceIn(0, height),
            (cropRight - cropLeft).coerceAtLeast(1),
            (cropBottom - cropTop).coerceAtLeast(1)
        )

        return if (params.geometryRotate != 0f) {
            val matrix = android.graphics.Matrix().apply {
                postRotate(params.geometryRotate)
            }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else cropped
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isAntiAlias = true
            isDither = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
