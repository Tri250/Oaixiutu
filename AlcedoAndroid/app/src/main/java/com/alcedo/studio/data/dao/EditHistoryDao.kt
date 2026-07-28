package com.alcedo.studio.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alcedo.studio.data.local.EditTransactionEntity
import com.alcedo.studio.data.local.EditVersionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the non-destructive edit history (version tree + transactions).
 * The cumulative params for a version are stored denormalised for fast replay;
 * transactions are kept for the history panel and undo/redo.
 */
@Dao
interface EditHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(version: EditVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersions(versions: List<EditVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: EditTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(transactions: List<EditTransactionEntity>)

    @Delete
    suspend fun deleteVersion(version: EditVersionEntity)

    @Query("DELETE FROM edit_versions WHERE id = :versionId")
    suspend fun deleteVersionById(versionId: String)

    @Query("DELETE FROM edit_transactions WHERE versionId = :versionId")
    suspend fun deleteTransactionsFor(versionId: String)

    @Query("DELETE FROM edit_versions WHERE imageId = :imageId")
    suspend fun deleteAllForImage(imageId: String)

    @Query("SELECT * FROM edit_versions WHERE id = :versionId")
    suspend fun getVersion(versionId: String): EditVersionEntity?

    @Query("SELECT * FROM edit_versions WHERE imageId = :imageId ORDER BY createdAt ASC")
    suspend fun getVersionsForImage(imageId: String): List<EditVersionEntity>

    @Query("SELECT * FROM edit_versions WHERE imageId = :imageId ORDER BY createdAt ASC")
    fun observeVersionsForImage(imageId: String): Flow<List<EditVersionEntity>>

    @Query("SELECT * FROM edit_transactions WHERE versionId = :versionId ORDER BY timestamp ASC")
    suspend fun getTransactionsFor(versionId: String): List<EditTransactionEntity>

    @Query("SELECT * FROM edit_transactions WHERE versionId = :versionId ORDER BY timestamp ASC")
    fun observeTransactionsFor(versionId: String): Flow<List<EditTransactionEntity>>

    @Query("SELECT * FROM edit_versions WHERE imageId = :imageId AND isActive = 1 LIMIT 1")
    suspend fun getActiveVersion(imageId: String): EditVersionEntity?

    @Query("UPDATE edit_versions SET isActive = 0 WHERE imageId = :imageId")
    suspend fun deactivateAllVersions(imageId: String)

    @Query("UPDATE edit_versions SET isActive = 1 WHERE id = :versionId")
    suspend fun activateVersion(versionId: String)

    @Query("UPDATE edit_versions SET cumulativeParamsJson = :json WHERE id = :versionId")
    suspend fun updateCumulativeParams(versionId: String, json: String)

    @Query("SELECT COUNT(*) FROM edit_versions WHERE imageId = :imageId AND isVirtualCopy = 1")
    suspend fun virtualCopyCount(imageId: String): Int

    @Query("SELECT COUNT(*) FROM edit_versions WHERE imageId = :imageId")
    suspend fun versionCount(imageId: String): Int

    @Transaction
    suspend fun setActiveVersion(imageId: String, versionId: String) {
        deactivateAllVersions(imageId)
        activateVersion(versionId)
    }

    @Transaction
    suspend fun deleteVersionCascade(versionId: String) {
        deleteTransactionsFor(versionId)
        deleteVersionById(versionId)
    }
}
