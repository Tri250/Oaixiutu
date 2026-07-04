package com.alcedo.studio.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.alcedo.studio.data.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

class SleeveDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "alcedo_sleeve.db"
        const val DB_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sleeve_elements (
                element_id INTEGER PRIMARY KEY,
                element_name TEXT NOT NULL,
                element_type INTEGER NOT NULL,
                added_time INTEGER,
                last_modified_time INTEGER,
                ref_count INTEGER DEFAULT 0,
                pinned INTEGER DEFAULT 0,
                sync_flag INTEGER DEFAULT 0,
                parent_id INTEGER,
                image_id INTEGER,
                current_version_id TEXT,
                edit_history_json TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS images (
                image_id INTEGER PRIMARY KEY,
                image_path TEXT NOT NULL,
                image_name TEXT NOT NULL,
                image_type INTEGER DEFAULT 0,
                thumb_state INTEGER DEFAULT 0,
                sync_state INTEGER DEFAULT 0,
                checksum INTEGER DEFAULT 0,
                exif_json TEXT,
                exif_display_json TEXT,
                raw_color_context_json TEXT,
                has_raw_color_context INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS projects (
                project_id TEXT PRIMARY KEY,
                project_name TEXT NOT NULL,
                project_path TEXT NOT NULL,
                sleeve_root_id INTEGER DEFAULT 0,
                created_at INTEGER,
                modified_at INTEGER,
                metadata_json TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS edit_history (
                history_id TEXT PRIMARY KEY,
                bound_image_id INTEGER NOT NULL,
                added_time INTEGER,
                last_modified_time INTEGER,
                default_version_id TEXT,
                active_version_id TEXT,
                import_pipeline_params_json TEXT,
                active_pipeline_params_json TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS versions (
                version_id TEXT PRIMARY KEY,
                history_id TEXT NOT NULL,
                display_name TEXT,
                bound_image_id INTEGER,
                added_time INTEGER,
                last_modified_time INTEGER,
                creation_nonce INTEGER DEFAULT 0,
                materialized_params_json TEXT,
                cursor INTEGER DEFAULT 0,
                version_hash TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
                version_id TEXT NOT NULL,
                operator_type INTEGER,
                params_before_json TEXT,
                params_after_json TEXT,
                timestamp INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS semantic_labels (
                label_id TEXT PRIMARY KEY,
                image_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                confidence REAL,
                model_id TEXT,
                generated_at INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS vector_index (
                image_id INTEGER PRIMARY KEY,
                embedding_blob BLOB,
                model_id TEXT
            )
        """)

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_images_path ON images(image_path)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_elements_parent ON sleeve_elements(parent_id)
        """)
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_labels_image ON semantic_labels(image_id)
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic here
    }

    fun insertElement(element: SleeveElement, parentId: UInt? = null): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("element_id", element.elementId.toInt())
            put("element_name", element.elementName)
            put("element_type", if (element.type == ElementType.FILE) 0 else 1)
            put("added_time", element.addedTime.toEpochMilli())
            put("last_modified_time", element.lastModifiedTime.toEpochMilli())
            put("ref_count", element.refCount.toInt())
            put("pinned", if (element.pinned) 1 else 0)
            put("sync_flag", element.syncFlag.ordinal)
            parentId?.let { put("parent_id", it.toInt()) }
            if (element is SleeveFile) {
                put("image_id", element.imageId.toInt())
                put("current_version_id", element.currentVersionId)
            }
        }
        return db.insertWithOnConflict("sleeve_elements", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getElementById(elementId: UInt): SleeveElement? {
        val db = readableDatabase
        db.query(
            "sleeve_elements",
            null,
            "element_id = ?",
            arrayOf(elementId.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToElement(cursor)
            }
        }
        return null
    }

    fun getChildrenByParentId(parentId: UInt): List<SleeveElement> {
        val list = mutableListOf<SleeveElement>()
        val db = readableDatabase
        db.query(
            "sleeve_elements",
            null,
            "parent_id = ?",
            arrayOf(parentId.toString()),
            null, null, "added_time DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToElement(cursor))
            }
        }
        return list
    }

    fun insertImage(image: ImageModel): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("image_id", image.imageId.toInt())
            put("image_path", image.imagePath)
            put("image_name", image.imageName)
            put("image_type", image.imageType.ordinal)
            put("thumb_state", image.thumbState.ordinal)
            put("sync_state", image.syncState.ordinal)
            put("checksum", image.checksum.toLong())
        }
        return db.insertWithOnConflict("images", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getImageById(imageId: UInt): ImageModel? {
        val db = readableDatabase
        db.query(
            "images", null, "image_id = ?",
            arrayOf(imageId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToImage(cursor)
            }
        }
        return null
    }

    private fun cursorToElement(cursor: android.database.Cursor): SleeveElement {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow("element_id")).toUInt()
        val name = cursor.getString(cursor.getColumnIndexOrThrow("element_name"))
        val type = if (cursor.getInt(cursor.getColumnIndexOrThrow("element_type")) == 0) ElementType.FILE else ElementType.FOLDER
        return if (type == ElementType.FILE) {
            SleeveFile(
                elementId = id,
                elementName = name,
                imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image_id")).toUInt(),
                currentVersionId = cursor.getString(cursor.getColumnIndexOrThrow("current_version_id"))
            )
        } else {
            SleeveFolder(
                elementId = id,
                elementName = name
            )
        }
    }

    private fun cursorToImage(cursor: android.database.Cursor): ImageModel {
        return ImageModel(
            imageId = cursor.getInt(cursor.getColumnIndexOrThrow("image_id")).toUInt(),
            imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
            imageName = cursor.getString(cursor.getColumnIndexOrThrow("image_name")),
            imageType = ImageType.entries[cursor.getInt(cursor.getColumnIndexOrThrow("image_type"))],
            thumbState = ThumbState.entries[cursor.getInt(cursor.getColumnIndexOrThrow("thumb_state"))],
            syncState = ImageSyncState.entries[cursor.getInt(cursor.getColumnIndexOrThrow("sync_state"))],
            checksum = cursor.getLong(cursor.getColumnIndexOrThrow("checksum")).toULong()
        )
    }
}
