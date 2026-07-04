package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.FilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun getById(id: Long): FilterEntity?

    @Query("SELECT * FROM filters WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<FilterEntity?>

    @Query("SELECT * FROM filters ORDER BY name ASC")
    suspend fun getAll(): List<FilterEntity>

    @Query("SELECT * FROM filters ORDER BY name ASC")
    fun getAllFlow(): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<FilterEntity>

    @Query("SELECT * FROM filters WHERE category = :category ORDER BY name ASC")
    fun getByCategoryFlow(category: String): Flow<List<FilterEntity>>

    @Query("SELECT * FROM filters WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<FilterEntity>

    @Query("SELECT DISTINCT category FROM filters ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT COUNT(*) FROM filters")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM filters WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: FilterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(filters: List<FilterEntity>): List<Long>

    @Update
    suspend fun update(filter: FilterEntity)

    @Delete
    suspend fun delete(filter: FilterEntity)

    @Query("DELETE FROM filters WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM filters WHERE category = :category")
    suspend fun deleteByCategory(category: String)
}
