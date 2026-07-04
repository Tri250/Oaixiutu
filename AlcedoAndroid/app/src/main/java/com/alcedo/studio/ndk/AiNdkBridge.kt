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

    external fun nativeClipEncodeImage(imagePixels: FloatArray, width: Int, height: Int): FloatArray
    external fun nativeClipEncodeText(text: String): FloatArray
    external fun nativeClipComputeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float
    external fun nativeClipZeroShotClassify(imagePixels: FloatArray, width: Int, height: Int, labels: Array<String>): FloatArray
    external fun nativeBuildHnswIndex(embeddings: Array<FloatArray>, ids: LongArray): Boolean
    external fun nativeHnswSearch(query: FloatArray, topK: Int): LongArray

    fun encodeImage(imagePixels: FloatArray, width: Int, height: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("encodeImage") {
            nativeClipEncodeImage(imagePixels, width, height)
        }
    }

    fun encodeText(text: String): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("encodeText") {
            nativeClipEncodeText(text)
        }
    }

    fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (!isAvailable) return 0f
        return NdkSafeCall.executeFloat("computeSimilarity") {
            nativeClipComputeSimilarity(embedding1, embedding2)
        }
    }

    fun zeroShotClassify(imagePixels: FloatArray, width: Int, height: Int, labels: Array<String>): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("zeroShotClassify") {
            nativeClipZeroShotClassify(imagePixels, width, height, labels)
        }
    }

    fun buildHnswIndex(embeddings: Array<FloatArray>, ids: LongArray): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("buildHnswIndex") {
            nativeBuildHnswIndex(embeddings, ids)
        }
    }

    fun hnswSearch(query: FloatArray, topK: Int): LongArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("hnswSearch") {
            nativeHnswSearch(query, topK)
        }
    }
}
