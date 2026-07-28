package com.alcedo.studio.domain.repository

import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.Version
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for non-destructive edit history (version tree + transactions).
 * Implemented by [com.alcedo.studio.data.repository.EditHistoryRepositoryImpl].
 */
interface EditHistoryRepository {

    fun observeVersions(imageId: String): Flow<List<Version>>
    fun observeTransactions(versionId: String): Flow<List<EditTransaction>>

    suspend fun getHistory(imageId: String): EditHistory?
    suspend fun getActiveVersion(imageId: String): Version?
    suspend fun getVersions(imageId: String): List<Version>
    suspend fun getTransactions(versionId: String): List<EditTransaction>

    suspend fun createVersion(
        imageId: String,
        parentId: String?,
        name: String,
        cumulativeParamsJson: String,
        isVirtualCopy: Boolean,
    ): Version

    suspend fun addTransaction(transaction: EditTransaction)
    suspend fun setActiveVersion(imageId: String, versionId: String)
    suspend fun updateCumulativeParams(versionId: String, json: String)
    suspend fun deleteVersion(versionId: String)
    suspend fun deleteAllForImage(imageId: String)

    suspend fun versionCount(imageId: String): Int
    suspend fun virtualCopyCount(imageId: String): Int
}
