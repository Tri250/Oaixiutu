package com.alcedo.studio.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
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
