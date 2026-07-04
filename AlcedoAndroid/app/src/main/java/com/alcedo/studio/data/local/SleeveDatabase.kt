package com.alcedo.studio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alcedo.studio.data.model.*

@Database(
    entities = [
        SleeveElementEntity::class,
        SleeveFileEntity::class,
        SleeveFolderEntity::class,
        ImageMetadataEntity::class,
        SemanticLabelEntity::class,
        VectorIndexEntity::class,
        RatingEntity::class,
        CollectionEntity::class,
        CollectionImageEntity::class,
        FilterPresetEntity::class,
        ElementFts::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(SleeveTypeConverters::class)
abstract class SleeveDatabase : RoomDatabase() {

    abstract fun sleeveElementDao(): SleeveElementDao
    abstract fun sleeveFileDao(): SleeveFileDao
    abstract fun sleeveFolderDao(): SleeveFolderDao
    abstract fun imageMetadataDao(): ImageMetadataDao
    abstract fun ratingDao(): RatingDao
    abstract fun semanticLabelDao(): SemanticLabelDao
    abstract fun collectionDao(): CollectionDao
    abstract fun filterDao(): FilterDao

    companion object {
        const val DATABASE_NAME = "alcedo_sleeve.db"

        @Volatile
        private var INSTANCE: SleeveDatabase? = null

        fun getInstance(context: Context): SleeveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SleeveDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SleeveDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Create FTS virtual tables
                        db.execSQL("""
                            CREATE VIRTUAL TABLE IF NOT EXISTS element_name_fts USING fts4(
                                content='element_fts',
                                element_name
                            )
                        """)
                        db.execSQL("""
                            CREATE VIRTUAL TABLE IF NOT EXISTS label_fts USING fts4(
                                content='semantic_labels',
                                label
                            )
                        """)
                        // Create triggers to keep FTS in sync
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS element_fts_insert AFTER INSERT ON element_fts
                            BEGIN
                                INSERT INTO element_name_fts(docid, element_name) VALUES (new.element_id, new.element_name);
                            END
                        """)
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS element_fts_delete AFTER DELETE ON element_fts
                            BEGIN
                                DELETE FROM element_name_fts WHERE docid = old.element_id;
                            END
                        """)
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS element_fts_update AFTER UPDATE ON element_fts
                            BEGIN
                                DELETE FROM element_name_fts WHERE docid = old.element_id;
                                INSERT INTO element_name_fts(docid, element_name) VALUES (new.element_id, new.element_name);
                            END
                        """)
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS label_fts_insert AFTER INSERT ON semantic_labels
                            BEGIN
                                INSERT INTO label_fts(docid, label) VALUES (new.rowid, new.label);
                            END
                        """)
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS label_fts_delete AFTER DELETE ON semantic_labels
                            BEGIN
                                DELETE FROM label_fts WHERE docid = old.rowid;
                            END
                        """)
                        db.execSQL("""
                            CREATE TRIGGER IF NOT EXISTS label_fts_update AFTER UPDATE ON semantic_labels
                            BEGIN
                                DELETE FROM label_fts WHERE docid = old.rowid;
                                INSERT INTO label_fts(docid, label) VALUES (new.rowid, new.label);
                            END
                        """)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        db.execSQL("PRAGMA journal_mode = WAL")
                        db.execSQL("PRAGMA synchronous = NORMAL")
                    }
                })
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to existing tables
                db.execSQL("ALTER TABLE sleeve_elements ADD COLUMN ref_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sleeve_elements ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sleeve_elements ADD COLUMN sync_flag INTEGER NOT NULL DEFAULT 0")

                // Create new tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sleeve_files (
                        element_id INTEGER PRIMARY KEY NOT NULL,
                        image_id INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        file_size INTEGER NOT NULL DEFAULT 0,
                        file_extension TEXT NOT NULL DEFAULT '',
                        mime_type TEXT NOT NULL DEFAULT '',
                        checksum INTEGER NOT NULL DEFAULT 0,
                        current_version_id TEXT,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        has_thumbnail INTEGER NOT NULL DEFAULT 0,
                        has_full_image INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (element_id) REFERENCES sleeve_elements(element_id) ON DELETE CASCADE
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sleeve_folders (
                        element_id INTEGER PRIMARY KEY NOT NULL,
                        child_count INTEGER NOT NULL DEFAULT 0,
                        file_count INTEGER NOT NULL DEFAULT 0,
                        folder_count INTEGER NOT NULL DEFAULT 0,
                        default_filter_id INTEGER NOT NULL DEFAULT 0,
                        thumbnail_element_id INTEGER,
                        children_loaded INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (element_id) REFERENCES sleeve_elements(element_id) ON DELETE CASCADE
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS image_metadata (
                        image_id INTEGER PRIMARY KEY NOT NULL,
                        image_path TEXT NOT NULL,
                        image_name TEXT NOT NULL,
                        image_type INTEGER NOT NULL DEFAULT 0,
                        file_size INTEGER NOT NULL DEFAULT 0,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        checksum INTEGER NOT NULL DEFAULT 0,
                        mime_type TEXT NOT NULL DEFAULT '',
                        thumb_state INTEGER NOT NULL DEFAULT 0,
                        sync_state INTEGER NOT NULL DEFAULT 0,
                        has_thumbnail INTEGER NOT NULL DEFAULT 0,
                        has_full_image INTEGER NOT NULL DEFAULT 0,
                        has_exif INTEGER NOT NULL DEFAULT 0,
                        has_exif_display INTEGER NOT NULL DEFAULT 0,
                        has_raw_color_context INTEGER NOT NULL DEFAULT 0,
                        thumb_pinned INTEGER NOT NULL DEFAULT 0,
                        full_pinned INTEGER NOT NULL DEFAULT 0,
                        camera_make TEXT NOT NULL DEFAULT '',
                        camera_model TEXT NOT NULL DEFAULT '',
                        lens_model TEXT NOT NULL DEFAULT '',
                        focal_length REAL NOT NULL DEFAULT 0,
                        focal_length_35mm REAL NOT NULL DEFAULT 0,
                        aperture REAL NOT NULL DEFAULT 0,
                        shutter_speed REAL NOT NULL DEFAULT 0,
                        iso INTEGER NOT NULL DEFAULT 0,
                        capture_date INTEGER NOT NULL DEFAULT 0,
                        image_size_display TEXT NOT NULL DEFAULT '',
                        file_size_display TEXT NOT NULL DEFAULT '',
                        exif_json TEXT NOT NULL DEFAULT '',
                        exif_display_json TEXT NOT NULL DEFAULT '',
                        raw_color_context_json TEXT NOT NULL DEFAULT '',
                        raw_cpid INTEGER NOT NULL DEFAULT 0,
                        is_floating INTEGER NOT NULL DEFAULT 0,
                        rating INTEGER NOT NULL DEFAULT 0,
                        imported_at INTEGER NOT NULL DEFAULT 0,
                        last_accessed_at INTEGER NOT NULL DEFAULT 0
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ratings (
                        image_id INTEGER NOT NULL,
                        rating INTEGER NOT NULL DEFAULT 0,
                        rated_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (image_id)
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collections (
                        collection_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        collection_name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        cover_image_id INTEGER,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collection_images (
                        collection_id INTEGER NOT NULL,
                        image_id INTEGER NOT NULL,
                        added_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (collection_id, image_id),
                        FOREIGN KEY (collection_id) REFERENCES collections(collection_id) ON DELETE CASCADE
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS filter_presets (
                        preset_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        filter_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        is_default INTEGER NOT NULL DEFAULT 0
                    )
                """)

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS element_fts (
                        element_id INTEGER PRIMARY KEY NOT NULL,
                        element_name TEXT NOT NULL
                    )
                """)

                // Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_camera_make ON image_metadata(camera_make)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_camera_model ON image_metadata(camera_model)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_lens_model ON image_metadata(lens_model)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_focal_length ON image_metadata(focal_length)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_aperture ON image_metadata(aperture)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_iso ON image_metadata(iso)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_capture_date ON image_metadata(capture_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_metadata_rating ON image_metadata(rating)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ratings_rating ON ratings(rating)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_collections_name ON collections(collection_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_collection_images_image_id ON collection_images(image_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_filter_presets_name ON filter_presets(name)")
            }
        }
    }
}

// ================================================================
// Type Converters
// ================================================================

class SleeveTypeConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): java.time.Instant? {
        return value?.let { java.time.Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun toTimestamp(instant: java.time.Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun fromByteArray(value: ByteArray?): ByteArray? = value

    @TypeConverter
    fun toByteArray(value: ByteArray?): ByteArray? = value
}