package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.AiDescriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDescriptionDao {

    @Query("SELECT * FROM ai_descriptions WHERE id = :id")
    suspend fun getById(id: Long): AiDescriptionEntity?

    @Query("SELECT * FROM ai_descriptions WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): AiDescriptionEntity?

    @Query("SELECT * FROM ai_descriptions WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<AiDescriptionEntity?>

    @Query("SELECT * FROM ai_descriptions WHERE model = :model ORDER BY imageId ASC")
    suspend fun getByModel(model: String): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions ORDER BY imageId ASC")
    suspend fun getAll(): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions ORDER BY imageId ASC")
    fun getAllFlow(): Flow<List<AiDescriptionEntity>>

    @Query("SELECT COUNT(*) FROM ai_descriptions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ai_descriptions WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(description: AiDescriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(descriptions: List<AiDescriptionEntity>): List<Long>

    @Update
    suspend fun update(description: AiDescriptionEntity)

    @Delete
    suspend fun delete(description: AiDescriptionEntity)

    @Query("DELETE FROM ai_descriptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_descriptions WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
