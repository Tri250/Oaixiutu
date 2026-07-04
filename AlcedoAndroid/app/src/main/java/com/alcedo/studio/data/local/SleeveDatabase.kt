package com.alcedo.studio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alcedo.studio.data.model.*
import com.alcedo.studio.data.dao.*

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
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SleeveDatabase::class.java,
                    "alcedo_sleeve.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
