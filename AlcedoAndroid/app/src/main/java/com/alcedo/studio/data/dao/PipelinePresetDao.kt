package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.PipelinePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelinePresetDao {

    @Query("SELECT * FROM pipeline_presets WHERE id = :id")
    suspend fun getById(id: Long): PipelinePresetEntity?

    @Query("SELECT * FROM pipeline_presets ORDER BY name ASC")
    suspend fun getAll(): List<PipelinePresetEntity>

    @Query("SELECT * FROM pipeline_presets ORDER BY name ASC")
    fun getAllFlow(): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<PipelinePresetEntity>

    @Query("SELECT * FROM pipeline_presets WHERE category = :category ORDER BY name ASC")
    fun getByCategoryFlow(category: String): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE isBuiltIn = 1 ORDER BY name ASC")
    suspend fun getBuiltInPresets(): List<PipelinePresetEntity>

    @Query("SELECT * FROM pipeline_presets WHERE isBuiltIn = 1 ORDER BY name ASC")
    fun getBuiltInPresetsFlow(): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE isBuiltIn = 0 ORDER BY name ASC")
    suspend fun getUserPresets(): List<PipelinePresetEntity>

    @Query("SELECT * FROM pipeline_presets WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<PipelinePresetEntity>

    @Query("SELECT * FROM pipeline_presets WHERE category = :category AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByNameInCategory(query: String, category: String): List<PipelinePresetEntity>

    @Query("SELECT DISTINCT category FROM pipeline_presets ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT COUNT(*) FROM pipeline_presets")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pipeline_presets WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: PipelinePresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<PipelinePresetEntity>): List<Long>

    @Update
    suspend fun update(preset: PipelinePresetEntity)

    @Delete
    suspend fun delete(preset: PipelinePresetEntity)

    @Query("DELETE FROM pipeline_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pipeline_presets WHERE isBuiltIn = 0")
    suspend fun deleteAllUserPresets()

    @Query("DELETE FROM pipeline_presets WHERE category = :category AND isBuiltIn = 0")
    suspend fun deleteUserPresetsByCategory(category: String)
}
