package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.FilterV2Entity
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterV2Dao {

    @Query("SELECT * FROM filters_v2 WHERE id = :id")
    suspend fun getById(id: Long): FilterV2Entity?

    @Query("SELECT * FROM filters_v2 WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<FilterV2Entity?>

    @Query("SELECT * FROM filters_v2 ORDER BY name ASC")
    suspend fun getAll(): List<FilterV2Entity>

    @Query("SELECT * FROM filters_v2 ORDER BY name ASC")
    fun getAllFlow(): Flow<List<FilterV2Entity>>

    @Query("SELECT * FROM filters_v2 WHERE category = :category ORDER BY name ASC")
    suspend fun getByCategory(category: String): List<FilterV2Entity>

    @Query("SELECT * FROM filters_v2 WHERE category = :category ORDER BY name ASC")
    fun getByCategoryFlow(category: String): Flow<List<FilterV2Entity>>

    @Query("SELECT * FROM filters_v2 WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<FilterV2Entity>

    @Query("SELECT DISTINCT category FROM filters_v2 ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Query("SELECT COUNT(*) FROM filters_v2")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM filters_v2 WHERE category = :category")
    suspend fun countByCategory(category: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(filter: FilterV2Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(filters: List<FilterV2Entity>): List<Long>

    @Update
    suspend fun update(filter: FilterV2Entity)

    @Delete
    suspend fun delete(filter: FilterV2Entity)

    @Query("DELETE FROM filters_v2 WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM filters_v2 WHERE category = :category")
    suspend fun deleteByCategory(category: String)
}
