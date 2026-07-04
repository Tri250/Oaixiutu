package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.ImageMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMetadataDao {

    @Query("SELECT * FROM image_metadata WHERE id = :id")
    suspend fun getById(id: Long): ImageMetadataEntity?

    @Query("SELECT * FROM image_metadata WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ImageMetadataEntity?>

    @Query("SELECT * FROM image_metadata WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): ImageMetadataEntity?

    @Query("SELECT * FROM image_metadata WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<ImageMetadataEntity?>

    @Query("SELECT * FROM image_metadata WHERE cameraMake = :make ORDER BY imageId ASC")
    suspend fun getByCameraMake(make: String): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata WHERE cameraModel = :model ORDER BY imageId ASC")
    suspend fun getByCameraModel(model: String): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata WHERE lensModel = :lensModel ORDER BY imageId ASC")
    suspend fun getByLensModel(lensModel: String): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata ORDER BY imageId ASC")
    suspend fun getAll(): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata ORDER BY imageId ASC")
    fun getAllFlow(): Flow<List<ImageMetadataEntity>>

    @Query("SELECT COUNT(*) FROM image_metadata")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM image_metadata WHERE imageId = :imageId")
    suspend fun countByImageId(imageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: ImageMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(metadataList: List<ImageMetadataEntity>): List<Long>

    @Update
    suspend fun update(metadata: ImageMetadataEntity)

    @Delete
    suspend fun delete(metadata: ImageMetadataEntity)

    @Query("DELETE FROM image_metadata WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM image_metadata WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
