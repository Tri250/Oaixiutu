package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.AiEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiEmbeddingDao {

    @Query("SELECT * FROM ai_embeddings WHERE id = :id")
    suspend fun getById(id: Long): AiEmbeddingEntity?

    @Query("SELECT * FROM ai_embeddings WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): AiEmbeddingEntity?

    @Query("SELECT * FROM ai_embeddings WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<AiEmbeddingEntity?>

    @Query("SELECT * FROM ai_embeddings WHERE imageId = :imageId AND modelVersion = :modelVersion LIMIT 1")
    suspend fun getByImageIdAndModel(imageId: Long, modelVersion: String): AiEmbeddingEntity?

    @Query("SELECT * FROM ai_embeddings ORDER BY createdTime ASC")
    suspend fun getAll(): List<AiEmbeddingEntity>

    @Query("SELECT * FROM ai_embeddings ORDER BY createdTime ASC")
    fun getAllFlow(): Flow<List<AiEmbeddingEntity>>

    @Query("SELECT * FROM ai_embeddings ORDER BY createdTime ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<AiEmbeddingEntity>

    @Query("SELECT * FROM ai_embeddings WHERE modelVersion = :modelVersion ORDER BY createdTime ASC")
    suspend fun getByModelVersion(modelVersion: String): List<AiEmbeddingEntity>

    @Query("SELECT * FROM ai_embeddings WHERE modelVersion = :modelVersion ORDER BY createdTime ASC LIMIT :limit OFFSET :offset")
    suspend fun getByModelVersionPaged(modelVersion: String, limit: Int, offset: Int): List<AiEmbeddingEntity>

    @Query("SELECT COUNT(*) FROM ai_embeddings")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ai_embeddings WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Query("SELECT COUNT(*) FROM ai_embeddings WHERE modelVersion = :modelVersion")
    suspend fun countByModelVersion(modelVersion: String): Int

    @Query("SELECT imageId FROM ai_embeddings")
    suspend fun getAllEmbeddedImageIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: AiEmbeddingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<AiEmbeddingEntity>): List<Long>

    @Update
    suspend fun update(embedding: AiEmbeddingEntity)

    @Delete
    suspend fun delete(embedding: AiEmbeddingEntity)

    @Query("DELETE FROM ai_embeddings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_embeddings WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)

    @Query("DELETE FROM ai_embeddings WHERE imageId IN (:imageIds)")
    suspend fun deleteByImageIds(imageIds: List<Long>)

    @Query("DELETE FROM ai_embeddings WHERE modelVersion = :modelVersion")
    suspend fun deleteByModelVersion(modelVersion: String)
}
