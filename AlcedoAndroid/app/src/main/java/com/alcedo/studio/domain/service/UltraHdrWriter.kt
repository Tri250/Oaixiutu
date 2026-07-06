package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/**
 * Ultra HDR JPEG writer using Android 14+ Gainmap API.
 *
 * On API 34+ (Android 14, UPSIDE_DOWN_CAKE) the writer attaches a [android.graphics.Gainmap]
 * to the SDR base bitmap and relies on [Bitmap.compress] to emit a standard Ultra HDR
 * JPEG (SDR base image + ISO 21496-1 gain map). On older devices it gracefully degrades
 * to a tone-mapped standard JPEG.
 *
 * The gain map encodes the per-pixel ratio between the HDR and SDR representations,
 * clamped to [1.0, RATIO_MAX]. A weight of 0 means "no HDR boost" (gain = ratioMin)
 * and a weight of 1 means "maximum HDR boost" (gain = ratioMax), following the
 * ISO 21496-1 gain-map formula: gain = ratioMin * (ratioMax / ratioMin) ^ w.
 */
class UltraHdrWriter private constructor(private val context: Context) {

    fun writeUltraHdr(
        sdrBitmap: Bitmap,
        hdrBitmap: Bitmap,
        outputPath: String,
        quality: Int = 95
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                writeUltraHdrApi34(sdrBitmap, hdrBitmap, outputPath, quality)
            } else {
                writeFallbackHdr(sdrBitmap, outputPath, quality)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ultra HDR write failed", e)
            false
        }
    }

    /**
     * Returns `true` when this device can emit a real Ultra HDR JPEG (API 34+).
     * On older devices the writer falls back to a standard JPEG.
     */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun writeUltraHdrApi34(
        sdrBitmap: Bitmap,
        hdrBitmap: Bitmap,
        outputPath: String,
        quality: Int
    ): Boolean {
        val q = quality.coerceIn(1, 100)

        // 1. Compress the SDR bitmap to JPEG bytes. This becomes the base image
        //    of the Ultra HDR JPEG. We then decode it back to obtain a fresh,
        //    mutable bitmap that we own and can attach a gainmap to (the caller's
        //    bitmap must not be mutated and may be immutable).
        val sdrJpegBytes = ByteArrayOutputStream().use { baos ->
            sdrBitmap.compress(Bitmap.CompressFormat.JPEG, q, baos)
            baos.toByteArray()
        }

        val decodeOptions = BitmapFactory.Options().apply { inMutable = true }
        val baseBitmap = BitmapFactory.decodeByteArray(
            sdrJpegBytes, 0, sdrJpegBytes.size, decodeOptions
        ) ?: run {
            Log.e(TAG, "Failed to decode SDR base JPEG")
            return false
        }

        // 2. Build the gain-map bitmap from the SDR/HDR luminance ratio.
        val gainmapBitmap = createGainmap(baseBitmap, hdrBitmap)

        // 3. Wrap the gain-map bitmap with ISO 21496-1 metadata. The Gainmap
        //    setters take per-channel (R, G, B) float values; for a single-plane
        //    gainmap all three channels must be identical.
        val gainmap = android.graphics.Gainmap(gainmapBitmap)
        gainmap.setRatioMax(RATIO_MAX, RATIO_MAX, RATIO_MAX)
        gainmap.setRatioMin(RATIO_MIN, RATIO_MIN, RATIO_MIN)
        gainmap.setGamma(GAMMA, GAMMA, GAMMA)
        gainmap.setEpsilonSdr(EPSILON, EPSILON, EPSILON)
        gainmap.setEpsilonHdr(EPSILON, EPSILON, EPSILON)

        // 4. Attach the gainmap to the base SDR bitmap.
        baseBitmap.setGainmap(gainmap)

        // 5. Compress to JPEG. On API 34+, Bitmap.compress(JPEG) automatically
        //    writes the Ultra HDR format (SDR + embedded gain map) when the
        //    source bitmap has an attached gainmap.
        FileOutputStream(outputPath).use { fos ->
            baseBitmap.compress(Bitmap.CompressFormat.JPEG, q, fos)
        }

        gainmapBitmap.recycle()
        baseBitmap.recycle()

        Log.i(
            TAG,
            "Ultra HDR JPEG written to $outputPath " +
                "(${sdrBitmap.width}x${sdrBitmap.height}, quality=$q)"
        )
        return true
    }

    /**
     * Create a gain-map bitmap that represents the HDR/SDR luminance ratio.
     *
     * The gain map is stored as an [Bitmap.Config.ALPHA_8] bitmap; the gain-map
     * reader consumes the alpha channel as the per-pixel weight `w` in [0, 1].
     * With ratioMin=1, ratioMax=RATIO_MAX and gamma=1, the applied gain is
     * `1.0 * (RATIO_MAX / 1.0) ^ w`, so a weight of 0 leaves the SDR pixel
     * unchanged and a weight of 1 boosts it by RATIO_MAX.
     */
    private fun createGainmap(sdrBitmap: Bitmap, hdrBitmap: Bitmap): Bitmap {
        val width = sdrBitmap.width
        val height = sdrBitmap.height

        val sdrScaled = if (sdrBitmap.width != width || sdrBitmap.height != height) {
            Bitmap.createScaledBitmap(sdrBitmap, width, height, true)
        } else sdrBitmap

        val hdrScaled = if (hdrBitmap.width != width || hdrBitmap.height != height) {
            Bitmap.createScaledBitmap(hdrBitmap, width, height, true)
        } else hdrBitmap

        val sdrPixels = IntArray(width * height)
        val hdrPixels = IntArray(width * height)
        sdrScaled.getPixels(sdrPixels, 0, width, 0, 0, width, height)
        hdrScaled.getPixels(hdrPixels, 0, width, 0, 0, width, height)

        val gainmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val gainmapPixels = IntArray(width * height)

        for (i in sdrPixels.indices) {
            val sdrLum = (0.299f * ((sdrPixels[i] shr 16) and 0xFF) +
                0.587f * ((sdrPixels[i] shr 8) and 0xFF) +
                0.114f * (sdrPixels[i] and 0xFF)) / 255f

            val hdrLum = (0.299f * ((hdrPixels[i] shr 16) and 0xFF) +
                0.587f * ((hdrPixels[i] shr 8) and 0xFF) +
                0.114f * (hdrPixels[i] and 0xFF)) / 255f

            val gain = if (sdrLum > 0.001f) {
                (hdrLum / sdrLum).coerceIn(RATIO_MIN, RATIO_MAX)
            } else {
                RATIO_MIN
            }

            // Map gain [RATIO_MIN, RATIO_MAX] to alpha [0, 255].
            val alpha = ((gain - RATIO_MIN) / (RATIO_MAX - RATIO_MIN) * 255f)
                .toInt()
                .coerceIn(0, 255)
            gainmapPixels[i] = alpha shl 24
        }

        gainmap.setPixels(gainmapPixels, 0, width, 0, 0, width, height)

        if (sdrScaled !== sdrBitmap) sdrScaled.recycle()
        if (hdrScaled !== hdrBitmap) hdrScaled.recycle()

        return gainmap
    }

    /**
     * Fallback for devices below Android 14: write a standard JPEG.
     * (Graceful degradation — the gain-map path simply is not available.)
     */
    private fun writeFallbackHdr(sdrBitmap: Bitmap, outputPath: String, quality: Int): Boolean {
        Log.w(
            TAG,
            "Ultra HDR not supported on API ${Build.VERSION.SDK_INT}, " +
                "falling back to standard JPEG"
        )
        FileOutputStream(outputPath).use { fos ->
            sdrBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fos)
        }
        return true
    }

    companion object {
        private const val TAG = "UltraHdrWriter"

        /** Maximum HDR/SDR gain ratio (typical mobile HDR headroom). */
        private const val RATIO_MAX = 4.0f

        /** Minimum HDR/SDR gain ratio (no boost). */
        private const val RATIO_MIN = 1.0f

        /** Gain-map gamma. 1.0 makes the weight linear in the applied gain. */
        private const val GAMMA = 1.0f

        /** Epsilon used to avoid divide-by-zero at very dark pixels. */
        private const val EPSILON = 0.0f

        fun create(context: Context): UltraHdrWriter = UltraHdrWriter(context.applicationContext)
    }
}
