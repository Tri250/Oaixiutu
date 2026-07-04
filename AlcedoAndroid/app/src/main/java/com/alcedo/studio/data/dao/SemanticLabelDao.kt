package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.SemanticLabelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemanticLabelDao {

    @Query("SELECT * FROM semantic_labels WHERE id = :id")
    suspend fun getById(id: Long): SemanticLabelEntity?

    @Query("SELECT * FROM semantic_labels WHERE imageId = :imageId ORDER BY confidence DESC")
    suspend fun getByImageId(imageId: Long): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels WHERE imageId = :imageId ORDER BY confidence DESC")
    fun getByImageIdFlow(imageId: Long): Flow<List<SemanticLabelEntity>>

    @Query("SELECT * FROM semantic_labels WHERE label = :label ORDER BY confidence DESC")
    suspend fun getByLabel(label: String): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels WHERE source = :source ORDER BY imageId ASC")
    suspend fun getBySource(source: String): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels WHERE imageId = :imageId AND label = :label LIMIT 1")
    suspend fun getByImageIdAndLabel(imageId: Long, label: String): SemanticLabelEntity?

    @Query("SELECT * FROM semantic_labels WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getByMinConfidence(minConfidence: Float): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels ORDER BY imageId ASC, confidence DESC")
    suspend fun getAll(): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels ORDER BY imageId ASC, confidence DESC")
    fun getAllFlow(): Flow<List<SemanticLabelEntity>>

    @Query("SELECT COUNT(*) FROM semantic_labels")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM semantic_labels WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Query("SELECT DISTINCT label FROM semantic_labels ORDER BY label ASC")
    suspend fun getAllDistinctLabels(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: SemanticLabelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(labels: List<SemanticLabelEntity>): List<Long>

    @Update
    suspend fun update(label: SemanticLabelEntity)

    @Delete
    suspend fun delete(label: SemanticLabelEntity)

    @Query("DELETE FROM semantic_labels WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM semantic_labels WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
