package com.alcedo.studio.ndk

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.domain.service.ClipInferenceEngine

/**
 * AI NDK 桥接器 — 委托给 Kotlin 实现的 ClipInferenceEngine
 *
 * 提供统一的 AI 推理接口，实际推理由 ONNX Runtime 执行；
 * HNSW 索引以纯 Kotlin 内存实现提供，避免依赖不存在的原生符号。
 */
object AiNdkBridge {
    private const val TAG = "AiNdkBridge"

    private var clipEngine: ClipInferenceEngine? = null

    // 简单的内存向量索引（HNSW 替代实现，按相似度暴力检索）
    private val indexEntries = mutableListOf<IndexEntry>()

    private class IndexEntry(val id: Long, val embedding: FloatArray)

    fun initialize(context: Context) {
        if (clipEngine == null) {
            clipEngine = ClipInferenceEngine(context)
        }
    }

    // ── 编码（委托给 ClipInferenceEngine）──

    suspend fun encodeImage(context: Context, bitmap: Bitmap): FloatArray? {
        return try {
            initialize(context)
            clipEngine?.encodeImage(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "图像编码失败", e)
            null
        }
    }

    suspend fun encodeText(context: Context, text: String): FloatArray? {
        return try {
            initialize(context)
            clipEngine?.encodeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "文本编码失败", e)
            null
        }
    }

    fun computeSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * 归一化嵌入向量：原地修改并返回同一数组，兼容旧调用方（丢弃返回值）与新调用方。
     */
    fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in embedding.indices) embedding[i] = embedding[i] / norm
        }
        return embedding
    }

    // ── 零样本分类 ──

    suspend fun zeroShotClassify(
        context: Context,
        bitmap: Bitmap,
        labels: List<String>
    ): List<Pair<String, Float>>? {
        val imgEmbedding = encodeImage(context, bitmap) ?: return null
        val labelEmbeddings = labels.map { label ->
            label to (encodeText(context, label) ?: return null)
        }
        return labelEmbeddings.map { (label, emb) ->
            label to cosineSimilarity(imgEmbedding, emb)
        }.sortedByDescending { it.second }
    }

    // ── HNSW 索引管理（内存实现）──

    fun buildHnswIndex(
        context: Context,
        embeddings: List<Pair<Long, FloatArray>>,
        dimensions: Int = 512,
        maxElements: Int = 10000
    ): Boolean {
        return try {
            indexEntries.clear()
            embeddings.forEach { (id, emb) ->
                indexEntries.add(IndexEntry(id, emb.copyOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "构建 HNSW 索引失败", e)
            false
        }
    }

    fun hnswSearch(query: FloatArray, k: Int = 10): List<Pair<Long, Float>>? {
        if (indexEntries.isEmpty()) return null
        return indexEntries
            .map { it.id to cosineSimilarity(query, it.embedding) }
            .sortedByDescending { it.second }
            .take(k)
    }

    fun hnswCreateIndex(dimensions: Int, maxElements: Int): Long {
        return try {
            indexEntries.clear()
            1L
        } catch (e: Exception) {
            Log.e(TAG, "创建 HNSW 索引失败", e)
            0L
        }
    }

    fun hnswSize(): Int = indexEntries.size

    fun hnswInsert(id: Long, embedding: FloatArray) {
        indexEntries.removeAll { it.id == id }
        indexEntries.add(IndexEntry(id, embedding.copyOf()))
    }

    fun hnswRemove(id: Long) {
        indexEntries.removeAll { it.id == id }
    }

    // ── CLIP 会话管理（委托给 ClipInferenceEngine）──

    fun clipCreateSession(context: Context): Long {
        return try {
            initialize(context)
            if (clipEngine != null) 1L else 0L
        } catch (e: Exception) {
            Log.e(TAG, "创建 CLIP 会话失败", e)
            0L
        }
    }

    fun clipIsLoaded(): Boolean = clipEngine?.isLoaded ?: false

    suspend fun clipEncodeImage(context: Context, bitmap: Bitmap): FloatArray? =
        encodeImage(context, bitmap)

    suspend fun clipEncodeText(context: Context, text: String): FloatArray? =
        encodeText(context, text)

    fun clipDestroySession() {
        clipEngine?.unloadModel()
        clipEngine = null
    }

    // ================================================================
    // 向后兼容重载：保留旧句柄式 API，委托到上述内存实现。
    // 旧调用方 service.AiService / domain.service.AiService 仍使用这些签名。
    // ================================================================

    fun hnswCreateIndex(dimension: Int): Long = hnswCreateIndex(dimension, 10000)

    fun hnswSize(indexHandle: Long): Int = hnswSize()

    /**
     * 旧句柄式检索：返回扁平 FloatArray [id, distance, id, distance, ...]，
     * 其中 distance = 1 - similarity，与 AiService.parseNativeSearchResults 约定一致。
     */
    fun hnswSearch(indexHandle: Long, query: FloatArray, topK: Int): FloatArray? {
        val results = hnswSearch(query, topK) ?: return null
        val packed = FloatArray(results.size * 2)
        for (i in results.indices) {
            packed[i * 2] = results[i].first.toFloat()
            packed[i * 2 + 1] = 1f - results[i].second
        }
        return packed
    }

    fun hnswInsert(indexHandle: Long, id: Long, embedding: FloatArray) {
        hnswInsert(id, embedding)
    }

    fun hnswRemove(indexHandle: Long, id: Long) {
        hnswRemove(id)
    }

    // 旧 CLIP 句柄式 API：未携带 Context，无法创建 ClipInferenceEngine，
    // 保留为空操作以维持编译兼容；调用方在返回 0L/false/null 时会回退到本地嵌入生成逻辑。
    fun clipCreateSession(modelPath: String, imageSize: Int, embeddingDim: Int): Long {
        Log.w(TAG, "clipCreateSession(modelPath,...): 缺少 Context，无法创建会话，返回 0L")
        return 0L
    }

    fun clipIsLoaded(sessionHandle: Long): Boolean = false

    fun clipEncodeImage(sessionHandle: Long, rgbBytes: ByteArray, width: Int, height: Int): FloatArray? {
        Log.w(TAG, "clipEncodeImage(handle,...): 旧句柄式 API 不支持，请改用 clipEncodeImage(context, bitmap)")
        return null
    }

    fun clipEncodeText(sessionHandle: Long, text: String): FloatArray? {
        Log.w(TAG, "clipEncodeText(handle,...): 旧句柄式 API 不支持，请改用 clipEncodeText(context, text)")
        return null
    }

    fun clipDestroySession(sessionHandle: Long) {
        // no-op：旧句柄式会话从未真正创建
    }
}
