package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.MemoryGuard
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes images for LLM vision input. Produces a base64 JPEG (data URI or
 * raw) sized to fit common vision-model token budgets while staying within
 * memory limits. Mirrors core/app/image_analysis_encoder.
 */
@Singleton
class ImageAnalysisEncoder @Inject constructor(
    private val memoryGuard: MemoryGuard,
) {

    /** Encode [uri] to a base64 JPEG string at most [maxDim] px, quality [quality]. */
    suspend fun encodeThumbnail(uri: Uri, maxDim: Int = 768, quality: Int = 80): String? =
        withContext(ThreadPool.aiInference) {
            val safeDim = maxDim.coerceIn(256, memoryGuard.suggestedMaxDim())
            val bitmap = BitmapDecoder.decodeSampled(ContextProvider.requireContext(), uri, safeDim)
                ?: return@withContext null
            try {
                val (w, h) = downscale(bitmap, safeDim)
                val scaled = if (w == bitmap.width && h == bitmap.height) bitmap
                else Bitmap.createScaledBitmap(bitmap, w, h, true)
                val bytes = ByteArrayOutputStream().use { baos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    baos.toByteArray()
                }
                if (scaled !== bitmap) scaled.recycle()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        }

    /** Encode a bitmap directly. */
    fun encodeBitmap(bitmap: Bitmap, quality: Int = 80): String {
        val bytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            baos.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Wrap a base64 string as a data URI suitable for OpenAI vision payloads. */
    fun toDataUri(base64: String, mime: String = "image/jpeg"): String = "data:$mime;base64,$base64"

    /** Compute a downscaled (w, h) preserving aspect ratio within [maxDim]. */
    private fun downscale(bitmap: Bitmap, maxDim: Int): Pair<Int, Int> {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap.width to bitmap.height
        val scale = maxDim.toFloat() / longest
        return (bitmap.width * scale).toInt().coerceAtLeast(1) to
            (bitmap.height * scale).toInt().coerceAtLeast(1)
    }

    companion object {
        private const val TAG = "ImageAnalysisEncoder"
    }
}
