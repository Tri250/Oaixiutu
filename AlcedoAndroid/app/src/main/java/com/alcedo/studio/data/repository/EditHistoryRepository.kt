package com.alcedo.studio.data.repository

import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.local.EditTransactionEntity
import com.alcedo.studio.data.local.EditVersionEntity
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.TransactionSource
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.utils.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [EditHistoryRepository]. Versions and
 * transactions are persisted via [EditHistoryDao]; cumulative params and deltas
 * are stored as JSON strings serialised with kotlinx.serialization.
 */
@Singleton
class EditHistoryRepositoryImpl @Inject constructor(
    private val dao: EditHistoryDao,
) : EditHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun observeVersions(imageId: String): Flow<List<Version>> =
        dao.observeVersionsForImage(imageId).map { it.map { e -> e.toDomain() } }

    override fun observeTransactions(versionId: String): Flow<List<EditTransaction>> =
        dao.observeTransactionsFor(versionId).map { it.map { e -> e.toDomain() } }

    override suspend fun getHistory(imageId: String): EditHistory? {
        val versions = dao.getVersionsForImage(imageId)
        if (versions.isEmpty()) return null
        val active = versions.firstOrNull { it.isActive } ?: versions.last()
        return EditHistory(
            imageId = imageId,
            versions = versions.map { it.toDomain() },
            currentVersionId = active.id,
        )
    }

    override suspend fun getActiveVersion(imageId: String): Version? =
        dao.getActiveVersion(imageId)?.toDomain()

    override suspend fun getVersions(imageId: String): List<Version> =
        dao.getVersionsForImage(imageId).map { it.toDomain() }

    override suspend fun getTransactions(versionId: String): List<EditTransaction> =
        dao.getTransactionsFor(versionId).map { it.toDomain() }

    override suspend fun createVersion(
        imageId: String,
        parentId: String?,
        name: String,
        cumulativeParamsJson: String,
        isVirtualCopy: Boolean,
    ): Version {
        val id = IdGenerator.newId("ver")
        val now = System.currentTimeMillis()
        dao.upsertVersion(
            EditVersionEntity(
                id = id,
                imageId = imageId,
                parentId = parentId,
                name = name,
                createdAt = now,
                cumulativeParamsJson = cumulativeParamsJson,
                isVirtualCopy = isVirtualCopy,
                isActive = false,
                note = null,
            ),
        )
        return Version(
            id = id,
            imageId = imageId,
            parentId = parentId,
            name = name,
            createdAt = now,
            transactions = emptyList(),
            cumulativeParams = json.decodeFromString(cumulativeParamsJson),
            isVirtualCopy = isVirtualCopy,
            isActive = false,
            note = null,
        )
    }

    override suspend fun addTransaction(transaction: EditTransaction) {
        dao.upsertTransaction(
            EditTransactionEntity(
                id = transaction.id,
                versionId = transaction.versionId,
                timestamp = transaction.timestamp,
                label = transaction.label,
                paramDeltaJson = json.encodeToString(transaction.paramDelta),
                maskIds = transaction.maskIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                source = transaction.source.name,
            ),
        )
    }

    override suspend fun setActiveVersion(imageId: String, versionId: String) =
        dao.setActiveVersion(imageId, versionId)

    override suspend fun updateCumulativeParams(versionId: String, json: String) =
        dao.updateCumulativeParams(versionId, json)

    override suspend fun deleteVersion(versionId: String) = dao.deleteVersionCascade(versionId)

    override suspend fun deleteAllForImage(imageId: String) = dao.deleteAllForImage(imageId)

    override suspend fun versionCount(imageId: String): Int = dao.versionCount(imageId)
    override suspend fun virtualCopyCount(imageId: String): Int = dao.virtualCopyCount(imageId)

    private fun EditVersionEntity.toDomain(): Version = Version(
        id = id,
        imageId = imageId,
        parentId = parentId,
        name = name,
        createdAt = createdAt,
        transactions = emptyList(), // loaded lazily via observeTransactions
        cumulativeParams = runCatching {
            json.decodeFromString<com.alcedo.studio.data.model.AdjustmentParams>(cumulativeParamsJson)
        }.getOrDefault(com.alcedo.studio.data.model.AdjustmentParams.DEFAULT),
        isVirtualCopy = isVirtualCopy,
        isActive = isActive,
        note = note,
    )

    private fun EditTransactionEntity.toDomain(): EditTransaction = EditTransaction(
        id = id,
        versionId = versionId,
        timestamp = timestamp,
        label = label,
        paramDelta = runCatching {
            json.decodeFromString<com.alcedo.studio.data.model.AdjustmentParamsDelta>(paramDeltaJson)
        }.getOrDefault(com.alcedo.studio.data.model.AdjustmentParamsDelta()),
        maskIds = maskIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        source = runCatching { TransactionSource.valueOf(source) }.getOrDefault(TransactionSource.MANUAL),
    )
}
