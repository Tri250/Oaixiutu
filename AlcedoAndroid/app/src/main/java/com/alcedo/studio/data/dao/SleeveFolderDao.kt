package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleeveFolderDao {

    @Query("SELECT * FROM sleeve_folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM sleeve_folders WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<FolderEntity?>

    @Query("SELECT * FROM sleeve_folders WHERE parentId = :parentId ORDER BY sortOrder ASC, folderName ASC")
    suspend fun getByParent(parentId: Long): List<FolderEntity>

    @Query("SELECT * FROM sleeve_folders WHERE parentId = :parentId ORDER BY sortOrder ASC, folderName ASC")
    fun getByParentFlow(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM sleeve_folders WHERE folderName = :folderName AND parentId = :parentId LIMIT 1")
    suspend fun getByNameAndParent(folderName: String, parentId: Long): FolderEntity?

    @Query("SELECT * FROM sleeve_folders ORDER BY folderName ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM sleeve_folders ORDER BY folderName ASC")
    fun getAllFlow(): Flow<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM sleeve_folders WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM sleeve_folders")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(folders: List<FolderEntity>): List<Long>

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE sleeve_folders SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM sleeve_folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sleeve_folders WHERE parentId = :parentId")
    suspend fun deleteByParent(parentId: Long)
}
