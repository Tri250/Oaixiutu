package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.FileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleeveFileDao {

    @Query("SELECT * FROM sleeve_files WHERE id = :id")
    suspend fun getById(id: Long): FileEntity?

    @Query("SELECT * FROM sleeve_files WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<FileEntity?>

    @Query("SELECT * FROM sleeve_files WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): FileEntity?

    @Query("SELECT * FROM sleeve_files WHERE path = :path LIMIT 1")
    fun getByPathFlow(path: String): Flow<FileEntity?>

    @Query("SELECT * FROM sleeve_files WHERE fileHash = :fileHash")
    suspend fun getByHash(fileHash: String): List<FileEntity>

    @Query("SELECT * FROM sleeve_files WHERE mimeType = :mimeType ORDER BY path ASC")
    suspend fun getByMimeType(mimeType: String): List<FileEntity>

    @Query("SELECT * FROM sleeve_files ORDER BY path ASC")
    suspend fun getAll(): List<FileEntity>

    @Query("SELECT * FROM sleeve_files ORDER BY path ASC")
    fun getAllFlow(): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) FROM sleeve_files")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sleeve_files WHERE mimeType = :mimeType")
    suspend fun countByMimeType(mimeType: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertAll(files: List<FileEntity>): List<Long>

    @Update
    suspend fun update(file: FileEntity)

    @Update
    @Transaction
    suspend fun updateAll(files: List<FileEntity>)

    @Delete
    suspend fun delete(file: FileEntity)

    @Query("DELETE FROM sleeve_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sleeve_files WHERE path = :path")
    suspend fun deleteByPath(path: String)
}
