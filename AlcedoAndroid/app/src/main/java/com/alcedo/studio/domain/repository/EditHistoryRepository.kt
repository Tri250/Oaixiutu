package com.alcedo.studio.domain.repository

import android.content.ContentValues
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

interface EditHistoryRepository {
    suspend fun saveHistory(history: EditHistory)
    suspend fun getHistory(boundImageId: UInt): EditHistory?
    suspend fun saveVersion(version: Version, historyId: String)
    suspend fun getVersions(historyId: String): List<Version>
}

class EditHistoryRepositoryImpl(private val db: SleeveDatabase) : EditHistoryRepository {

    override suspend fun saveHistory(history: EditHistory) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("history_id", history.historyId)
            put("bound_image_id", history.boundImageId.toInt())
            put("default_version_id", history.defaultVersionId)
            put("active_version_id", history.activeVersionId)
            put("import_pipeline_params_json", history.importPipelineParams.toString())
            put("active_pipeline_params_json", history.activePipelineParams?.toString())
        }
        db.writableDatabase.insertWithOnConflict("edit_history", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun getHistory(boundImageId: UInt): EditHistory? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "edit_history", null, "bound_image_id = ?",
            arrayOf(boundImageId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                EditHistory(
                    historyId = cursor.getString(0),
                    boundImageId = cursor.getInt(1).toUInt(),
                    defaultVersionId = cursor.getString(4) ?: "",
                    activeVersionId = cursor.getString(5) ?: "",
                    importPipelineParams = JsonObject(emptyMap())
                )
            } else null
        }
    }

    override suspend fun saveVersion(version: Version, historyId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("version_id", version.versionId)
            put("history_id", historyId)
            put("display_name", version.displayName)
            put("bound_image_id", version.boundImageId.toInt())
            put("cursor", version.cursor)
            put("version_hash", version.versionHash)
        }
        db.writableDatabase.insertWithOnConflict("versions", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun getVersions(historyId: String): List<Version> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Version>()
        db.readableDatabase.query(
            "versions", null, "history_id = ?",
            arrayOf(historyId), null, null, "added_time ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Version(
                    versionId = cursor.getString(0),
                    displayName = cursor.getString(2) ?: "",
                    boundImageId = cursor.getInt(3).toUInt(),
                    cursor = cursor.getInt(7)
                ))
            }
        }
        list
    }
}
