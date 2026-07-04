package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.ImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<ImageEntity?>

    @Query("SELECT * FROM images ORDER BY dateAdded DESC")
    suspend fun getAll(): List<ImageEntity>

    @Query("SELECT * FROM images ORDER BY dateAdded DESC")
    fun getAllFlow(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images ORDER BY dateAdded DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): ImageEntity?

    @Query("SELECT * FROM images WHERE fileName LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    suspend fun searchByName(query: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE dateAdded BETWEEN :startDate AND :endDate ORDER BY dateAdded DESC")
    suspend fun getByDateRange(startDate: Long, endDate: Long): List<ImageEntity>

    @Query("SELECT * FROM images WHERE rating = :rating ORDER BY dateAdded DESC")
    suspend fun getByRating(rating: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE rating >= :minRating ORDER BY dateAdded DESC")
    suspend fun getByMinRating(minRating: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE colorLabel = :colorLabel ORDER BY dateAdded DESC")
    suspend fun getByColorLabel(colorLabel: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE isRaw = 1 ORDER BY dateAdded DESC")
    suspend fun getRawImages(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE isRaw = 1 ORDER BY dateAdded DESC")
    fun getRawImagesFlow(): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images WHERE rawMake LIKE '%' || :make || '%' ORDER BY dateAdded DESC")
    suspend fun getByCameraMake(make: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE rawModel LIKE '%' || :model || '%' ORDER BY dateAdded DESC")
    suspend fun getByCameraModel(model: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE iso = :isoValue ORDER BY dateAdded DESC")
    suspend fun getByIso(isoValue: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE focalLength BETWEEN :minFocal AND :maxFocal ORDER BY dateAdded DESC")
    suspend fun getByFocalLengthRange(minFocal: Double, maxFocal: Double): List<ImageEntity>

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM images WHERE isRaw = 1")
    suspend fun countRawImages(): Int

    @Query("SELECT COUNT(*) FROM images WHERE rating = :rating")
    suspend fun countByRating(rating: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(images: List<ImageEntity>): List<Long>

    @Update
    suspend fun update(image: ImageEntity)

    @Update
    @Transaction
    suspend fun updateAll(images: List<ImageEntity>)

    @Query("UPDATE images SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Int)

    @Query("UPDATE images SET colorLabel = :colorLabel WHERE id = :id")
    suspend fun updateColorLabel(id: Long, colorLabel: Int)

    @Query("UPDATE images SET thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateThumbnail(id: Long, thumbnailPath: String)

    @Delete
    suspend fun delete(image: ImageEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM images WHERE filePath LIKE '%' || :directory || '%'")
    suspend fun deleteByDirectory(directory: String)
}
