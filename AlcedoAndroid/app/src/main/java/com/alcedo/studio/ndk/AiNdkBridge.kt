package com.alcedo.studio.ndk

import android.util.Log

object AiNdkBridge {
    private const val TAG = "AiNdkBridge"

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

    fun encodeImage(imagePixels: FloatArray, width: Int, height: Int): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "encodeImage: AI inference not yet available via NDK bridge")
        return null
    }

    fun encodeText(text: String): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "encodeText: AI inference not yet available via NDK bridge")
        return null
    }

    fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (!isAvailable) return 0f
        // Cosine similarity in Kotlin
        if (embedding1.size != embedding2.size) return 0f
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (i in embedding1.indices) {
            dotProduct += (embedding1[i] * embedding2[i]).toDouble()
            norm1 += (embedding1[i] * embedding1[i]).toDouble()
            norm2 += (embedding2[i] * embedding2[i]).toDouble()
        }
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }

    fun zeroShotClassify(imagePixels: FloatArray, width: Int, height: Int, labels: Array<String>): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "zeroShotClassify: AI inference not yet available via NDK bridge")
        return null
    }

    fun buildHnswIndex(embeddings: Array<FloatArray>, ids: LongArray): Boolean {
        if (!isAvailable) return false
        Log.w(TAG, "buildHnswIndex: AI inference not yet available via NDK bridge")
        return false
    }

    fun hnswSearch(query: FloatArray, topK: Int): LongArray? {
        if (!isAvailable) return null
        Log.w(TAG, "hnswSearch: AI inference not yet available via NDK bridge")
        return null
    }
}
