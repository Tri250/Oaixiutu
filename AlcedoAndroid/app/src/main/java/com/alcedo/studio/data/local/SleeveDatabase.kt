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
        AiEmbeddingEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class SleeveDatabase : RoomDatabase() {
    abstract fun sleeveElementDao(): SleeveElementDao
    abstract fun imageDao(): ImageDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun pipelinePresetDao(): PipelinePresetDao
    abstract fun aiEmbeddingDao(): AiEmbeddingDao

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
                .addMigrations(MIGRATION_1_2)
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
