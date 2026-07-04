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
        ElementFts::class,
        // Desktop schema entities (v3)
        ImageEntity::class,
        PipelineEntity::class,
        HistoryEntity::class,
        FilterEntity::class,
        AiDescriptionEntity::class,
        AiRatingEntity::class,
        SemanticEmbeddingEntity::class,
        SemanticLabelV2Entity::class,
        CollectionV2Entity::class,
        CollectionImageV2Entity::class
    ],
    version = 3,
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

    // Desktop schema DAOs
    abstract fun imageDao(): ImageDao
    abstract fun pipelineDao(): PipelineDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterV2Dao(): FilterV2Dao
    abstract fun aiDescriptionDao(): AiDescriptionDao
    abstract fun aiRatingDao(): AiRatingDao
    abstract fun semanticEmbeddingDao(): SemanticEmbeddingDao
    abstract fun semanticLabelV2Dao(): SemanticLabelV2Dao
    abstract fun collectionV2Dao(): CollectionV2Dao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── ImageEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS images (
                        file_id INTEGER PRIMARY KEY NOT NULL,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        format TEXT NOT NULL DEFAULT '',
                        color_space TEXT NOT NULL DEFAULT '',
                        bit_depth INTEGER NOT NULL DEFAULT 8,
                        is_hdr INTEGER NOT NULL DEFAULT 0,
                        rating INTEGER NOT NULL DEFAULT 0,
                        import_date INTEGER NOT NULL DEFAULT 0,
                        last_modified INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_file_id ON images(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_format ON images(format)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_color_space ON images(color_space)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_is_hdr ON images(is_hdr)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_rating ON images(rating)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_images_import_date ON images(import_date)")

                // ── PipelineEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pipelines (
                        pipeline_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        params_json TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pipelines_file_id ON pipelines(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_pipelines_is_active ON pipelines(is_active)")

                // ── HistoryEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS histories (
                        row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        version_id TEXT NOT NULL,
                        version_name TEXT NOT NULL DEFAULT '',
                        params_json TEXT NOT NULL,
                        parent_version_id TEXT,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        is_active INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_histories_file_id ON histories(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_histories_version_id ON histories(version_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_histories_is_active ON histories(is_active)")

                // ── FilterEntity table (desktop schema) ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS filters (
                        filter_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        filter_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_filters_name ON filters(name)")

                // ── AiDescriptionEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_descriptions (
                        row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        task_id TEXT NOT NULL DEFAULT '',
                        provider_id TEXT NOT NULL DEFAULT '',
                        model_id TEXT NOT NULL DEFAULT '',
                        caption TEXT NOT NULL DEFAULT '',
                        tags_json TEXT NOT NULL DEFAULT '[]',
                        scene TEXT NOT NULL DEFAULT '',
                        confidence REAL NOT NULL DEFAULT 0,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_descriptions_file_id ON ai_descriptions(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_descriptions_task_id ON ai_descriptions(task_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_descriptions_provider_id ON ai_descriptions(provider_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_descriptions_is_active ON ai_descriptions(is_active)")

                // ── AiRatingEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_ratings (
                        row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        task_id TEXT NOT NULL DEFAULT '',
                        provider_id TEXT NOT NULL DEFAULT '',
                        model_id TEXT NOT NULL DEFAULT '',
                        rating INTEGER NOT NULL DEFAULT 0,
                        rubric_id TEXT NOT NULL DEFAULT '',
                        reasons_json TEXT NOT NULL DEFAULT '[]',
                        is_active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_ratings_file_id ON ai_ratings(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_ratings_task_id ON ai_ratings(task_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_ratings_provider_id ON ai_ratings(provider_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_ai_ratings_is_active ON ai_ratings(is_active)")

                // ── SemanticEmbeddingEntity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS semantic_embeddings (
                        row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        model_id TEXT NOT NULL DEFAULT '',
                        embedding_blob BLOB NOT NULL DEFAULT X'',
                        dimension INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_file_id ON semantic_embeddings(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_embeddings_model_id ON semantic_embeddings(model_id)")

                // ── SemanticLabelV2Entity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS semantic_labels_v2 (
                        row_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        file_id INTEGER NOT NULL,
                        model_id TEXT NOT NULL DEFAULT '',
                        primary_label TEXT NOT NULL DEFAULT '',
                        secondary_label TEXT NOT NULL DEFAULT '',
                        primary_confidence REAL NOT NULL DEFAULT 0,
                        marginal REAL NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_labels_v2_file_id ON semantic_labels_v2(file_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_labels_v2_model_id ON semantic_labels_v2(model_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_semantic_labels_v2_primary_label ON semantic_labels_v2(primary_label)")

                // ── CollectionV2Entity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collections_v2 (
                        collection_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        cover_file_id INTEGER,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_collections_v2_name ON collections_v2(name)")

                // ── CollectionImageV2Entity table ──
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS collection_images_v2 (
                        collection_id INTEGER NOT NULL,
                        file_id INTEGER NOT NULL,
                        added_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (collection_id, file_id),
                        FOREIGN KEY (collection_id) REFERENCES collections_v2(collection_id) ON DELETE CASCADE,
                        FOREIGN KEY (file_id) REFERENCES sleeve_files(element_id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_collection_images_v2_file_id ON collection_images_v2(file_id)")

                // ── Migrate data from old collections to collections_v2 ──
                db.execSQL("""
                    INSERT INTO collections_v2 (collection_id, name, description, cover_file_id, created_at, updated_at)
                    SELECT collection_id, collection_name, description, cover_image_id, created_at, updated_at
                    FROM collections
                """)
                db.execSQL("""
                    INSERT INTO collection_images_v2 (collection_id, file_id, added_at)
                    SELECT collection_id, image_id, added_at
                    FROM collection_images
                """)
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
