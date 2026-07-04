package com.alcedo.studio.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alcedo.studio.data.model.*
import com.alcedo.studio.data.dao.*
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [
        SleeveElementEntity::class,
        ImageEntity::class,
        EditHistoryEntity::class,
        PipelinePresetEntity::class,
        AiEmbeddingEntity::class,
        FileEntity::class,
        FolderEntity::class,
        ImageMetadataEntity::class,
        RatingEntity::class,
        SemanticLabelEntity::class,
        CollectionEntity::class,
        FilterEntity::class,
        PipelineEntity::class,
        FilterV2Entity::class,
        AiDescriptionEntity::class,
        AiRatingEntity::class,
        SemanticLabelV2Entity::class,
        CollectionV2Entity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class SleeveDatabase : RoomDatabase() {
    abstract fun sleeveElementDao(): SleeveElementDao
    abstract fun imageDao(): ImageDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun pipelinePresetDao(): PipelinePresetDao
    abstract fun aiEmbeddingDao(): AiEmbeddingDao
    abstract fun sleeveFileDao(): SleeveFileDao
    abstract fun sleeveFolderDao(): SleeveFolderDao
    abstract fun imageMetadataDao(): ImageMetadataDao
    abstract fun ratingDao(): RatingDao
    abstract fun semanticLabelDao(): SemanticLabelDao
    abstract fun collectionDao(): CollectionDao
    abstract fun filterDao(): FilterDao
    abstract fun pipelineDao(): PipelineDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterV2Dao(): FilterV2Dao
    abstract fun aiDescriptionDao(): AiDescriptionDao
    abstract fun aiRatingDao(): AiRatingDao
    abstract fun semanticEmbeddingDao(): SemanticEmbeddingDao
    abstract fun semanticLabelV2Dao(): SemanticLabelV2Dao
    abstract fun collectionV2Dao(): CollectionV2Dao

    companion object {
        @Volatile
        private var INSTANCE: SleeveDatabase? = null

        fun getInstance(context: Context): SleeveDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SleeveDatabase::class.java,
                    "alcedo_sleeve.db"
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.WAL)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Ensure WAL mode is active for optimal concurrent read/write
                        db.execSQL("PRAGMA journal_mode=WAL")
                        // Enable foreign key enforcement
                        db.execSQL("PRAGMA foreign_keys=ON")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from v1 to v2: Add indices for frequently queried columns.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ImageEntity indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_filePath` ON `images` (`filePath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_dateAdded` ON `images` (`dateAdded`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_dateModified` ON `images` (`dateModified`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_rating` ON `images` (`rating`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_colorLabel` ON `images` (`colorLabel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_isRaw` ON `images` (`isRaw`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_rawMake` ON `images` (`rawMake`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_rawModel` ON `images` (`rawModel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_iso` ON `images` (`iso`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_focalLength` ON `images` (`focalLength`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_fileName` ON `images` (`fileName`)")

                // SleeveElementEntity indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_parentId` ON `sleeve_elements` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_elementType` ON `sleeve_elements` (`elementType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_imageId` ON `sleeve_elements` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_syncFlag` ON `sleeve_elements` (`syncFlag`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_elementName` ON `sleeve_elements` (`elementName`)")

                // EditHistoryEntity indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_history_imageId` ON `edit_history` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_history_versionId` ON `edit_history` (`versionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_history_parentId` ON `edit_history` (`parentId`)")

                // PipelinePresetEntity indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_presets_category` ON `pipeline_presets` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_presets_name` ON `pipeline_presets` (`name`)")

                // AiEmbeddingEntity indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_embeddings_imageId` ON `ai_embeddings` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_embeddings_modelVersion` ON `ai_embeddings` (`modelVersion`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // sleeve_files
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleeve_files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL, `size` INTEGER NOT NULL, `mimeType` TEXT NOT NULL, `fileHash` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sleeve_files_path` ON `sleeve_files` (`path`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_files_fileHash` ON `sleeve_files` (`fileHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_files_mimeType` ON `sleeve_files` (`mimeType`)")

                // sleeve_folders
                db.execSQL("CREATE TABLE IF NOT EXISTS `sleeve_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `parentId` INTEGER NOT NULL, `folderName` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_folders_parentId` ON `sleeve_folders` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_folders_folderName` ON `sleeve_folders` (`folderName`)")

                // image_metadata
                db.execSQL("CREATE TABLE IF NOT EXISTS `image_metadata` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `cameraMake` TEXT NOT NULL, `cameraModel` TEXT NOT NULL, `lensModel` TEXT NOT NULL, `focalLength` REAL NOT NULL, `aperture` REAL NOT NULL, `exposureTime` REAL NOT NULL, `iso` INTEGER NOT NULL, `whiteBalance` INTEGER NOT NULL, `flashFired` INTEGER NOT NULL, `gpsLatitude` REAL NOT NULL, `gpsLongitude` REAL NOT NULL, `gpsAltitude` REAL NOT NULL, `orientation` INTEGER NOT NULL, `dateTaken` INTEGER NOT NULL, `software` TEXT NOT NULL, `exifJson` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_image_metadata_imageId` ON `image_metadata` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_metadata_cameraMake` ON `image_metadata` (`cameraMake`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_metadata_cameraModel` ON `image_metadata` (`cameraModel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_metadata_lensModel` ON `image_metadata` (`lensModel`)")

                // ratings
                db.execSQL("CREATE TABLE IF NOT EXISTS `ratings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `ratingSource` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ratings_imageId` ON `ratings` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ratings_rating` ON `ratings` (`rating`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ratings_ratingSource` ON `ratings` (`ratingSource`)")

                // semantic_labels
                db.execSQL("CREATE TABLE IF NOT EXISTS `semantic_labels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `label` TEXT NOT NULL, `confidence` REAL NOT NULL, `source` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_imageId` ON `semantic_labels` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_label` ON `semantic_labels` (`label`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_source` ON `semantic_labels` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_confidence` ON `semantic_labels` (`confidence`)")

                // collections
                db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `coverImageId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_name` ON `collections` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collections_sortOrder` ON `collections` (`sortOrder`)")

                // filters
                db.execSQL("CREATE TABLE IF NOT EXISTS `filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `paramsJson` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filters_category` ON `filters` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filters_name` ON `filters` (`name`)")

                // pipeline_state
                db.execSQL("CREATE TABLE IF NOT EXISTS `pipeline_state` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `paramsJson` TEXT NOT NULL, `createdTime` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pipeline_state_imageId` ON `pipeline_state` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_state_createdTime` ON `pipeline_state` (`createdTime`)")

                // filters_v2
                db.execSQL("CREATE TABLE IF NOT EXISTS `filters_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `paramsJson` TEXT NOT NULL, `previewPath` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filters_v2_category` ON `filters_v2` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filters_v2_name` ON `filters_v2` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_filters_v2_previewPath` ON `filters_v2` (`previewPath`)")

                // ai_descriptions
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_descriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `descriptionText` TEXT NOT NULL, `model` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_descriptions_imageId` ON `ai_descriptions` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_descriptions_model` ON `ai_descriptions` (`model`)")

                // ai_ratings
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_ratings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `qualityScore` REAL NOT NULL, `aestheticScore` REAL NOT NULL, `technicalScore` REAL NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_ratings_imageId` ON `ai_ratings` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_ratings_qualityScore` ON `ai_ratings` (`qualityScore`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_ratings_aestheticScore` ON `ai_ratings` (`aestheticScore`)")

                // semantic_labels_v2
                db.execSQL("CREATE TABLE IF NOT EXISTS `semantic_labels_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `imageId` INTEGER NOT NULL, `label` TEXT NOT NULL, `confidence` REAL NOT NULL, `category` TEXT NOT NULL, `source` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_imageId` ON `semantic_labels_v2` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_label` ON `semantic_labels_v2` (`label`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_category` ON `semantic_labels_v2` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_source` ON `semantic_labels_v2` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_semantic_labels_v2_confidence` ON `semantic_labels_v2` (`confidence`)")

                // collections_v2
                db.execSQL("CREATE TABLE IF NOT EXISTS `collections_v2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `query` TEXT NOT NULL, `isSmart` INTEGER NOT NULL, `coverImageId` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_v2_name` ON `collections_v2` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collections_v2_isSmart` ON `collections_v2` (`isSmart`)")
            }
        }

        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val securePrefs = EncryptedSharedPreferences.create(
                context,
                "alcedo_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existingKey = securePrefs.getString("db_passphrase", null)

            if (existingKey != null) {
                return Base64.decode(existingKey, Base64.NO_WRAP)
            }

            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            securePrefs.edit()
                .putString("db_passphrase", Base64.encodeToString(key, Base64.NO_WRAP))
                .apply()
            return key
        }
    }
}
