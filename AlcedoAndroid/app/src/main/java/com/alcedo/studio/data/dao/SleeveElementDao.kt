package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.SleeveElementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleeveElementDao {

    @Query("SELECT * FROM sleeve_elements WHERE elementId = :elementId")
    suspend fun getById(elementId: Long): SleeveElementEntity?

    @Query("SELECT * FROM sleeve_elements WHERE elementId = :elementId")
    fun getByIdFlow(elementId: Long): Flow<SleeveElementEntity?>

    @Query("SELECT * FROM sleeve_elements WHERE parentId = :parentId ORDER BY elementType ASC, elementName ASC")
    suspend fun getByParent(parentId: Long): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE parentId = :parentId ORDER BY elementType ASC, elementName ASC")
    fun getByParentFlow(parentId: Long): Flow<List<SleeveElementEntity>>

    @Query("SELECT * FROM sleeve_elements WHERE parentId = :parentId AND elementType = :elementType ORDER BY elementName ASC")
    suspend fun getByParentAndType(parentId: Long, elementType: Int): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE elementName = :name AND parentId = :parentId LIMIT 1")
    suspend fun getByPath(name: String, parentId: Long): SleeveElementEntity?

    @Query("SELECT * FROM sleeve_elements WHERE parentId = :parentId AND elementType = 2 ORDER BY elementName ASC")
    suspend fun getSubFolders(parentId: Long): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE parentId = :parentId AND elementType = 1 ORDER BY elementName ASC")
    suspend fun getFilesInFolder(parentId: Long): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements ORDER BY addedTime DESC")
    suspend fun getAll(): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements ORDER BY addedTime DESC")
    fun getAllFlow(): Flow<List<SleeveElementEntity>>

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE parentId = :parentId AND elementType = 1")
    suspend fun countFiles(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE parentId = :parentId AND elementType = 2")
    suspend fun countFolders(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM sleeve_elements")
    suspend fun countAll(): Int

    @Query("SELECT * FROM sleeve_elements WHERE syncFlag = :syncFlag")
    suspend fun getBySyncFlag(syncFlag: Int): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE elementName LIKE '%' || :query || '%' AND parentId = :parentId")
    suspend fun searchInFolder(query: String, parentId: Long): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE elementName LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<SleeveElementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(element: SleeveElementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(elements: List<SleeveElementEntity>): List<Long>

    @Update
    suspend fun update(element: SleeveElementEntity)

    @Update
    suspend fun updateAll(elements: List<SleeveElementEntity>)

    @Query("UPDATE sleeve_elements SET syncFlag = :syncFlag WHERE elementId = :elementId")
    suspend fun updateSyncFlag(elementId: Long, syncFlag: Int)

    @Query("UPDATE sleeve_elements SET lastModifiedTime = :modifiedTime WHERE elementId = :elementId")
    suspend fun updateModifiedTime(elementId: Long, modifiedTime: Long)

    @Query("UPDATE sleeve_elements SET currentVersionId = :versionId WHERE elementId = :elementId")
    suspend fun updateCurrentVersion(elementId: Long, versionId: String)

    @Delete
    suspend fun delete(element: SleeveElementEntity)

    @Query("DELETE FROM sleeve_elements WHERE elementId = :elementId")
    suspend fun deleteById(elementId: Long)

    @Query("DELETE FROM sleeve_elements WHERE parentId = :parentId")
    suspend fun deleteByParent(parentId: Long)

    @Query("DELETE FROM sleeve_elements WHERE syncFlag = 2")
    suspend fun deleteMarkedAsDeleted()
}
