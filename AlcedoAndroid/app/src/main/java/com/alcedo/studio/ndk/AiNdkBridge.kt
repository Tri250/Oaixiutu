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

    // ── HNSW Index Management ──

    fun hnswCreateIndex(dimension: Int): Long {
        if (!isAvailable) return 0L
        Log.w(TAG, "hnswCreateIndex: AI inference not yet available via NDK bridge")
        return 0L
    }

    fun hnswSize(indexHandle: Long): Int {
        if (!isAvailable) return 0
        Log.w(TAG, "hnswSize: AI inference not yet available via NDK bridge")
        return 0
    }

    fun hnswSearch(indexHandle: Long, query: FloatArray, topK: Int): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "hnswSearch(idx): AI inference not yet available via NDK bridge")
        return null
    }

    fun hnswInsert(indexHandle: Long, id: Long, embedding: FloatArray) {
        if (!isAvailable) return
        Log.w(TAG, "hnswInsert: AI inference not yet available via NDK bridge")
    }

    fun hnswRemove(indexHandle: Long, id: Long) {
        if (!isAvailable) return
        Log.w(TAG, "hnswRemove: AI inference not yet available via NDK bridge")
    }

    fun normalizeEmbedding(embedding: FloatArray) {
        var norm = 0.0
        for (v in embedding) norm += (v * v).toDouble()
        val length = kotlin.math.sqrt(norm)
        if (length > 0) {
            for (i in embedding.indices) {
                embedding[i] = (embedding[i] / length).toFloat()
            }
        }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (i in a.indices) {
            dotProduct += (a[i] * b[i]).toDouble()
            norm1 += (a[i] * a[i]).toDouble()
            norm2 += (b[i] * b[i]).toDouble()
        }
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }

    // ── CLIP Session Management ──

    fun clipCreateSession(modelPath: String, imageSize: Int, embeddingDim: Int): Long {
        if (!isAvailable) return 0L
        Log.w(TAG, "clipCreateSession: AI inference not yet available via NDK bridge")
        return 0L
    }

    fun clipIsLoaded(sessionHandle: Long): Boolean {
        if (!isAvailable) return false
        Log.w(TAG, "clipIsLoaded: AI inference not yet available via NDK bridge")
        return false
    }

    fun clipEncodeImage(sessionHandle: Long, rgbBytes: ByteArray, width: Int, height: Int): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "clipEncodeImage: AI inference not yet available via NDK bridge")
        return null
    }

    fun clipEncodeText(sessionHandle: Long, text: String): FloatArray? {
        if (!isAvailable) return null
        Log.w(TAG, "clipEncodeText: AI inference not yet available via NDK bridge")
        return null
    }

    fun clipDestroySession(sessionHandle: Long) {
        if (!isAvailable) return
        Log.w(TAG, "clipDestroySession: AI inference not yet available via NDK bridge")
    }
}
