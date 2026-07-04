package com.alcedo.studio.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alcedo.studio.data.model.*
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.dao.AiEmbeddingDao
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

@Database(
    entities = [
        // Sleeve element system (SleeveElement.kt)
        SleeveElementEntity::class,
        SleeveFileEntity::class,
        SleeveFolderEntity::class,
        CollectionEntity::class,
        CollectionImageEntity::class,
        RatingEntity::class,
        FilterPresetEntity::class,
        // Image system (ImageModel.kt)
        ImageMetadataEntity::class,
        SemanticLabelEntity::class,
        VectorIndexEntity::class,
        ImageEntity::class,
        PipelineEntity::class,
        HistoryEntity::class,
        FilterEntity::class,
        AiDescriptionEntity::class,
        AiRatingEntity::class,
        SemanticEmbeddingEntity::class,
        SemanticLabelV2Entity::class,
        CollectionV2Entity::class,
        CollectionImageV2Entity::class,
        // Simple entities (Models.kt)
        EditHistoryEntity::class,
        PipelinePresetEntity::class,
        AiEmbeddingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SleeveDatabase : RoomDatabase() {
    // DAOs from data.local.SleeveDao.kt
    abstract fun sleeveElementDao(): SleeveElementDao
    abstract fun sleeveFileDao(): SleeveFileDao
    abstract fun sleeveFolderDao(): SleeveFolderDao
    abstract fun imageMetadataDao(): ImageMetadataDao
    abstract fun ratingDao(): RatingDao
    abstract fun semanticLabelDao(): SemanticLabelDao
    abstract fun collectionDao(): CollectionDao
    abstract fun filterDao(): FilterDao
    abstract fun imageDao(): ImageDao
    abstract fun pipelineDao(): PipelineDao
    abstract fun historyDao(): HistoryDao
    abstract fun filterV2Dao(): FilterV2Dao
    abstract fun aiDescriptionDao(): AiDescriptionDao
    abstract fun aiRatingDao(): AiRatingDao
    abstract fun semanticEmbeddingDao(): SemanticEmbeddingDao
    abstract fun semanticLabelV2Dao(): SemanticLabelV2Dao
    abstract fun collectionV2Dao(): CollectionV2Dao

    // DAOs from data.dao package
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
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.AUTOMATIC)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA journal_mode=WAL")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val securePrefs = context.getSharedPreferences("alcedo_secure", Context.MODE_PRIVATE)

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
