package com.alcedo.studio.domain.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.time.Instant

interface EditHistoryRepository {
    suspend fun saveHistory(history: EditHistory)
    suspend fun getHistory(boundImageId: UInt): EditHistory?
    suspend fun getHistoryById(historyId: String): EditHistory?
    suspend fun deleteHistory(boundImageId: UInt)
    suspend fun saveVersion(version: Version, historyId: String)
    suspend fun getVersions(historyId: String): List<Version>
    suspend fun getVersion(versionId: String): Version?
    suspend fun saveTransaction(transaction: EditTransaction, versionId: String)
    suspend fun getTransactions(versionId: String): List<EditTransaction>
    suspend fun deleteTransactions(versionId: String)
    suspend fun cloneHistory(sourceImageId: UInt, targetImageId: UInt): EditHistory?
    suspend fun computeMerkleHash(historyId: String): String
    suspend fun verifyMerkleHash(historyId: String): Boolean
}

class EditHistoryRepositoryImpl(private val db: SleeveDatabase) : EditHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val writableDb get() = db.openHelper.writableDatabase
    private val readableDb get() = db.openHelper.readableDatabase

    override suspend fun saveHistory(history: EditHistory) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("history_id", history.historyId)
            put("bound_image_id", history.boundImageId.toInt())
            put("added_time", history.addedTime.toEpochMilli())
            put("last_modified_time", history.lastModifiedTime.toEpochMilli())
            put("default_version_id", history.defaultVersionId)
            put("active_version_id", history.activeVersionId)
            put("import_pipeline_params_json", history.importPipelineParams.toString())
            put("active_pipeline_params_json", history.activePipelineParams?.toString())
        }
        writableDb.insert("edit_history", SQLiteDatabase.CONFLICT_REPLACE, values)

        // Save all versions
        for (version in history.versionStorage.values) {
            saveVersion(version, history.historyId)
        }
    }

    override suspend fun getHistory(boundImageId: UInt): EditHistory? = withContext(Dispatchers.IO) {
        readableDb.query("SELECT * FROM edit_history WHERE bound_image_id = ?", arrayOf(boundImageId.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                cursorToHistory(cursor)
            } else null
        }
    }

    override suspend fun getHistoryById(historyId: String): EditHistory? = withContext(Dispatchers.IO) {
        readableDb.query("SELECT * FROM edit_history WHERE history_id = ?", arrayOf(historyId)).use { cursor ->
            if (cursor.moveToFirst()) {
                cursorToHistory(cursor)
            } else null
        }
    }

    override suspend fun deleteHistory(boundImageId: UInt) = withContext(Dispatchers.IO) {
        val history = getHistory(boundImageId) ?: return@withContext
        // Delete all versions and their transactions
        for (version in getVersions(history.historyId)) {
            deleteTransactions(version.versionId)
        }
        writableDb.delete("versions", "history_id = ?", arrayOf(history.historyId))
        writableDb.delete("edit_history", "bound_image_id = ?", arrayOf(boundImageId.toString()))
    }

    override suspend fun saveVersion(version: Version, historyId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("version_id", version.versionId)
            put("history_id", historyId)
            put("display_name", version.displayName)
            put("bound_image_id", version.boundImageId.toInt())
            put("added_time", version.addedTime.toEpochMilli())
            put("last_modified_time", version.lastModifiedTime.toEpochMilli())
            put("creation_nonce", version.creationNonce.toLong())
            put("materialized_params_json", version.materializedParams?.toString())
            put("cursor", version.cursor)
            put("version_hash", version.versionHash)
        }
        writableDb.insert("versions", SQLiteDatabase.CONFLICT_REPLACE, values)

        // Save transactions
        for (tx in version.transactions) {
            saveTransaction(tx, version.versionId)
        }
    }

    override suspend fun getVersions(historyId: String): List<Version> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Version>()
        readableDb.query("SELECT * FROM versions WHERE history_id = ? ORDER BY added_time ASC", arrayOf(historyId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToVersion(cursor))
            }
        }
        list
    }

    override suspend fun getVersion(versionId: String): Version? = withContext(Dispatchers.IO) {
        readableDb.query("SELECT * FROM versions WHERE version_id = ?", arrayOf(versionId)).use { cursor ->
            if (cursor.moveToFirst()) {
                cursorToVersion(cursor)
            } else null
        }
    }

    override suspend fun saveTransaction(
        transaction: EditTransaction,
        versionId: String
    ): Unit = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("version_id", versionId)
            put("operator_type", transaction.operatorType.ordinal)
            put("params_before_json", transaction.paramsBefore.toString())
            put("params_after_json", transaction.paramsAfter.toString())
            put("timestamp", transaction.timestamp.toEpochMilli())
        }
        writableDb.insert("transactions", SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    override suspend fun getTransactions(versionId: String): List<EditTransaction> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EditTransaction>()
        readableDb.query("SELECT * FROM transactions WHERE version_id = ? ORDER BY transaction_id ASC", arrayOf(versionId)).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToTransaction(cursor))
            }
        }
        list
    }

    override suspend fun deleteTransactions(versionId: String): Unit = withContext(Dispatchers.IO) {
        writableDb.delete("transactions", "version_id = ?", arrayOf(versionId))
    }

    override suspend fun cloneHistory(
        sourceImageId: UInt,
        targetImageId: UInt
    ): EditHistory? = withContext(Dispatchers.IO) {
        val sourceHistory = getHistory(sourceImageId) ?: return@withContext null

        val cloned = sourceHistory.cloneForFile(targetImageId)
        saveHistory(cloned)

        cloned
    }

    override suspend fun computeMerkleHash(historyId: String): String = withContext(Dispatchers.Default) {
        val md = MessageDigest.getInstance("SHA-256")
        val versions = getVersions(historyId)

        md.update(historyId.toByteArray())

        for (version in versions) {
            md.update(version.versionId.toByteArray())
            md.update(version.boundImageId.toString().toByteArray())
            md.update(version.cursor.toString().toByteArray())

            val transactions = getTransactions(version.versionId)
            for (tx in transactions) {
                md.update(tx.transactionId.toString().toByteArray())
                md.update(tx.operatorType.name.toByteArray())
                md.update(tx.paramsAfter.toString().toByteArray())
                md.update(tx.timestamp.toEpochMilli().toString().toByteArray())
            }
        }

        md.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    override suspend fun verifyMerkleHash(historyId: String): Boolean = withContext(Dispatchers.Default) {
        val computedHash = computeMerkleHash(historyId)
        val history = getHistoryById(historyId) ?: return@withContext false
        val activeVersion = history.getActiveVersion() ?: return@withContext false
        activeVersion.versionHash == computedHash
    }

    // ── Cursor Parsing ──

    private suspend fun cursorToHistory(cursor: android.database.Cursor): EditHistory {
        val historyId = cursor.getString(cursor.getColumnIndexOrThrow("history_id"))
        val boundImageId = cursor.getInt(cursor.getColumnIndexOrThrow("bound_image_id")).toUInt()
        val importParamsStr = cursor.getString(cursor.getColumnIndexOrThrow("import_pipeline_params_json"))
        val activeParamsStr = cursor.getString(cursor.getColumnIndexOrThrow("active_pipeline_params_json"))

        // Load versions
        val versions = getVersions(historyId)
        val versionStorage = versions.associateBy { it.versionId }.toMutableMap()
        val versionOrder = versions.map { VersionNode(it.versionId, "") }.toMutableList()

        return EditHistory(
            historyId = historyId,
            boundImageId = boundImageId,
            addedTime = cursor.getLong(cursor.getColumnIndexOrThrow("added_time"))
                .let { Instant.ofEpochMilli(it) },
            lastModifiedTime = cursor.getLong(cursor.getColumnIndexOrThrow("last_modified_time"))
                .let { Instant.ofEpochMilli(it) },
            defaultVersionId = cursor.getString(cursor.getColumnIndexOrThrow("default_version_id")) ?: "",
            activeVersionId = cursor.getString(cursor.getColumnIndexOrThrow("active_version_id")) ?: "",
            importPipelineParams = importParamsStr?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap()),
            activePipelineParams = activeParamsStr?.let { JsonObject(emptyMap()) },
            versionOrder = versionOrder,
            versionStorage = versionStorage
        )
    }

    private suspend fun cursorToVersion(cursor: android.database.Cursor): Version {
        val versionId = cursor.getString(cursor.getColumnIndexOrThrow("version_id"))
        val transactions = getTransactions(versionId)

        return Version(
            versionId = versionId,
            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")) ?: "",
            boundImageId = cursor.getInt(cursor.getColumnIndexOrThrow("bound_image_id")).toUInt(),
            addedTime = cursor.getLong(cursor.getColumnIndexOrThrow("added_time"))
                .let { Instant.ofEpochMilli(it) },
            lastModifiedTime = cursor.getLong(cursor.getColumnIndexOrThrow("last_modified_time"))
                .let { Instant.ofEpochMilli(it) },
            creationNonce = cursor.getLong(cursor.getColumnIndexOrThrow("creation_nonce")).toULong(),
            materializedParams = cursor.getString(cursor.getColumnIndexOrThrow("materialized_params_json"))
                ?.let { JsonObject(emptyMap()) },
            transactions = transactions.toMutableList(),
            cursor = cursor.getInt(cursor.getColumnIndexOrThrow("cursor")),
            versionHash = cursor.getString(cursor.getColumnIndexOrThrow("version_hash")) ?: ""
        )
    }

    private fun cursorToTransaction(cursor: android.database.Cursor): EditTransaction {
        val paramsBefore = cursor.getString(cursor.getColumnIndexOrThrow("params_before_json"))
            ?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap())
        val paramsAfter = cursor.getString(cursor.getColumnIndexOrThrow("params_after_json"))
            ?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap())

        return EditTransaction(
            transactionId = cursor.getInt(cursor.getColumnIndexOrThrow("transaction_id")).toUInt(),
            operatorType = OperatorType.entries[cursor.getInt(cursor.getColumnIndexOrThrow("operator_type"))],
            paramsBefore = paramsBefore,
            paramsAfter = paramsAfter,
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                .let { Instant.ofEpochMilli(it) }
        )
    }
}