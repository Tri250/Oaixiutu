package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UltraHDR gain-map writer. Wraps the native ultra_hdr_writer
 * (core/io/ultra_hdr_writer.cpp) to produce JPEG_R (UltraHDR) images from a
 * primary SDR JPEG plus an HDR gain map, per the Android 14 spec.
 */
@Singleton
class UltraHdrWriter @Inject constructor() {

    /** Write an UltraHDR JPEG from [primaryPath] + [gainmapPath] to [outputPath]. */
    suspend fun write(primaryPath: String, gainmapPath: String, outputPath: String): Boolean =
        withContext(ThreadPool.compute) {
            if (!File(primaryPath).exists() || !File(gainmapPath).exists()) {
                Log.w(TAG, "missing primary or gainmap input")
                return@withContext false
            }
            NdkSafeCall.call(default = false) {
                AlcedoNativeBridge.nativeWriteUltraHdr(primaryPath, gainmapPath, outputPath)
            }
        }

    /**
     * Build a gain map bitmap from an HDR-rendered [hdrBitmap] and the SDR
     * [sdrBitmap]. Returns the gain map as a compressed JPEG byte array, or
     * null. The native writer expects a single-channel-ish gain map.
     */
    fun buildGainMapBytes(sdrBitmap: Bitmap, hdrBitmap: Bitmap, maxBoost: Float = 3.5f): ByteArray? {
        if (sdrBitmap.width != hdrBitmap.width || sdrBitmap.height != hdrBitmap.height) {
            Log.w(TAG, "sdr/hdr dimension mismatch")
            return null
        }
        val w = sdrBitmap.width
        val h = sdrBitmap.height
        val sdrPx = IntArray(w * h)
        val hdrPx = IntArray(w * h)
        sdrBitmap.getPixels(sdrPx, 0, w, 0, 0, w, h)
        hdrBitmap.getPixels(hdrPx, 0, w, 0, 0, w, h)
        val gainmap = IntArray(w * h)
        val log2 = kotlin.math.ln(2.0)
        for (i in sdrPx.indices) {
            val sdr = luminance(sdrPx[i]) / 255f
            val hdr = luminance(hdrPx[i]) / 255f
            val ratio = if (sdr > 1e-4f) (hdr / sdr).coerceIn(1f, maxBoost) else 1f
            val gain = (kotlin.math.ln(ratio.toDouble()) / log2 / maxBoost).toFloat().coerceIn(0f, 1f)
            val g = (gain * 255f).toInt().coerceIn(0, 255)
            gainmap[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(gainmap, 0, w, 0, 0, w, h)
        val bos = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        out.recycle()
        return bos.toByteArray()
    }

    private fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    companion object {
        private const val TAG = "UltraHdrWriter"
    }
}
