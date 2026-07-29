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

    /**
     * Undo/redo cursor per version id: the number of trailing transactions that
     * are currently "undone" (inactive). 0 means nothing is undone (all
     * transactions applied). Used to recompute [Version.cumulativeParams] by
     * replaying only the active prefix of the transaction list.
     */
    private val undoCursor = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun observeVersions(imageId: String): Flow<List<Version>> =
        editHistoryRepository.observeVersions(imageId)

    fun observeTransactions(versionId: String): Flow<List<EditTransaction>> =
        editHistoryRepository.observeTransactions(versionId)

    /** Get the currently active version for [imageId]. */
    suspend fun getActiveVersion(imageId: String): Version? = withContext(ThreadPool.database) {
        editHistoryRepository.getActiveVersion(imageId)
    }

    /** Initialise an edit history for an image if none exists. */
    suspend fun ensureHistory(imageId: String): Version? = withContext(ThreadPool.database) {
        val existing = editHistoryRepository.getActiveVersion(imageId)
        if (existing != null) return@withContext existing
        val version = editHistoryRepository.createVersion(
            imageId = imageId,
            parentId = null,
            name = "Original",
            cumulativeParamsJson = json.encodeToString(AdjustmentParams.serializer(), AdjustmentParams.DEFAULT),
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
            // A new edit after undos discards the redo tail: reset the cursor so
            // every transaction is active again before appending the new one.
            undoCursor.remove(active.id)
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
            editHistoryRepository.updateCumulativeParams(active.id, json.encodeToString(AdjustmentParams.serializer(), updated))
            transaction
        }

    /** Create a virtual copy of [imageId] (a new version branching from the active one). */
    suspend fun createVirtualCopy(imageId: String, name: String): Version? = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext null
        val copy = editHistoryRepository.createVersion(
            imageId = imageId,
            parentId = active.id,
            name = name,
            cumulativeParamsJson = json.encodeToString(AdjustmentParams.serializer(), active.cumulativeParams),
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

    /**
     * Undo the last active transaction on the active version. Rather than
     * appending a marker, this advances an in-memory undo cursor and
     * recomputes [Version.cumulativeParams] by replaying only the still-active
     * prefix of the transaction list from the base state. The editor reloads
     * the pipeline from the updated cumulative params.
     */
    suspend fun undo(imageId: String) = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext
        val txs = editHistoryRepository.getTransactions(active.id)
        if (txs.isEmpty()) return@withContext
        val cursor = undoCursor[active.id] ?: 0
        if (cursor >= txs.size) return@withContext // nothing left to undo
        val nextCursor = cursor + 1
        undoCursor[active.id] = nextCursor
        val replayed = replayPrefix(txs, txs.size - nextCursor)
        editHistoryRepository.updateCumulativeParams(active.id, json.encodeToString<AdjustmentParams>(replayed))
    }

    /**
     * Redo the most recently undone transaction on the active version. Decrements
     * the undo cursor and recomputes cumulative params by replaying the larger
     * active prefix.
     */
    suspend fun redo(imageId: String) = withContext(ThreadPool.database) {
        val active = editHistoryRepository.getActiveVersion(imageId) ?: return@withContext
        val txs = editHistoryRepository.getTransactions(active.id)
        if (txs.isEmpty()) return@withContext
        val cursor = undoCursor[active.id] ?: 0
        if (cursor <= 0) return@withContext // nothing to redo
        val nextCursor = cursor - 1
        if (nextCursor == 0) undoCursor.remove(active.id) else undoCursor[active.id] = nextCursor
        val replayed = replayPrefix(txs, txs.size - nextCursor)
        editHistoryRepository.updateCumulativeParams(active.id, json.encodeToString<AdjustmentParams>(replayed))
    }

    /**
     * Replay the first [count] transactions' param deltas onto the base
     * [AdjustmentParams.DEFAULT], producing the cumulative params for that
     * prefix. Skips no-op UNDO/REDO marker transactions emitted by legacy code.
     */
    private fun replayPrefix(transactions: List<EditTransaction>, count: Int): AdjustmentParams {
        var params = AdjustmentParams.DEFAULT
        val n = count.coerceIn(0, transactions.size)
        for (i in 0 until n) {
            val tx = transactions[i]
            if (tx.source == TransactionSource.UNDO || tx.source == TransactionSource.REDO) continue
            params = params.applyDelta(tx.paramDelta)
        }
        return params
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
