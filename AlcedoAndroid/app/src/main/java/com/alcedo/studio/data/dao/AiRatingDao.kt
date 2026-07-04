package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.AiRatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiRatingDao {

    @Query("SELECT * FROM ai_ratings WHERE id = :id")
    suspend fun getById(id: Long): AiRatingEntity?

    @Query("SELECT * FROM ai_ratings WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): AiRatingEntity?

    @Query("SELECT * FROM ai_ratings WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<AiRatingEntity?>

    @Query("SELECT * FROM ai_ratings WHERE qualityScore >= :minScore ORDER BY qualityScore DESC")
    suspend fun getByMinQualityScore(minScore: Float): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings WHERE aestheticScore >= :minScore ORDER BY aestheticScore DESC")
    suspend fun getByMinAestheticScore(minScore: Float): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings ORDER BY imageId ASC")
    suspend fun getAll(): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings ORDER BY imageId ASC")
    fun getAllFlow(): Flow<List<AiRatingEntity>>

    @Query("SELECT COUNT(*) FROM ai_ratings")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ai_ratings WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: AiRatingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(ratings: List<AiRatingEntity>): List<Long>

    @Update
    suspend fun update(rating: AiRatingEntity)

    @Delete
    suspend fun delete(rating: AiRatingEntity)

    @Query("DELETE FROM ai_ratings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_ratings WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
