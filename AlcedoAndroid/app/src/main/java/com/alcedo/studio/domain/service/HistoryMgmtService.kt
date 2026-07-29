package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.AdjustmentParamsDelta
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.TransactionSource
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.data.model.applyDelta
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Edit history management. Drives the version tree panel: creating versions,
 * virtual copies, recording transactions, undo/redo and switching the active
 * version the pipeline replays.
 */
@Singleton
class HistoryMgmtService @Inject constructor(
    private val editHistoryRepository: EditHistoryRepository,
    private val imageRepository: ImageRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeVersions(imageId: String): Flow<List<Version>> =
        editHistoryRepository.observeVersions(imageId)

    fun observeTransactions(versionId: String): Flow<List<EditTransaction>> =
        editHistoryRepository.observeTransactions(versionId)

    /** Initialise an edit history for an image if none exists. */
    suspend fun ensureHistory(imageId: String): Version? = withContext(ThreadPool.database) {
        val existing = editHistoryRepository.getActiveVersion(imageId)
        if (existing != null) return@withContext existing
        val version = editHistoryRepository.createVersion(
            imageId = imageId,
            parentId = null,
            name = "Original",
            cumulativeParamsJson = json.encodeToString(AdjustmentParams.DEFAULT),
            isVirtualCopy = false,
        )
        editHistoryRepository.setActiveVersion(imageId, version.id)
        imageRepository.setCurrentVersion(imageId, version.id)
        version
    }

    /** Record an adjustment change as a transaction on the active version. */
    suspend fun recordChange(imageId: String, delta: AdjustmentParamsDelta, label: String): EditTransaction? =
        withContext(ThreadPool.database) {
            val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext null
            val transaction = EditTransaction(
                id = IdGenerator.newId("tx"),
                versionId = active.id,
                timestamp = System.currentTimeMillis(),
                label = label,
                paramDelta = delta,
                source = TransactionSource.MANUAL,
            )
            editHistoryRepository.addTransaction(transaction)
            val updated = active.cumulativeParams.applyDelta(delta)
            editHistoryRepository.updateCumulativeParams(active.id, json.encodeToString(updated))
            transaction
        }

    /** Create a virtual copy of [imageId] (a new version branching from the active one). */
    suspend fun createVirtualCopy(imageId: String, name: String): Version? = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext null
        val copy = editHistoryRepository.createVersion(
            imageId = imageId,
            parentId = active.id,
            name = name,
            cumulativeParamsJson = json.encodeToString(active.cumulativeParams),
            isVirtualCopy = true,
        )
        editHistoryRepository.setActiveVersion(imageId, copy.id)
        imageRepository.setCurrentVersion(imageId, copy.id)
        copy
    }

    /** Switch the active version for [imageId]. */
    suspend fun switchVersion(imageId: String, versionId: String) = withContext(ThreadPool.database) {
        editHistoryRepository.setActiveVersion(imageId, versionId)
        imageRepository.setCurrentVersion(imageId, versionId)
    }

    /** Undo the last transaction on the active version (best-effort). */
    suspend fun undo(imageId: String) = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext
        val txs = editHistoryRepository.getTransactions(active.id)
        if (txs.isEmpty()) return@withContext
        val last = txs.last()
        // Record an inverse marker transaction (full recompute handled by replay).
        editHistoryRepository.addTransaction(
            last.copy(
                id = IdGenerator.newId("tx"),
                timestamp = System.currentTimeMillis(),
                label = "Undo: ${last.label}",
                source = TransactionSource.UNDO,
            ),
        )
    }

    /** Redo the last undone transaction on the active version (best-effort). */
    suspend fun redo(imageId: String) = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext
        val txs = editHistoryRepository.getTransactions(active.id)
        // Find the most recent UNDO marker; replay the original transaction it
        // negated as a REDO marker so the version tree stays replayable.
        val undoIdx = txs.indexOfLast { it.source == TransactionSource.UNDO }
        if (undoIdx < 0 || undoIdx == 0) return@withContext
        val undone = txs[undoIdx - 1]
        editHistoryRepository.addTransaction(
            undone.copy(
                id = IdGenerator.newId("tx"),
                timestamp = System.currentTimeMillis(),
                label = "Redo: ${undone.label}",
                source = TransactionSource.REDO,
            ),
        )
    }

    /** Delete a version (and its transactions) from the tree. */
    suspend fun deleteVersion(imageId: String, versionId: String) = withContext(ThreadPool.database) {
        editHistoryRepository.deleteVersion(versionId)
    }

    /** Full history tree for [imageId]. */
    suspend fun getHistory(imageId: String): EditHistory? = withContext(ThreadPool.database) {
        editHistoryRepository.getHistory(imageId)
    }
}
