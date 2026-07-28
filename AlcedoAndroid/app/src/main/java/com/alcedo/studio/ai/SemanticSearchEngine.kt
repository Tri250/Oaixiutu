package com.alcedo.studio.ai

import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.local.AiEmbeddingEntity
import com.alcedo.studio.data.model.AiEmbedding
import com.alcedo.studio.data.model.SemanticSearchResult
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vector search engine over persisted CLIP/SigLIP embeddings. Loads all
 * embeddings for a model into memory (typical catalogues fit comfortably in a
 * few MB) and performs brute-force cosine similarity, returning ranked
 * [SemanticSearchResult]s. For very large catalogues the native layer provides
 * an ANN index; this Kotlin implementation is the reference path.
 */
@Singleton
class SemanticSearchEngine @Inject constructor(
    private val embeddingDao: AiEmbeddingDao,
) {

    data class IndexEntry(val imageId: String, val vector: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IndexEntry) return false
            return imageId == other.imageId && vector.contentEquals(other.vector)
        }
        override fun hashCode(): Int = imageId.hashCode()
    }

    @Volatile
    private var index: List<IndexEntry> = emptyList()

    @Volatile
    private var indexedModelId: String? = null

    /** Load all embeddings for [modelId] into the in-memory index. */
    suspend fun buildIndex(modelId: String) = withContext(ThreadPool.aiInference) {
        val entities = embeddingDao.getAllForModel(modelId)
        index = entities.map { e ->
            IndexEntry(e.imageId, AiEmbeddingDao.unpack(e.embeddingBlob))
        }
        indexedModelId = modelId
    }

    /** True when the index for [modelId] is loaded. */
    fun isIndexed(modelId: String): Boolean = indexedModelId == modelId && index.isNotEmpty()

    /** Number of vectors in the current index. */
    fun indexSize(): Int = index.size

    /** Search the index for [queryVector], returning the top [k] results. */
    suspend fun search(queryVector: FloatArray, k: Int = 50): List<SemanticSearchResult> =
        withContext(ThreadPool.aiInference) {
            if (index.isEmpty()) return@withContext emptyList()
            index.map { entry ->
                val score = AiEmbedding.cosineSimilarity(queryVector, entry.vector)
                SemanticSearchResult(entry.imageId, score)
            }.sortedByDescending { it.score }.take(k)
        }

    /** Add or replace a single embedding in the in-memory index. */
    fun upsert(entry: IndexEntry) {
        val current = index.toMutableList()
        val existing = current.indexOfFirst { it.imageId == entry.imageId }
        if (existing >= 0) current[existing] = entry else current.add(entry)
        index = current
    }

    /** Remove an image from the in-memory index. */
    fun remove(imageId: String) {
        index = index.filterNot { it.imageId == imageId }
    }

    /** Clear the in-memory index. */
    fun clear() {
        index = emptyList()
        indexedModelId = null
    }
}
