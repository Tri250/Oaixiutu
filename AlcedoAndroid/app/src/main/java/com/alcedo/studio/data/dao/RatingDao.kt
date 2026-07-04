package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.RatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {

    @Query("SELECT * FROM ratings WHERE id = :id")
    suspend fun getById(id: Long): RatingEntity?

    @Query("SELECT * FROM ratings WHERE imageId = :imageId LIMIT 1")
    suspend fun getByImageId(imageId: Long): RatingEntity?

    @Query("SELECT * FROM ratings WHERE imageId = :imageId LIMIT 1")
    fun getByImageIdFlow(imageId: Long): Flow<RatingEntity?>

    @Query("SELECT * FROM ratings WHERE rating = :rating ORDER BY imageId ASC")
    suspend fun getByRating(rating: Int): List<RatingEntity>

    @Query("SELECT * FROM ratings WHERE rating >= :minRating ORDER BY imageId ASC")
    suspend fun getByMinRating(minRating: Int): List<RatingEntity>

    @Query("SELECT * FROM ratings WHERE ratingSource = :source ORDER BY imageId ASC")
    suspend fun getBySource(source: String): List<RatingEntity>

    @Query("SELECT * FROM ratings ORDER BY imageId ASC")
    suspend fun getAll(): List<RatingEntity>

    @Query("SELECT * FROM ratings ORDER BY imageId ASC")
    fun getAllFlow(): Flow<List<RatingEntity>>

    @Query("SELECT COUNT(*) FROM ratings")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ratings WHERE rating = :rating")
    suspend fun countByRating(rating: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: RatingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(ratings: List<RatingEntity>): List<Long>

    @Update
    suspend fun update(rating: RatingEntity)

    @Query("UPDATE ratings SET rating = :rating WHERE imageId = :imageId")
    suspend fun updateRating(imageId: Long, rating: Int)

    @Delete
    suspend fun delete(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ratings WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
