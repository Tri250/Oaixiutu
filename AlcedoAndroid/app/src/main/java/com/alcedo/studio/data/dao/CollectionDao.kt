package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<CollectionEntity>

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name ASC")
    fun getAllFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE name LIKE '%' || :query || '%' ORDER BY sortOrder ASC")
    suspend fun searchByName(query: String): List<CollectionEntity>

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(collections: List<CollectionEntity>): List<Long>

    @Update
    suspend fun update(collection: CollectionEntity)

    @Query("UPDATE collections SET coverImageId = :coverImageId WHERE id = :id")
    suspend fun updateCoverImage(id: Long, coverImageId: Long)

    @Query("UPDATE collections SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Delete
    suspend fun delete(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM collections WHERE name = :name")
    suspend fun deleteByName(name: String)
}
