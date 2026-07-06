package com.alcedo.studio.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Memory guard utilities for preventing OOM in image-heavy workflows.
 *
 * Provides:
 * - Available memory estimation (taking into account runtime.maxMemory() / ActivityManager)
 * - Safe bitmap allocation checks before decoding large images
 * - Memory pressure level detection for adaptive quality
 */
object MemoryGuard {
    private const val TAG = "MemoryGuard"

    // Safety margin: never use more than 60% of the available heap for a single bitmap
    private const val BITMAP_HEAP_RATIO = 0.6f

    // Conservative overhead for non-bitmap allocations (in bytes)
    private const val RUNTIME_OVERHEAD_BYTES = 32L * 1024 * 1024 // 32 MB

    /**
     * Estimates the number of bytes currently available for allocation.
     * Uses Runtime.maxMemory() - Runtime.totalMemory() + Runtime.freeMemory()
     * which is the standard formula for available heap in Dalvik/ART.
     */
    fun availableHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
    }

    /**
     * Checks whether the app has enough heap headroom to allocate a bitmap of
     * the given [byteCount] without risking OOM.
     *
     * Returns true if:
     *   availableHeap >= byteCount + RUNTIME_OVERHEAD_BYTES
     * AND
     *   byteCount <= BITMAP_HEAP_RATIO * maxMemory
     */
    fun canAllocateBitmap(byteCount: Long): Boolean {
        val available = availableHeapBytes()
        val maxMemory = Runtime.getRuntime().maxMemory()
        val withinBudget = byteCount <= (BITMAP_HEAP_RATIO * maxMemory).toLong()
        val hasHeadroom = available >= byteCount + RUNTIME_OVERHEAD_BYTES
        if (!withinBudget || !hasHeadroom) {
            Log.w(TAG, "Bitmap allocation rejected: bytes=$byteCount, " +
                    "available=${available / 1024 / 1024}MB, " +
                    "maxHeap=${maxMemory / 1024 / 1024}MB, " +
                    "withinBudget=$withinBudget, hasHeadroom=$hasHeadroom")
        }
        return withinBudget && hasHeadroom
    }

    /**
     * Estimates the byte count for a bitmap with the given dimensions and config.
     */
    fun estimateBitmapBytes(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Long {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ARGB_8888 -> 4L
            Bitmap.Config.RGB_565 -> 2L
            Bitmap.Config.ALPHA_8 -> 1L
            else -> 4L
        }
        return width.toLong() * height.toLong() * bytesPerPixel
    }

    /**
     * Calculates a safe inSampleSize for BitmapFactory that ensures the decoded
     * bitmap does not exceed [maxBytes].
     */
    fun calculateSafeSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        maxBytes: Long,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Int {
        var sampleSize = 1
        while (estimateBitmapBytes(imageWidth / sampleSize, imageHeight / sampleSize, config) > maxBytes) {
            sampleSize *= 2
            if (sampleSize > 64) break // Cap at 64x downsampling
        }
        return sampleSize
    }

    /**
     * Returns the current memory pressure level as reported by the system.
     * Wraps ActivityManager.MemoryInfo if available; otherwise returns a safe default.
     */
    fun getMemoryPressureLevel(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return when {
            memInfo.lowMemory -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
            memInfo.availMem < memInfo.threshold * 2 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND
            else -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    /**
     * Suggests a maximum pixel count for image processing based on current memory state.
     * Returns a value that can be used as PREVIEW_MAX_PIXELS or MAX_BITMAP_PIXELS.
     */
    fun suggestedMaxPixels(context: Context): Int {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val pressure = getMemoryPressureLevel(context)
        val baseRatio = when (pressure) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> 0.15f
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND -> 0.25f
            else -> 0.4f
        }
        // float32 RGBA = 16 bytes per pixel
        val maxPixels = ((maxMemory * baseRatio) / 16f).toLong()
        // Clamp between 1M and 16M pixels
        return max(min(maxPixels, 16L * 1024 * 1024), 1024 * 1024).toInt()
    }

    /**
     * Tries to free memory by suggesting GC. Should only be called when
     * memory is critically low (e.g. after catching OutOfMemoryError).
     */
    fun emergencyGC() {
        Log.w(TAG, "Emergency GC triggered")
        System.gc()
        System.runFinalization()
        System.gc()
    }
}
