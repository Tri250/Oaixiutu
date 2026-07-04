package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.CollectionV2Entity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionV2Dao {

    @Query("SELECT * FROM collections_v2 WHERE id = :id")
    suspend fun getById(id: Long): CollectionV2Entity?

    @Query("SELECT * FROM collections_v2 WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<CollectionV2Entity?>

    @Query("SELECT * FROM collections_v2 WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CollectionV2Entity?

    @Query("SELECT * FROM collections_v2 WHERE isSmart = 1 ORDER BY name ASC")
    suspend fun getSmartCollections(): List<CollectionV2Entity>

    @Query("SELECT * FROM collections_v2 WHERE isSmart = 0 ORDER BY name ASC")
    suspend fun getManualCollections(): List<CollectionV2Entity>

    @Query("SELECT * FROM collections_v2 ORDER BY name ASC")
    suspend fun getAll(): List<CollectionV2Entity>

    @Query("SELECT * FROM collections_v2 ORDER BY name ASC")
    fun getAllFlow(): Flow<List<CollectionV2Entity>>

    @Query("SELECT * FROM collections_v2 WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchByName(query: String): List<CollectionV2Entity>

    @Query("SELECT COUNT(*) FROM collections_v2")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM collections_v2 WHERE isSmart = 1")
    suspend fun countSmartCollections(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionV2Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(collections: List<CollectionV2Entity>): List<Long>

    @Update
    suspend fun update(collection: CollectionV2Entity)

    @Query("UPDATE collections_v2 SET coverImageId = :coverImageId WHERE id = :id")
    suspend fun updateCoverImage(id: Long, coverImageId: Long)

    @Delete
    suspend fun delete(collection: CollectionV2Entity)

    @Query("DELETE FROM collections_v2 WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM collections_v2 WHERE name = :name")
    suspend fun deleteByName(name: String)
}
