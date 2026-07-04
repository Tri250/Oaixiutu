package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.SemanticLabelV2Entity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemanticLabelV2Dao {

    @Query("SELECT * FROM semantic_labels_v2 WHERE id = :id")
    suspend fun getById(id: Long): SemanticLabelV2Entity?

    @Query("SELECT * FROM semantic_labels_v2 WHERE imageId = :imageId ORDER BY confidence DESC")
    suspend fun getByImageId(imageId: Long): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE imageId = :imageId ORDER BY confidence DESC")
    fun getByImageIdFlow(imageId: Long): Flow<List<SemanticLabelV2Entity>>

    @Query("SELECT * FROM semantic_labels_v2 WHERE label = :label ORDER BY confidence DESC")
    suspend fun getByLabel(label: String): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE category = :category ORDER BY imageId ASC, confidence DESC")
    suspend fun getByCategory(category: String): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE source = :source ORDER BY imageId ASC")
    suspend fun getBySource(source: String): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE imageId = :imageId AND label = :label LIMIT 1")
    suspend fun getByImageIdAndLabel(imageId: Long, label: String): SemanticLabelV2Entity?

    @Query("SELECT * FROM semantic_labels_v2 WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getByMinConfidence(minConfidence: Float): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 ORDER BY imageId ASC, confidence DESC")
    suspend fun getAll(): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 ORDER BY imageId ASC, confidence DESC")
    fun getAllFlow(): Flow<List<SemanticLabelV2Entity>>

    @Query("SELECT COUNT(*) FROM semantic_labels_v2")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM semantic_labels_v2 WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Query("SELECT DISTINCT label FROM semantic_labels_v2 ORDER BY label ASC")
    suspend fun getAllDistinctLabels(): List<String>

    @Query("SELECT DISTINCT category FROM semantic_labels_v2 ORDER BY category ASC")
    suspend fun getAllDistinctCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(label: SemanticLabelV2Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(labels: List<SemanticLabelV2Entity>): List<Long>

    @Update
    suspend fun update(label: SemanticLabelV2Entity)

    @Delete
    suspend fun delete(label: SemanticLabelV2Entity)

    @Query("DELETE FROM semantic_labels_v2 WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM semantic_labels_v2 WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
