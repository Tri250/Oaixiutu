package com.alcedo.studio.data.model

import androidx.room.*

@Entity(tableName = "edit_history", indices = [Index(value = ["imageId"])])
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: Long,
    val versionId: String,
    val parentId: String = "",
    val createdTime: Long,
    val name: String = "",
    val isActive: Boolean = true,
    val paramsJson: String = "{}"
)

@Entity(tableName = "pipeline_presets", indices = [Index(value = ["name"]), Index(value = ["category"])])
data class PipelinePresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    val paramsJson: String,
    val createdTime: Long,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "ai_embeddings", indices = [Index(value = ["imageId"])])
data class AiEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: Long,
    val embeddingData: ByteArray,  // serialized float array
    val modelVersion: String = "clip-vit-base-patch32",
    val createdTime: Long
)

data class HistoryVersion(
    val versionId: String,
    val createdTime: Long,
    val parentId: String,
    val name: String
)
