package com.alcedo.studio.ndk

import android.util.Log

object AlcedoNdkBridge {
    private const val TAG = "AlcedoNdkBridge"

    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("alcedo")
            isAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isAvailable = false
        }
    }

    external fun stringFromJNI(): String

    external fun nativeInitialize()

    external fun nativeGenerateId(): Long

    external fun nativeGetTimestampMillis(): Long

    external fun nativeGetTimestampMicros(): Long

    external fun nativeSetLogLevel(level: Int)

    fun initialize() {
        if (!isAvailable) return
        NdkSafeCall.execute("initialize") {
            nativeInitialize()
        }
    }

    fun generateId(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("generateId") {
            nativeGenerateId()
        } ?: 0L
    }

    fun getTimestampMillis(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("getTimestampMillis") {
            nativeGetTimestampMillis()
        } ?: 0L
    }

    fun getTimestampMicros(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("getTimestampMicros") {
            nativeGetTimestampMicros()
        } ?: 0L
    }

    fun setLogLevel(level: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("setLogLevel") {
            nativeSetLogLevel(level)
        }
    }

    // ── Pipeline Processing ──

    fun processPipeline(
        pixels: FloatArray,
        width: Int,
        height: Int,
        channels: Int,
        exposure: Float,
        contrast: Float,
        highlights: Float,
        shadows: Float,
        saturation: Float,
        vibrance: Float,
        clarity: Float,
        sharpen: Float
    ): Boolean {
        if (!isAvailable) return false
        Log.w(TAG, "processPipeline: AI inference not yet available via NDK bridge")
        return false
    }
}
