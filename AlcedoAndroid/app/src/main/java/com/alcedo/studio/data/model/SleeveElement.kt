package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * Sleeve filesystem elements. The "sleeve" is Alcedo's virtual, project-local
 * filesystem (a tree of folders and files backed by DuckDB) that organises
 * imported images independently of the device's real storage. It mirrors the
 * desktop core/sleeve/sleeve_element hierarchy.
 */
@Serializable
sealed class SleeveElement {
    abstract val id: String
    abstract val parentId: String?
    abstract val name: String
    abstract val sleevePath: String
    abstract val createdAt: Long
    abstract val modifiedAt: Long

    val isRoot: Boolean get() = parentId == null && name == "/"
}

@Serializable
data class SleeveFolder(
    override val id: String,
    override val parentId: String?,
    override val name: String,
    override val sleevePath: String,
    override val createdAt: Long,
    override val modifiedAt: Long,
    val childCount: Int = 0,
    val imageCount: Int = 0,
    val isSmartCollection: Boolean = false,
    val smartFilter: FilterCombo? = null,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val isExpanded: Boolean = true,
) : SleeveElement()

@Serializable
data class SleeveFile(
    override val id: String,
    override val parentId: String?,
    override val name: String,
    override val sleevePath: String,
    override val createdAt: Long,
    override val modifiedAt: Long,
    val imageId: String,
    val sourceUri: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val isReferenced: Boolean = true,
    val checksum: String? = null,
) : SleeveElement() {

    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isRaw: Boolean
        get() = RAW_EXTENSIONS.any { ext -> extension == ext }
}

@Serializable
data class SleeveTree(
    val root: SleeveFolder,
    val children: Map<String, List<SleeveElement>>,
) {
    fun childrenOf(folderId: String): List<SleeveElement> =
        children[folderId] ?: emptyList()

    fun flatten(): List<SleeveElement> {
        val result = mutableListOf<SleeveElement>()
        fun walk(folder: SleeveFolder) {
            result.add(folder)
            childrenOf(folder.id).forEach { child ->
                when (child) {
                    is SleeveFolder -> walk(child)
                    is SleeveFile -> result.add(child)
                }
            }
        }
        walk(root)
        return result
    }

    fun foldersOnly(): List<SleeveFolder> =
        flatten().filterIsInstance<SleeveFolder>()
}

/** Companion constants for RAW file detection. */
object SleeveConstants {
    const val ROOT_PATH = "/"
    const val PATH_SEPARATOR = "/"
    const val DEFAULT_IMPORT_FOLDER = "Imports"
    const val VIRTUAL_COPIES_FOLDER = "Virtual Copies"
    const val TRASH_FOLDER = "Trash"
}

val RAW_EXTENSIONS = setOf(
    "arw", "cr2", "cr3", "crw", "dng", "nef", "nrw", "orf", "raf", "raw",
    "rw2", "rwl", "sr2", "srf", "srw", "3fr", "iiq", "mos", "pef", "raf",
)

val SUPPORTED_IMAGE_EXTENSIONS = RAW_EXTENSIONS + setOf(
    "jpg", "jpeg", "png", "tif", "tiff", "webp", "heic", "heif", "avif", "bmp",
)

fun isRawExtension(ext: String): Boolean =
    RAW_EXTENSIONS.contains(ext.lowercase())

fun isSupportedImage(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return SUPPORTED_IMAGE_EXTENSIONS.contains(ext)
}
