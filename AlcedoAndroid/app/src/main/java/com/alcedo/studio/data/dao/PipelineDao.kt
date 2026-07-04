package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.PipelineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Query("SELECT * FROM pipeline_state WHERE id = :id")
    suspend fun getById(id: Long): PipelineEntity?

    @Query("SELECT * FROM pipeline_state WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): PipelineEntity?

    @Query("SELECT * FROM pipeline_state WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<PipelineEntity?>

    @Query("SELECT * FROM pipeline_state ORDER BY createdTime DESC")
    suspend fun getAll(): List<PipelineEntity>

    @Query("SELECT * FROM pipeline_state ORDER BY createdTime DESC")
    fun getAllFlow(): Flow<List<PipelineEntity>>

    @Query("SELECT COUNT(*) FROM pipeline_state")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pipeline_state WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pipeline: PipelineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(pipelines: List<PipelineEntity>): List<Long>

    @Update
    suspend fun update(pipeline: PipelineEntity)

    @Query("UPDATE pipeline_state SET paramsJson = :paramsJson WHERE imageId = :imageId")
    suspend fun updateParams(imageId: Long, paramsJson: String)

    @Delete
    suspend fun delete(pipeline: PipelineEntity)

    @Query("DELETE FROM pipeline_state WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pipeline_state WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
