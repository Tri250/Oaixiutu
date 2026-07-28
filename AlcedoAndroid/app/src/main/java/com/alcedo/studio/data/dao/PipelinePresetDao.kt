package com.alcedo.studio.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alcedo.studio.data.local.PipelinePresetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for pipeline presets (look/preset catalogue). Presets store a
 * serialised [com.alcedo.studio.data.model.AdjustmentParams] blob and are
 * grouped by category for the editor's Preset panel.
 */
@Dao
interface PipelinePresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PipelinePresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(presets: List<PipelinePresetEntity>)

    @Delete
    suspend fun delete(preset: PipelinePresetEntity)

    @Query("DELETE FROM pipeline_presets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pipeline_presets WHERE isBuiltIn = 0")
    suspend fun deleteAllUserPresets()

    @Query("SELECT * FROM pipeline_presets WHERE id = :id")
    suspend fun getById(id: String): PipelinePresetEntity?

    @Query("SELECT * FROM pipeline_presets ORDER BY name ASC")
    fun observeAll(): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE category = :category ORDER BY name ASC")
    fun observeByCategory(category: String): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<PipelinePresetEntity>>

    @Query("SELECT * FROM pipeline_presets WHERE isBuiltIn = 1 ORDER BY name ASC")
    suspend fun builtInPresets(): List<PipelinePresetEntity>

    @Query("UPDATE pipeline_presets SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE pipeline_presets SET thumbnailPath = :path WHERE id = :id")
    suspend fun setThumbnail(id: String, path: String?)

    @Query("SELECT COUNT(*) FROM pipeline_presets")
    suspend fun count(): Int

    @Query("SELECT DISTINCT category FROM pipeline_presets ORDER BY category ASC")
    suspend fun categories(): List<String>
}
