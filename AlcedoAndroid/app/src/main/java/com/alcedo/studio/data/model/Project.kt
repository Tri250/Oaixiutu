package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * An Alcedo project (.alcd). A project bundles a root sleeve (image collection),
 * edit history, presets and metadata into a single self-contained archive. The
 * native layer reads/writes the binary container; this model is the Kotlin-side
 * descriptor persisted in Room and shown in the UI.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val filePath: String,
    val rootSleeveId: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String = "",
    val version: Int = 1,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val imageCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val thumbnailPath: String? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long? = null,
) {
    val displaySize: String
        get() = formatBytes(totalSizeBytes)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FILE_EXTENSION = "alcd"

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024
            var unitIdx = 0
            while (value >= 1024 && unitIdx < units.lastIndex) {
                value /= 1024
                unitIdx++
            }
            return "%.1f %s".format(value, units[unitIdx])
        }
    }
}

/** Header of a .alcd archive, parsed before the full container is unpacked. */
@Serializable
data class ProjectHeader(
    val magic: String = "ALCD",
    val version: Int = Project.CURRENT_SCHEMA_VERSION,
    val schemaVersion: Int = Project.CURRENT_SCHEMA_VERSION,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val imageCount: Int,
    val entryOffset: Long,
    val entryCount: Int,
)

/** Internal entry descriptor used while packaging / unpacking a .alcd archive. */
@Serializable
data class ProjectPackageEntry(
    val relativePath: String,
    val offset: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val crc32: Long,
    val compression: PackageCompression = PackageCompression.ZSTD,
)

@Serializable
enum class PackageCompression { NONE, ZSTD, DEFLATE }
