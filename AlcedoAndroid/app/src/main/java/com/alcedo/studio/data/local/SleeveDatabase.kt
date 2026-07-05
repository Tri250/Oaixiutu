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
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.dao.AiEmbeddingDao
import android.util.Log
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

@Database(
    entities = [
        // Sleeve element system (SleeveElement.kt)
        SleeveElementEntity::class,
        SleeveFileEntity::class,
        SleeveFolderEntity::class,
        ElementFts::class,
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
    version = 2,
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
        private const val TAG = "SleeveDatabase"

        @Volatile
        private var INSTANCE: SleeveDatabase? = null

        @Volatile
        private var sqlCipherAvailable: Boolean = false

        init {
            // SQLCipher requires the native library to be loaded explicitly.
            // Load it here so the first access to SupportOpenHelperFactory does not fail.
            try {
                System.loadLibrary("sqlcipher")
                sqlCipherAvailable = true
            } catch (e: Throwable) {
                sqlCipherAvailable = false
                Log.w(TAG, "libsqlcipher unavailable, will use plain Room database", e)
            }
        }

        fun getInstance(context: Context): SleeveDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                try {
                    val db = if (sqlCipherAvailable) {
                        buildEncryptedDatabase(appContext)
                    } else {
                        buildPlainDatabase(appContext)
                    }
                    INSTANCE = db
                    db
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to open database, attempting recovery", e)
                    try {
                        deleteDatabaseFiles(appContext)
                        if (sqlCipherAvailable) {
                            resetPassphrase(appContext)
                        } else {
                            resetFallbackPassphrase(appContext)
                        }
                        val db = if (sqlCipherAvailable) {
                            buildEncryptedDatabase(appContext)
                        } else {
                            buildPlainDatabase(appContext)
                        }
                        INSTANCE = db
                        db
                    } catch (e2: Throwable) {
                        Log.e(TAG, "Recovery failed, falling back to plain database", e2)
                        try {
                            deleteDatabaseFiles(appContext)
                            val db = buildPlainDatabase(appContext)
                            INSTANCE = db
                            db
                        } catch (e3: Throwable) {
                            Log.e(TAG, "Catastrophic database failure; returning dummy instance", e3)
                            throw e3
                        }
                    }
                }
            }
        }

        private fun buildEncryptedDatabase(appContext: Context): SleeveDatabase {
            val passphrase = getOrCreatePassphrase(appContext)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                appContext, SleeveDatabase::class.java, "alcedo_sleeve.db"
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
        }

        private fun buildPlainDatabase(appContext: Context): SleeveDatabase {
            return Room.databaseBuilder(
                appContext, SleeveDatabase::class.java, "alcedo_sleeve.db"
            )
            .fallbackToDestructiveMigration()
            .setJournalMode(JournalMode.AUTOMATIC)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA journal_mode=WAL")
                }
            })
            .build()
        }

        private fun deleteDatabaseFiles(context: Context) {
            val dbFile = context.getDatabasePath("alcedo_sleeve.db")
            listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach {
                if (it.exists()) it.delete()
            }
        }

        private fun resetPassphrase(context: Context) {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val securePrefs = EncryptedSharedPreferences.create(
                    context, "alcedo_secure", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                securePrefs.edit().remove("db_passphrase").apply()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to reset passphrase in encrypted prefs", e)
            }
        }

        private fun resetFallbackPassphrase(context: Context) {
            // No-op: the KeyStore-derived fallback key is not persisted anywhere,
            // so there is nothing to reset. We intentionally keep the keystore alias
            // intact so the derived passphrase remains stable across recovery attempts.
        }

        private fun getOrCreatePassphrase(context: Context): ByteArray {
            return try {
                getOrCreateEncryptedPassphrase(context)
            } catch (e: Throwable) {
                Log.e(TAG, "EncryptedSharedPreferences unavailable, using KeyStore-derived key", e)
                try {
                    getOrCreateSecureKey(context)
                } catch (e2: Throwable) {
                    Log.e(TAG, "KeyStore fallback also failed, using ephemeral passphrase", e2)
                    ByteArray(32).also { SecureRandom().nextBytes(it) }
                }
            }
        }

        private fun getOrCreateEncryptedPassphrase(context: Context): ByteArray {
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
                return try {
                    Base64.decode(existingKey, Base64.NO_WRAP)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to decode existing passphrase, generating new one", e)
                    generateAndSavePassphrase(securePrefs)
                }
            }

            return generateAndSavePassphrase(securePrefs)
        }

        /**
         * Derive a stable SQLCipher passphrase from a non-exportable AndroidKeystore key.
         *
         * Used as a secure fallback when EncryptedSharedPreferences is unavailable.
         * No plaintext storage is involved: the passphrase is recomputed from the
         * keystore-bound HMAC key every time, so there is nothing on disk to leak.
         *
         * The keystore key is bound to the device, so the derived passphrase is
         * stable across launches but inaccessible to other apps or to attackers
         * without the device's keystore.
         */
        private fun getOrCreateSecureKey(context: Context): ByteArray {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "alcedo_db_key"

            if (!keyStore.containsAlias(alias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                ?: throw IllegalStateException("KeyStore entry type mismatch for $alias")
            val secretKey = entry.secretKey
            // Sign a fixed message to derive a deterministic 32-byte passphrase.
            // The keystore key is non-exportable, but signing operations are permitted.
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKey)
            return mac.doFinal("alcedo-sqlcipher-passphrase-v1".toByteArray(Charsets.UTF_8))
        }

        private fun generateAndSavePassphrase(securePrefs: android.content.SharedPreferences): ByteArray {
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            securePrefs.edit()
                .putString("db_passphrase", Base64.encodeToString(key, Base64.NO_WRAP))
                .apply()
            return key
        }
    }
}
