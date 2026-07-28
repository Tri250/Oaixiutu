package com.alcedo.studio.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alcedo.studio.data.local.AiEmbeddingEntity
import com.alcedo.studio.data.local.AiRatingEntity
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Data access for AI embeddings (CLIP/SigLIP) and LLM ratings. Embeddings are
 * stored as packed little-endian float32 blobs; the DAO exposes typed helpers
 * to decode them back to [FloatArray] for vector search.
 */
@Dao
interface AiEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbedding(embedding: AiEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmbeddings(embeddings: List<AiEmbeddingEntity>)

    @Query("DELETE FROM ai_embeddings WHERE imageId = :imageId")
    suspend fun deleteForImage(imageId: String)

    @Query("DELETE FROM ai_embeddings WHERE modelId = :modelId")
    suspend fun deleteForModel(modelId: String)

    @Query("SELECT * FROM ai_embeddings WHERE imageId = :imageId AND modelId = :modelId LIMIT 1")
    suspend fun get(imageId: String, modelId: String): AiEmbeddingEntity?

    @Query("SELECT * FROM ai_embeddings WHERE modelId = :modelId")
    suspend fun getAllForModel(modelId: String): List<AiEmbeddingEntity>

    @Query("SELECT imageId FROM ai_embeddings WHERE modelId = :modelId")
    suspend fun embeddedImageIds(modelId: String): List<String>

    @Query("SELECT COUNT(*) FROM ai_embeddings WHERE modelId = :modelId")
    suspend fun countForModel(modelId: String): Int

    @Query("SELECT COUNT(*) FROM ai_embeddings")
    suspend fun count(): Int

    // ---- Ratings ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(rating: AiRatingEntity)

    @Query("DELETE FROM ai_ratings WHERE imageId = :imageId")
    suspend fun deleteRating(imageId: String)

    @Query("SELECT * FROM ai_ratings WHERE imageId = :imageId LIMIT 1")
    suspend fun getRating(imageId: String): AiRatingEntity?

    @Query("SELECT * FROM ai_ratings ORDER BY overallScore DESC LIMIT :limit")
    suspend fun topRated(limit: Int): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings ORDER BY overallScore ASC LIMIT :limit")
    suspend fun lowestRated(limit: Int): List<AiRatingEntity>

    @Query("SELECT COUNT(*) FROM ai_ratings")
    suspend fun ratingCount(): Int

    @Query("DELETE FROM ai_ratings")
    suspend fun deleteAllRatings()

    companion object {
        /** Pack a float vector into a little-endian byte blob for storage. */
        fun pack(vector: FloatArray): ByteArray {
            val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.asFloatBuffer().put(vector)
            return buffer.array()
        }

        /** Unpack a stored blob back into a float vector. */
        fun unpack(blob: ByteArray): FloatArray {
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(blob.size / 4)
            buffer.asFloatBuffer().get(out)
            return out
        }
    }
}
