package com.alcedo.studio.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.ImageDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageFlag

// ---------------------------------------------------------------------------
// Room entities. These mirror the domain models but are flattened for SQL.
// The native DuckDB layer remains the source of truth for the sleeve tree;
// Room caches the projection the UI renders and serves AI/edge metadata.
// ---------------------------------------------------------------------------

@Entity(tableName = "images", indices = [Index("sleevePath"), Index("rating"), Index("dateCapturedEpoch")])
data class ImageEntity(
    @PrimaryKey val id: String,
    val sleevePath: String,
    val originalUri: String,
    val displayName: String,
    val fileExtension: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedEpoch: Long,
    val dateCapturedEpoch: Long,
    val rating: Int = 0,
    val flag: ImageFlag = ImageFlag.NONE,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val isRaw: Boolean = false,
    val isVirtualCopy: Boolean = false,
    val parentId: String? = null,
    val thumbnailPath: String? = null,
    val currentVersionId: String? = null,
    val aiCaption: String? = null,
    val aiTags: String? = null,
    val aiScore: Float? = null,
    val isHidden: Boolean = false,
    val lensModel: String? = null,
    val cameraModel: String? = null,
    val focalLength: Float? = null,
    val iso: Int? = null,
    val aperture: Float? = null,
    val shutterSpeed: String? = null,
)

@Entity(tableName = "sleeve_elements", indices = [Index("parentId"), Index("sleevePath", unique = true)])
data class SleeveElementEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val sleevePath: String,
    val isFolder: Boolean,
    val createdAt: Long,
    val modifiedAt: Long,
    val imageId: String? = null,
    val childCount: Int = 0,
    val imageCount: Int = 0,
    val isSmartCollection: Boolean = false,
    val smartFilterJson: String? = null,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val rootSleeveId: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String,
    val version: Int,
    val schemaVersion: Int,
    val imageCount: Int,
    val totalSizeBytes: Long,
    val thumbnailPath: String?,
    val tags: String?,
    val isFavorite: Boolean,
    val lastOpenedAt: Long?,
)

@Entity(tableName = "edit_versions", indices = [Index("imageId")])
data class EditVersionEntity(
    @PrimaryKey val id: String,
    val imageId: String,
    val parentId: String?,
    val name: String,
    val createdAt: Long,
    val cumulativeParamsJson: String,
    val isVirtualCopy: Boolean,
    val isActive: Boolean,
    val note: String?,
)

@Entity(tableName = "edit_transactions", indices = [Index("versionId")])
data class EditTransactionEntity(
    @PrimaryKey val id: String,
    val versionId: String,
    val timestamp: Long,
    val label: String,
    val paramDeltaJson: String,
    val maskIds: String?,
    val source: String,
)

@Entity(tableName = "ai_embeddings", indices = [Index("imageId"), Index("modelId")])
data class AiEmbeddingEntity(
    @PrimaryKey val id: String,
    val imageId: String,
    val modelId: String,
    val dimensions: Int,
    val generatedAt: Long,
    val norm: Float,
    val embeddingBlob: ByteArray, // little-endian float32 packed
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiEmbeddingEntity) return false
        return id == other.id && imageId == other.imageId &&
            modelId == other.modelId && embeddingBlob.contentEquals(other.embeddingBlob)
    }

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + imageId.hashCode()
        r = 31 * r + embeddingBlob.contentHashCode()
        return r
    }
}

@Entity(tableName = "ai_ratings", indices = [Index("imageId", unique = true)])
data class AiRatingEntity(
    @PrimaryKey val imageId: String,
    val overallScore: Float,
    val technicalScore: Float,
    val aestheticScore: Float,
    val sharpnessScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val emotionScore: Float,
    val rationale: String,
    val suggestedRating: Int,
    val suggestedFlag: String,
    val generatedAt: Long,
    val modelId: String,
    val provider: String,
    val confidence: Float,
)

@Entity(tableName = "pipeline_presets", indices = [Index("category")])
data class PipelinePresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val adjustmentsJson: String,
    val isBuiltIn: Boolean,
    val isFavorite: Boolean,
    val thumbnailPath: String?,
    val createdAt: Long,
)

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String,
    val localPath: String?,
    val isDownloaded: Boolean,
    val isDefault: Boolean,
    val dimensions: Int,
    val description: String,
)

@Entity(tableName = "lens_profiles", indices = [Index("lensId", unique = true)])
data class LensProfileEntity(
    @PrimaryKey val id: String,
    val lensId: String,
    val displayName: String,
    val maker: String,
    val profileJson: String,
    val isCalibrated: Boolean,
)

// ---------------------------------------------------------------------------
// Type converters
// ---------------------------------------------------------------------------

class SleeveTypeConverters {

    @TypeConverter fun flagToString(flag: ImageFlag?): String? = flag?.name
    @TypeConverter fun stringToFlag(value: String?): ImageFlag? =
        value?.let { runCatching { ImageFlag.valueOf(it) }.getOrNull() }

    @TypeConverter fun colorLabelToString(label: ColorLabel?): String? = label?.name
    @TypeConverter fun stringToColorLabel(value: String?): ColorLabel? =
        value?.let { runCatching { ColorLabel.valueOf(it) }.getOrNull() }

    @TypeConverter fun floatArrayToString(arr: FloatArray?): String? =
        arr?.joinToString(",") { it.toString() }
    @TypeConverter fun stringToFloatArray(value: String?): FloatArray? =
        value?.takeIf { it.isNotBlank() }
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
}

/**
 * Alcedo Room database. A single database holds the projected sleeve, image,
 * edit-history, AI and preset caches. The DuckDB-backed native sleeve remains
 * authoritative for the tree; this DB mirrors the projection for the UI.
 */
@Database(
    entities = [
        ImageEntity::class,
        SleeveElementEntity::class,
        ProjectEntity::class,
        EditVersionEntity::class,
        EditTransactionEntity::class,
        AiEmbeddingEntity::class,
        AiRatingEntity::class,
        PipelinePresetEntity::class,
        AiModelEntity::class,
        LensProfileEntity::class,
    ],
    version = SleeveDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(SleeveTypeConverters::class)
abstract class SleeveDatabase : RoomDatabase() {

    abstract fun imageDao(): ImageDao
    abstract fun editHistoryDao(): EditHistoryDao
    abstract fun aiEmbeddingDao(): AiEmbeddingDao
    abstract fun pipelinePresetDao(): PipelinePresetDao

    companion object {
        const val VERSION = 1
        const val DB_NAME = "alcedo_sleeve.db"

        @Volatile
        private var instance: SleeveDatabase? = null

        fun get(context: Context): SleeveDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        fun build(context: Context): SleeveDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SleeveDatabase::class.java,
                DB_NAME,
            )
                .fallbackToDestructiveMigration()
                .addMigrations(*DatabaseMigrations.ALL)
                .build()

        /** Close and recreate; used by Manage Space / tests. */
        fun reset(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = build(context)
            }
        }
    }
}
