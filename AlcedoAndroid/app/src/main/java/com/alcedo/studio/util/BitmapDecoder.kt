package com.alcedo.studio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * Efficient bitmap decoding utilities. Provides size-bounded decoding from [Uri],
 * [InputStream] or file paths with EXIF orientation correction and in-flight
 * memory budgets derived from a target pixel cap.
 */
object BitmapDecoder {

    private const val TAG = "BitmapDecoder"

    /**
     * Decode a bitmap from [uri] bounded by [maxDim] (the largest of width/height)
     * while honouring EXIF orientation.
     */
    fun decodeSampled(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return null
            applyExifOrientation(context, uri, raw)
        }.onFailure { Log.w(TAG, "decodeSampled failed for $uri", it) }.getOrNull()
    }

    /** Decode a thumbnail-sized bitmap suitable for grid display. */
    fun decodeThumbnail(context: Context, uri: Uri, targetSize: Int = 320): Bitmap? =
        decodeSampled(context, uri, targetSize)

    fun decodeStream(input: InputStream, maxDim: Int = 2048): Bitmap? {
        val marksSupported = input.markSupported()
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (marksSupported) {
                input.mark(Int.MAX_VALUE)
                BitmapFactory.decodeStream(input, null, bounds)
                input.reset()
            } else {
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0) return null
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            if (marksSupported) {
                input.mark(Int.MAX_VALUE)
                val bmp = BitmapFactory.decodeStream(input, null, opts)
                input.reset()
                bmp
            } else {
                BitmapFactory.decodeStream(input, null, opts)
            }
        }.onFailure { Log.w(TAG, "decodeStream failed", it) }.getOrNull()
    }

    /**
     * Decode a bitmap via [ImageDecoder] (Android P+) for full-resolution images
     * with accurate color management.
     */
    fun decodeFull(context: Context, uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
        }
    }.onFailure { Log.w(TAG, "decodeFull failed for $uri", it) }.getOrNull()

    /** Compute the largest inSampleSize that keeps the decoded dim >= [maxDim]. */
    fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxDim) sample *= 2
        return sample
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                rotateOrFlip(bitmap, orientation)
            } ?: bitmap
        }.getOrElse { bitmap }
    }

    private fun rotateOrFlip(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Estimate the byte size of a bitmap with the given config. */
    fun estimateBytes(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Long {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ALPHA_8 -> 1L
            Bitmap.Config.RGB_565, Bitmap.Config.ARGB_4444 -> 2L
            Bitmap.Config.RGBA_F16 -> 8L
            else -> 4L
        }
        return width.toLong() * height.toLong() * bytesPerPixel
    }
}
