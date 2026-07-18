package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

// TODO(dead-code): 历史管理服务未接入 UI。当前编辑历史由 EditorViewModel 直接通过 EditHistoryRepository 管理，未使用此服务层。
//   待多图工程/批量编辑的统一历史管理功能上线时启用。

class HistoryMgmtService(
    private val editHistoryRepository: EditHistoryRepository
) {
    companion object {
        private const val TAG = "HistoryMgmtService"
        private const val MAX_TRANSACTIONS_BEFORE_COMPRESS = 100
        private const val COMPRESS_WINDOW_SIMILAR = 5
        private const val MAX_ACTIVE_HISTORIES = 32
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Bounded LRU cache (prevents unbounded memory growth as more images are edited)
    private val activeHistories: MutableMap<UInt, EditHistory> = Collections.synchronizedMap(
        object : LinkedHashMap<UInt, EditHistory>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UInt, EditHistory>): Boolean =
                size > MAX_ACTIVE_HISTORIES
        }
    )
    private val workingVersions = ConcurrentHashMap<UInt, WorkingVersion>()

    private val _historyState = MutableStateFlow<Map<UInt, HistoryState>>(emptyMap())
    val historyState: StateFlow<Map<UInt, HistoryState>> = _historyState.asStateFlow()

    data class HistoryState(
        val imageId: UInt,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val versionCount: Int = 0,
        val activeVersionId: String = "",
        val transactionCount: Int = 0
    )

    // ── Version Tree Management ──

    suspend fun loadHistory(imageId: UInt): EditHistory = withContext(Dispatchers.IO) {
        activeHistories[imageId]?.let { return@withContext it }

        val history = editHistoryRepository.getHistory(imageId)
            ?: EditHistory(boundImageId = imageId)

        activeHistories[imageId] = history
        updateHistoryState(imageId, history)
        history
    }

    suspend fun createVersion(
        imageId: UInt,
        displayName: String = ""
    ): String = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val versionId = history.createVersion(displayName.ifEmpty { "Version ${history.versionOrder.size + 1}" })
        persistHistory(history)
        updateHistoryState(imageId, history)
        versionId
    }

    suspend fun renameVersion(
        imageId: UInt,
        versionId: String,
        newName: String
    ) = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val version = history.getVersion(versionId) ?: return@withContext
        history.versionStorage[versionId] = version.copy(displayName = newName)
        persistHistory(history)
        updateHistoryState(imageId, history)
    }

    suspend fun deleteVersion(
        imageId: UInt,
        versionId: String
    ) = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        if (versionId == history.defaultVersionId) return@withContext
        if (history.versionStorage.size <= 1) return@withContext

        history.versionStorage.remove(versionId)
        history.versionOrder.removeAll { it.versionId == versionId }

        if (history.activeVersionId == versionId) {
            history.activeVersionId = history.defaultVersionId
        }

        persistHistory(history)
        updateHistoryState(imageId, history)
    }

    suspend fun switchVersion(
        imageId: UInt,
        versionId: String
    ): Version? = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val version = history.getVersion(versionId) ?: return@withContext null

        // Save current working version state
        saveWorkingVersion(imageId)

        history.setActiveVersion(versionId)
        persistHistory(history)
        updateHistoryState(imageId, history)

        // Load the new working version
        loadWorkingVersion(imageId, version)

        version
    }

    suspend fun getVersionList(imageId: UInt): List<VersionNode> = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        history.versionOrder.toList()
    }

    suspend fun getVersion(imageId: UInt, versionId: String): Version? = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        history.getVersion(versionId)
    }

    // ── Undo / Redo (Cursor-Based Transaction Log) ──

    suspend fun recordTransaction(
        imageId: UInt,
        operatorType: OperatorType,
        paramsBefore: JsonObject,
        paramsAfter: JsonObject
    ): EditTransaction = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val activeVersion = history.getActiveVersion()
            ?: throw IllegalStateException("No active version for image $imageId")

        val tx = EditTransaction(
            transactionId = (activeVersion.transactions.size + 1).toUInt(),
            operatorType = operatorType,
            paramsBefore = paramsBefore,
            paramsAfter = paramsAfter,
            timestamp = Instant.now()
        )

        activeVersion.appendTransaction(tx)
        history.lastModifiedTime = Instant.now()

        // Auto-compress if needed
        if (activeVersion.transactions.size > MAX_TRANSACTIONS_BEFORE_COMPRESS) {
            compressTransactions(imageId)
        }

        persistHistory(history)
        updateHistoryState(imageId, history)
        tx
    }

    suspend fun undo(imageId: UInt): EditTransaction? = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val version = history.getActiveVersion() ?: return@withContext null

        val tx = version.undo()
        if (tx != null) {
            history.lastModifiedTime = Instant.now()
            persistHistory(history)
            updateHistoryState(imageId, history)
        }
        tx
    }

    suspend fun redo(imageId: UInt): EditTransaction? = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val version = history.getActiveVersion() ?: return@withContext null

        val tx = version.redo()
        if (tx != null) {
            history.lastModifiedTime = Instant.now()
            persistHistory(history)
            updateHistoryState(imageId, history)
        }
        tx
    }

    fun canUndo(imageId: UInt): Boolean {
        val history = activeHistories[imageId] ?: return false
        val version = history.getActiveVersion() ?: return false
        return version.cursor > 0
    }

    fun canRedo(imageId: UInt): Boolean {
        val history = activeHistories[imageId] ?: return false
        val version = history.getActiveVersion() ?: return false
        return version.cursor < version.transactions.size
    }

    // ── Version Cloning ──

    suspend fun cloneVersionToImage(
        sourceImageId: UInt,
        sourceVersionId: String,
        targetImageId: UInt
    ): String = withContext(Dispatchers.IO) {
        val sourceHistory = loadHistory(sourceImageId)
        val sourceVersion = sourceHistory.getVersion(sourceVersionId)
            ?: throw IllegalStateException("Source version not found")

        val targetHistory = editHistoryRepository.getHistory(targetImageId)
            ?: EditHistory(boundImageId = targetImageId)

        val clonedVersionId = targetHistory.createVersion(
            "Cloned from ${sourceVersion.displayName}"
        )

        val clonedVersion = targetHistory.getVersion(clonedVersionId)!!
        clonedVersion.transactions.clear()
        clonedVersion.transactions.addAll(sourceVersion.transactions.map { it.copy() })
        clonedVersion.cursor = sourceVersion.cursor

        targetHistory.versionStorage[clonedVersionId] = clonedVersion
        targetHistory.setActiveVersion(clonedVersionId)

        editHistoryRepository.saveHistory(targetHistory)
        activeHistories[targetImageId] = targetHistory
        updateHistoryState(targetImageId, targetHistory)

        clonedVersionId
    }

    // ── A/B Comparison ──

    suspend fun compareVersions(
        imageId: UInt,
        versionIdA: String,
        versionIdB: String
    ): VersionComparisonResult = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val versionA = history.getVersion(versionIdA) ?: throw IllegalStateException("Version A not found")
        val versionB = history.getVersion(versionIdB) ?: throw IllegalStateException("Version B not found")

        val txCountA = versionA.transactions.size
        val txCountB = versionB.transactions.size
        val cursorDiff = versionA.cursor - versionB.cursor

        VersionComparisonResult(
            versionIdA = versionIdA,
            versionIdB = versionIdB,
            transactionCountDiff = txCountA - txCountB,
            cursorDiff = cursorDiff,
            isDifferent = versionA.versionHash != versionB.versionHash ||
                txCountA != txCountB ||
                versionA.cursor != versionB.cursor
        )
    }

    data class VersionComparisonResult(
        val versionIdA: String,
        val versionIdB: String,
        val transactionCountDiff: Int,
        val cursorDiff: Int,
        val isDifferent: Boolean
    )

    // ── Import Baseline Preservation ──

    suspend fun preserveImportBaseline(imageId: UInt, importParams: JsonObject) = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val baseline = history.copy(importPipelineParams = importParams)
        // Ensure default version preserves the import baseline
        baseline.defaultVersionId.let { defaultId ->
            val defaultVersion = baseline.getVersion(defaultId)
            if (defaultVersion != null && defaultVersion.transactions.isEmpty()) {
                defaultVersion.materializedParams = importParams
            }
        }
        persistHistory(baseline)
        activeHistories[imageId] = baseline
    }

    suspend fun getImportBaseline(imageId: UInt): JsonObject = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        history.importPipelineParams
    }

    // ── Transaction Compression ──

    suspend fun compressTransactions(imageId: UInt) = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val version = history.getActiveVersion() ?: return@withContext

        val transactions = version.transactions
        if (transactions.size < COMPRESS_WINDOW_SIMILAR * 2) return@withContext

        val compressed = mutableListOf<EditTransaction>()
        var i = 0
        while (i < transactions.size) {
            val current = transactions[i]
            var merged = current

            // Look ahead for similar adjacent operations
            var j = i + 1
            while (j < transactions.size && j - i < COMPRESS_WINDOW_SIMILAR) {
                val next = transactions[j]
                if (next.operatorType == current.operatorType) {
                    // Merge: keep the latest paramsAfter as the merged result
                    merged = merged.copy(
                        paramsAfter = next.paramsAfter,
                        timestamp = next.timestamp
                    )
                    j++
                } else {
                    break
                }
            }

            compressed.add(merged)
            i = j
        }

        val cursorAdjustment = transactions.size - compressed.size
        version.transactions.clear()
        version.transactions.addAll(compressed)
        version.cursor = (version.cursor - cursorAdjustment).coerceAtLeast(0)

        history.lastModifiedTime = Instant.now()
        persistHistory(history)
        updateHistoryState(imageId, history)
    }

    // ── Merkle Tree Hash ──

    suspend fun computeMerkleHash(imageId: UInt): String = withContext(Dispatchers.Default) {
        val history = loadHistory(imageId)
        val version = history.getActiveVersion() ?: return@withContext ""

        val md = MessageDigest.getInstance("SHA-256")

        // Hash each transaction
        for (tx in version.appliedTransactions()) {
            md.update(tx.transactionId.toString().toByteArray())
            md.update(tx.operatorType.name.toByteArray())
            md.update(tx.paramsAfter.toString().toByteArray())
            md.update(tx.timestamp.toEpochMilli().toString().toByteArray())
        }

        // Hash version metadata
        md.update(version.versionId.toByteArray())
        md.update(version.boundImageId.toString().toByteArray())
        md.update(version.cursor.toString().toByteArray())

        val hash = md.digest().take(16).joinToString("") { "%02x".format(it) }
        version.versionHash = hash
        hash
    }

    suspend fun verifyMerkleHash(imageId: UInt): Boolean = withContext(Dispatchers.Default) {
        val history = loadHistory(imageId)
        val version = history.getActiveVersion() ?: return@withContext false
        val computed = computeMerkleHash(imageId)
        version.versionHash == computed
    }

    // ── History Serialization / Deserialization ──

    suspend fun serializeHistory(imageId: UInt): String = withContext(Dispatchers.IO) {
        val history = loadHistory(imageId)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                serializer<String>(),
                serializer<String>()
            ),
            mapOf(
                "historyId" to history.historyId,
                "boundImageId" to history.boundImageId.toString(),
                "addedTime" to history.addedTime.toEpochMilli().toString(),
                "lastModifiedTime" to history.lastModifiedTime.toEpochMilli().toString(),
                "defaultVersionId" to history.defaultVersionId,
                "activeVersionId" to history.activeVersionId,
                "importPipelineParams" to history.importPipelineParams.toString(),
                "activePipelineParams" to (history.activePipelineParams?.toString() ?: ""),
                "versionCount" to history.versionOrder.size.toString(),
                "activeVersionHash" to (history.getActiveVersion()?.versionHash ?: "")
            )
        )
    }

    suspend fun deserializeHistory(imageId: UInt, data: String): EditHistory = withContext(Dispatchers.IO) {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val map = json.decodeFromString<Map<String, String>>(data)

        EditHistory(
            historyId = map["historyId"] ?: generateHash128(),
            boundImageId = imageId,
            addedTime = map["addedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            lastModifiedTime = map["lastModifiedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            defaultVersionId = map["defaultVersionId"] ?: "",
            activeVersionId = map["activeVersionId"] ?: "",
            importPipelineParams = JsonObject(emptyMap()),
            activePipelineParams = map["activePipelineParams"]?.takeIf { it.isNotEmpty() }?.let { JsonObject(emptyMap()) }
        )
    }

    // ── Working Version Management ──

    private fun loadWorkingVersion(imageId: UInt, version: Version) {
        val wv = WorkingVersion(
            versionId = version.versionId,
            boundImageId = version.boundImageId,
            cursor = version.cursor,
            headPipelineParams = version.materializedParams
        )
        wv.transactions.addAll(version.transactions)
        workingVersions[imageId] = wv
    }

    private fun saveWorkingVersion(imageId: UInt) {
        val wv = workingVersions[imageId] ?: return
        val history = activeHistories[imageId] ?: return
        val version = history.getActiveVersion() ?: return

        version.transactions.clear()
        version.transactions.addAll(wv.transactions)
        version.cursor = wv.cursor
        version.materializedParams = wv.headPipelineParams
    }

    fun getWorkingVersion(imageId: UInt): WorkingVersion? = workingVersions[imageId]

    // ── Cleanup ──

    suspend fun releaseHistory(imageId: UInt) = withContext(Dispatchers.IO) {
        saveWorkingVersion(imageId)
        activeHistories.remove(imageId)
        workingVersions.remove(imageId)
        _historyState.value = _historyState.value - imageId
    }

    fun releaseAll() {
        scope.launch {
            // Snapshot keys to avoid ConcurrentModificationException while
            // releaseHistory() removes entries from the same map.
            activeHistories.keys.toList().forEach { releaseHistory(it) }
        }
    }

    // ── Private Helpers ──

    private suspend fun persistHistory(history: EditHistory) = withContext(Dispatchers.IO) {
        editHistoryRepository.saveHistory(history)
        for (version in history.versionStorage.values) {
            editHistoryRepository.saveVersion(version, history.historyId)
        }
    }

    private fun updateHistoryState(imageId: UInt, history: EditHistory) {
        val version = history.getActiveVersion()
        _historyState.value = _historyState.value + (imageId to HistoryState(
            imageId = imageId,
            canUndo = (version?.cursor ?: 0) > 0,
            canRedo = (version?.cursor ?: 0) < (version?.transactions?.size ?: 0),
            versionCount = history.versionOrder.size,
            activeVersionId = history.activeVersionId,
            transactionCount = version?.transactions?.size ?: 0
        ))
    }
}