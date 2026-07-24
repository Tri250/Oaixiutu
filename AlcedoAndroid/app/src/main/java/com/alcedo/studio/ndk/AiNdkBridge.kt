package com.alcedo.studio.ndk

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.domain.service.ClipInferenceEngine
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * AI NDK 桥接器 — 委托给 Kotlin 实现的 ClipInferenceEngine
 *
 * 提供统一的 AI 推理接口，实际推理由 ONNX Runtime 执行；
 * HNSW 索引使用纯 Kotlin 实现的 HNSW 图结构，支持 O(log n) 近似最近邻搜索。
 */
object AiNdkBridge {
    private const val TAG = "AiNdkBridge"

    private var clipEngine: ClipInferenceEngine? = null

    // HNSW 图索引实例
    private val indexLock = Any()
    private var hnswGraph: HnswGraph? = null

    // ── HNSW 图索引实现 ──────────────────────────────────────────────

    /**
     * HNSW (Hierarchical Navigable Small World) 图索引
     * 基于 C++ hnsw_index.cpp 的算法逻辑，使用纯 Kotlin 实现
     * 支持 O(log n) 的近似最近邻搜索
     */
    private class HnswGraph(
        private val dimensions: Int,
        private val m: Int = 16,              // 每层最大连接数
        private val efConstruction: Int = 200, // 构建时搜索宽度
        private val efSearch: Int = 50,        // 搜索时搜索宽度
        private val mMax0: Int = m * 2,        // 第0层最大连接数
        private val seed: Long = System.currentTimeMillis()
    ) {
        private val levelMult = 1.0f / ln(1.0f * m)
        private val rng = Random(seed)

        private val nodes = mutableMapOf<Long, HnswNode>()
        private var entryPoint: Long = 0
        private var hasEntry = false  // 独立于entryPoint==0的哨兵标志
        private var maxLevel = -1

        private class HnswNode(
            val id: Long,
            val embedding: FloatArray,
            val level: Int
        ) {
            // 每层的邻居列表，层号 → 邻居ID列表
            val neighbors = mutableMapOf<Int, MutableList<Long>>()
        }

        // ── 距离计算 (1 - cosine similarity) ──

        private fun distance(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            val sim = if (denom > 0f) dot / denom else 0f
            return 1.0f - sim
        }

        // ── 随机层级生成 ──

        private fun randomLevel(): Int {
            val r = rng.nextFloat()
            if (r <= 0f) return 0
            return (-(ln(r.toDouble()) * levelMult)).toInt().coerceAtLeast(0)
        }

        // ── 层级搜索 ──

        private fun searchLayer(
            query: FloatArray,
            entryPoints: List<Long>,
            ef: Int,
            layer: Int
        ): List<Pair<Float, Long>> {
            // 使用最小堆维护候选，最大堆维护结果
            val visited = mutableSetOf<Long>()
            val candidates = mutableListOf<Pair<Float, Long>>() // min-heap by distance
            val results = mutableListOf<Pair<Float, Long>>()   // max-heap by distance (simulated)

            for (epId in entryPoints) {
                if (epId in visited) continue
                visited.add(epId)
                val node = nodes[epId] ?: continue
                val d = distance(query, node.embedding)
                candidates.add(d to epId)
                results.add(d to epId)
            }

            // Sort candidates ascending (min-heap)
            candidates.sortBy { it.first }
            // Sort results descending (max-heap)
            results.sortByDescending { it.first }

            while (candidates.isNotEmpty()) {
                val (cDist, cId) = candidates.removeAt(0) // closest candidate
                if (results.size >= ef && cDist > results.first().first) break

                val node = nodes[cId] ?: continue
                val neighbors = node.neighbors[layer] ?: continue

                for (nId in neighbors) {
                    if (nId in visited) continue
                    visited.add(nId)
                    val nNode = nodes[nId] ?: continue
                    val d = distance(query, nNode.embedding)

                    if (results.size < ef || d < results.first().first) {
                        // Insert into candidates (sorted)
                        val insertPos = candidates.binarySearchBy(d) { it.first }.let {
                            if (it < 0) -(it + 1) else it
                        }
                        candidates.add(insertPos, d to nId)

                        // Insert into results (sorted descending)
                        results.add(d to nId)
                        results.sortByDescending { it.first }
                        if (results.size > ef) {
                            results.removeAt(0) // remove farthest
                        }
                    }
                }
            }

            return results.sortedBy { it.first } // return closest first
        }

        // ── 选择邻居（简化版：按距离选前M个）──

        private fun selectNeighbors(
            query: FloatArray,
            candidates: List<Long>,
            m: Int
        ): List<Long> {
            return candidates
                .mapNotNull { id -> nodes[id]?.let { distance(query, it.embedding) to id } }
                .sortedBy { it.first }
                .take(m)
                .map { it.second }
        }

        // ── 连接邻居 ──

        private fun connectNeighbors(nodeId: Long, layer: Int) {
            val node = nodes[nodeId] ?: return
            val maxConn = if (layer == 0) mMax0 else m
            val nodeNeighbors = node.neighbors.getOrPut(layer) { mutableListOf() }

            for (nId in nodeNeighbors.toList()) {
                val neighbor = nodes[nId] ?: continue
                val nNeighbors = neighbor.neighbors.getOrPut(layer) { mutableListOf() }

                if (nNeighbors.size < maxConn) {
                    nNeighbors.add(nodeId)
                } else {
                    // 剪枝：替换最远的邻居（距离应从被剪枝节点的视角计算）
                    val nEmb = neighbor.embedding
                    var worstIdx = 0
                    var worstDist = distance(nEmb, nodes[nNeighbors[0]]?.embedding ?: continue)

                    for (j in 1 until nNeighbors.size) {
                        val neighborOfN = nodes[nNeighbors[j]]?.embedding ?: continue
                        val d = distance(nEmb, neighborOfN)
                        if (d > worstDist) {
                            worstDist = d
                            worstIdx = j
                        }
                    }

                    val dToNode = distance(nEmb, node.embedding)
                    if (dToNode < worstDist) {
                        nNeighbors[worstIdx] = nodeId
                    }
                }
            }
        }

        // ── 插入 ──

        fun insert(id: Long, embedding: FloatArray) {
            val normalized = embedding.copyOf()
            normalizeEmbedding(normalized)

            val level = randomLevel()
            val node = HnswNode(id, normalized, level)
            nodes[id] = node

            if (!hasEntry) {
                entryPoint = id
                hasEntry = true
                maxLevel = level
                return
            }

            var curId = entryPoint

            // 从顶层向下贪婪搜索
            for (L in maxLevel downTo (level + 1)) {
                val results = searchLayer(normalized, listOf(curId), 1, L)
                if (results.isNotEmpty()) curId = results[0].second
            }

            // 从 min(level, maxLevel) 层向下逐层插入
            for (L in minOf(level, maxLevel) downTo 0) {
                val results = searchLayer(normalized, listOf(curId), efConstruction, L)
                val candidateIds = results.map { it.second }
                val selected = selectNeighbors(normalized, candidateIds, if (L == 0) mMax0 else m)

                node.neighbors[L] = selected.toMutableList()
                connectNeighbors(id, L)

                if (results.isNotEmpty()) curId = results[0].second
            }

            if (level > maxLevel) {
                maxLevel = level
                entryPoint = id
            }
        }

        // ── 搜索 ──

        fun search(query: FloatArray, k: Int): List<Pair<Long, Float>> {
            if (nodes.isEmpty() || k <= 0) return emptyList()

            val normalizedQuery = query.copyOf()
            normalizeEmbedding(normalizedQuery)

            if (!hasEntry) return emptyList()

            var curId = entryPoint
            // 从顶层向下贪婪搜索到第1层
            for (L in maxLevel downTo 1) {
                val results = searchLayer(normalizedQuery, listOf(curId), 1, L)
                if (results.isNotEmpty()) curId = results[0].second
            }

            // 在第0层用 efSearch 宽度搜索
            val results = searchLayer(normalizedQuery, listOf(curId), efSearch, 0)

            return results.take(k).map { (dist, id) ->
                id to (1.0f - dist) // distance → similarity
            }
        }

        // ── 删除 ──

        fun remove(id: Long) {
            val node = nodes[id] ?: return
            for (L in 0..node.level) {
                val neighbors = node.neighbors[L] ?: continue
                for (nId in neighbors) {
                    val neighbor = nodes[nId] ?: continue
                    neighbor.neighbors[L]?.remove(id)
                }
            }
            nodes.remove(id)
            if (id == entryPoint) {
                if (nodes.isEmpty()) {
                    entryPoint = 0L
                    hasEntry = false
                    maxLevel = -1
                } else {
                    entryPoint = nodes.keys.first()
                    maxLevel = nodes[entryPoint]?.level ?: -1
                }
            }
        }

        fun size(): Int = nodes.size

        fun clear() {
            nodes.clear()
            entryPoint = 0
            hasEntry = false
            maxLevel = -1
        }

        private fun normalizeEmbedding(emb: FloatArray) {
            var norm = 0f
            for (v in emb) norm += v * v
            norm = sqrt(norm)
            if (norm > 0f) {
                for (i in emb.indices) emb[i] = emb[i] / norm
            }
        }
    }

    fun initialize(context: Context) {
        if (clipEngine == null) {
            clipEngine = ClipInferenceEngine(context)
        }
    }

    // ── 编码（委托给 ClipInferenceEngine）──

    suspend fun encodeImage(context: Context, bitmap: Bitmap): FloatArray? {
        val maxRetries = 3
        var delayMs = 100L
        repeat(maxRetries) { attempt ->
            try {
                initialize(context)
                return clipEngine?.encodeImage(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "图像编码失败 (attempt ${attempt + 1}/$maxRetries)", e)
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        return null
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
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * 归一化嵌入向量：原地修改并返回同一数组，兼容旧调用方（丢弃返回值）与新调用方。
     */
    fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = sqrt(norm)
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

    // ── HNSW 索引管理 ──

    fun buildHnswIndex(
        context: Context,
        embeddings: List<Pair<Long, FloatArray>>,
        dimensions: Int = 512,
        maxElements: Int = 10000
    ): Boolean {
        if (embeddings.isEmpty()) {
            Log.w(TAG, "buildHnswIndex: embeddings is empty, skipping")
            return false
        }
        return try {
            synchronized(indexLock) {
                hnswGraph = HnswGraph(dimensions = dimensions)
                embeddings.forEach { (id, emb) ->
                    if (emb.isEmpty()) {
                        Log.w(TAG, "buildHnswIndex: skipping empty embedding for id=$id")
                        return@forEach
                    }
                    hnswGraph?.insert(id, emb)
                }
            }
            Log.i(TAG, "HNSW 索引构建完成: ${embeddings.size} 条目, 维度=$dimensions")
            true
        } catch (e: Exception) {
            Log.e(TAG, "构建 HNSW 索引失败", e)
            false
        }
    }

    fun hnswSearch(query: FloatArray, k: Int = 10): List<Pair<Long, Float>>? {
        if (query.isEmpty()) {
            Log.w(TAG, "hnswSearch: query vector is empty")
            return null
        }
        synchronized(indexLock) {
            val graph = hnswGraph ?: return null
            if (graph.size() == 0) return null
            return graph.search(query, k)
        }
    }

    fun hnswCreateIndex(dimensions: Int, maxElements: Int): Long {
        return try {
            synchronized(indexLock) {
                hnswGraph = HnswGraph(dimensions = dimensions)
            }
            1L
        } catch (e: Exception) {
            Log.e(TAG, "创建 HNSW 索引失败", e)
            0L
        }
    }

    fun hnswSize(): Int = synchronized(indexLock) { hnswGraph?.size() ?: 0 }

    fun hnswInsert(id: Long, embedding: FloatArray) {
        synchronized(indexLock) {
            val graph = hnswGraph ?: return
            graph.insert(id, embedding)
        }
    }

    fun hnswRemove(id: Long) {
        synchronized(indexLock) {
            val graph = hnswGraph ?: return
            graph.remove(id)
        }
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

    suspend fun clipDestroySession() {
        clipEngine?.unloadModel()
        clipEngine = null
    }

    // ================================================================
    // 向后兼容重载：保留旧句柄式 API，委托到上述实现。
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
