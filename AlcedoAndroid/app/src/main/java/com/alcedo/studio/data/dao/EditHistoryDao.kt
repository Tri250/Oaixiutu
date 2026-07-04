package com.alcedo.studio.data.dao

import androidx.room.*
import com.alcedo.studio.data.model.EditHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EditHistoryDao {

    @Query("SELECT * FROM edit_history WHERE id = :id")
    suspend fun getById(id: Long): EditHistoryEntity?

    @Query("SELECT * FROM edit_history WHERE imageId = :imageId ORDER BY createdTime ASC")
    suspend fun getVersionsByImageId(imageId: Long): List<EditHistoryEntity>

    @Query("SELECT * FROM edit_history WHERE imageId = :imageId ORDER BY createdTime ASC")
    fun getVersionsByImageIdFlow(imageId: Long): Flow<List<EditHistoryEntity>>

    @Query("SELECT * FROM edit_history WHERE imageId = :imageId AND isActive = 1 LIMIT 1")
    suspend fun getActiveVersion(imageId: Long): EditHistoryEntity?

    @Query("SELECT * FROM edit_history WHERE imageId = :imageId AND versionId = :versionId LIMIT 1")
    suspend fun getByVersionId(imageId: Long, versionId: String): EditHistoryEntity?

    @Query("SELECT * FROM edit_history WHERE imageId = :imageId AND parentId = :parentId ORDER BY createdTime ASC")
    suspend fun getChildVersions(imageId: Long, parentId: String): List<EditHistoryEntity>

    @Query("SELECT COUNT(*) FROM edit_history WHERE imageId = :imageId")
    suspend fun countVersions(imageId: Long): Int

    @Query("SELECT * FROM edit_history ORDER BY createdTime DESC")
    suspend fun getAll(): List<EditHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EditHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EditHistoryEntity>): List<Long>

    @Update
    suspend fun update(entity: EditHistoryEntity)

    @Query("UPDATE edit_history SET paramsJson = :paramsJson WHERE imageId = :imageId AND versionId = :versionId")
    suspend fun updateParams(imageId: Long, versionId: String, paramsJson: String)

    @Query("UPDATE edit_history SET name = :name WHERE imageId = :imageId AND versionId = :versionId")
    suspend fun updateVersionName(imageId: Long, versionId: String, name: String)

    @Query("UPDATE edit_history SET isActive = 0 WHERE imageId = :imageId")
    suspend fun deactivateAllVersions(imageId: Long)

    @Query("UPDATE edit_history SET isActive = 1 WHERE imageId = :imageId AND versionId = :versionId")
    suspend fun setActiveVersion(imageId: Long, versionId: String)

    @Delete
    suspend fun delete(entity: EditHistoryEntity)

    @Query("DELETE FROM edit_history WHERE imageId = :imageId AND versionId = :versionId")
    suspend fun deleteByVersionId(imageId: Long, versionId: String)

    @Query("DELETE FROM edit_history WHERE imageId = :imageId")
    suspend fun deleteByImageId(imageId: Long)
}
