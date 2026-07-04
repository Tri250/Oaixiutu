package com.alcedo.studio.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// ================================================================
// Enums
// ================================================================

enum class ElementType {
    FILE, FOLDER
}

enum class SyncFlag {
    UNSYNC, MODIFIED, SYNCED, DELETED
}

enum class ElementOrder {
    ASC, DESC
}

enum class FilterType {
    DATETIME, EXIF, VALUE, RANGE, STRING, COMBO, DEFAULT
}

enum class FilterLogic {
    AND, OR
}

// ================================================================
// Room Entities
// ================================================================

@Entity(
    tableName = "sleeve_elements",
    indices = [
        Index(value = ["parent_id"]),
        Index(value = ["element_name"]),
        Index(value = ["element_type"]),
        Index(value = ["added_time"])
    ]
)
data class SleeveElementEntity(
    @PrimaryKey
    @ColumnInfo(name = "element_id")
    val elementId: Long,

    @ColumnInfo(name = "element_name")
    val elementName: String,

    @ColumnInfo(name = "element_type")
    val elementType: Int, // 0=FILE, 1=FOLDER

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "added_time")
    val addedTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_modified_time")
    val lastModifiedTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "ref_count")
    val refCount: Int = 0,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "sync_flag")
    val syncFlag: Int = SyncFlag.UNSYNC.ordinal
)

@Entity(
    tableName = "sleeve_files",
    foreignKeys = [
        ForeignKey(
            entity = SleeveElementEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["element_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SleeveFileEntity(
    @PrimaryKey
    @ColumnInfo(name = "element_id")
    val elementId: Long,

    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,

    @ColumnInfo(name = "file_extension")
    val fileExtension: String = "",

    @ColumnInfo(name = "mime_type")
    val mimeType: String = "",

    @ColumnInfo(name = "checksum")
    val checksum: Long = 0L,

    @ColumnInfo(name = "current_version_id")
    val currentVersionId: String? = null,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,

    @ColumnInfo(name = "has_thumbnail")
    val hasThumbnail: Boolean = false,

    @ColumnInfo(name = "has_full_image")
    val hasFullImage: Boolean = false
)

@Entity(
    tableName = "sleeve_folders",
    foreignKeys = [
        ForeignKey(
            entity = SleeveElementEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["element_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SleeveFolderEntity(
    @PrimaryKey
    @ColumnInfo(name = "element_id")
    val elementId: Long,

    @ColumnInfo(name = "child_count")
    val childCount: Int = 0,

    @ColumnInfo(name = "file_count")
    val fileCount: Int = 0,

    @ColumnInfo(name = "folder_count")
    val folderCount: Int = 0,

    @ColumnInfo(name = "default_filter_id")
    val defaultFilterId: Long = 0L,

    @ColumnInfo(name = "thumbnail_element_id")
    val thumbnailElementId: Long? = null,

    @ColumnInfo(name = "children_loaded")
    val childrenLoaded: Boolean = false
)

// ================================================================
// FTS Entities
// ================================================================

@Entity(tableName = "element_fts")
data class ElementFts(
    @PrimaryKey
    @ColumnInfo(name = "element_id")
    val elementId: Long,

    @ColumnInfo(name = "element_name")
    val elementName: String
)

// ================================================================
// Collection Entities
// ================================================================

@Entity(
    tableName = "collections",
    indices = [Index(value = ["collection_name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "collection_id")
    val collectionId: Long = 0L,

    @ColumnInfo(name = "collection_name")
    val collectionName: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "cover_image_id")
    val coverImageId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_images",
    primaryKeys = ["collection_id", "image_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collection_id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["image_id"])]
)
data class CollectionImageEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,

    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

// ================================================================
// Rating Entity
// ================================================================

@Entity(
    tableName = "ratings",
    primaryKeys = ["image_id"],
    indices = [Index(value = ["rating"])]
)
data class RatingEntity(
    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "rating")
    val rating: Int = 0, // 0-5 stars

    @ColumnInfo(name = "rated_at")
    val ratedAt: Long = System.currentTimeMillis()
)

// ================================================================
// Filter Preset Entity
// ================================================================

@Entity(
    tableName = "filter_presets",
    indices = [Index(value = ["name"])]
)
data class FilterPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "preset_id")
    val presetId: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "filter_json")
    val filterJson: String, // JSON-serialized filter configuration

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false
)

// ================================================================
// Domain Models (sealed class hierarchy, retained for compatibility)
// ================================================================

sealed class SleeveElement(
    open val elementId: Long,
    open val elementName: String,
    open val type: ElementType,
    open val parentId: Long? = null,
    open val addedTime: Instant = Instant.now(),
    open val lastModifiedTime: Instant = Instant.now(),
    open val refCount: Int = 0,
    open val pinned: Boolean = false,
    open val syncFlag: SyncFlag = SyncFlag.UNSYNC
) {
    abstract fun copyWithId(newId: Long): SleeveElement
    abstract fun clear(): Boolean

    fun toEntity(): SleeveElementEntity = SleeveElementEntity(
        elementId = elementId,
        elementName = elementName,
        elementType = if (type == ElementType.FILE) 0 else 1,
        parentId = parentId,
        addedTime = addedTime.toEpochMilli(),
        lastModifiedTime = lastModifiedTime.toEpochMilli(),
        refCount = refCount,
        pinned = pinned,
        syncFlag = syncFlag.ordinal
    )
}

data class SleeveFile(
    override val elementId: Long,
    override val elementName: String,
    val imageId: Long,
    val filePath: String = "",
    val fileSize: Long = 0L,
    val fileExtension: String = "",
    val mimeType: String = "",
    val checksum: Long = 0L,
    val image: ImageModel? = null,
    val editHistory: EditHistory? = null,
    val currentVersionId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val hasThumbnail: Boolean = false,
    val hasFullImage: Boolean = false,
    override val parentId: Long? = null,
    override val addedTime: Instant = Instant.now(),
    override val lastModifiedTime: Instant = Instant.now(),
    override val refCount: Int = 0,
    override val pinned: Boolean = false,
    override val syncFlag: SyncFlag = SyncFlag.UNSYNC
) : SleeveElement(elementId, elementName, ElementType.FILE, parentId, addedTime, lastModifiedTime, refCount, pinned, syncFlag) {

    override fun copyWithId(newId: Long): SleeveElement = copy(elementId = newId)

    override fun clear(): Boolean {
        image?.clearData()
        return true
    }

    fun toFileEntity(): SleeveFileEntity = SleeveFileEntity(
        elementId = elementId,
        imageId = imageId,
        filePath = filePath,
        fileSize = fileSize,
        fileExtension = fileExtension,
        mimeType = mimeType,
        checksum = checksum,
        currentVersionId = currentVersionId,
        width = width,
        height = height,
        hasThumbnail = hasThumbnail,
        hasFullImage = hasFullImage
    )
}

data class SleeveFolder(
    override val elementId: Long,
    override val elementName: String,
    val childCount: Int = 0,
    val fileCount: Int = 0,
    val folderCount: Int = 0,
    val defaultFilterId: Long = 0L,
    val thumbnailElementId: Long? = null,
    val contents: MutableMap<String, Long> = mutableMapOf(),
    val indicesCache: MutableMap<Long, List<Long>> = mutableMapOf(),
    var childrenLoaded: Boolean = false,
    override val parentId: Long? = null,
    override val addedTime: Instant = Instant.now(),
    override val lastModifiedTime: Instant = Instant.now(),
    override val refCount: Int = 0,
    override val pinned: Boolean = false,
    override val syncFlag: SyncFlag = SyncFlag.UNSYNC
) : SleeveElement(elementId, elementName, ElementType.FOLDER, parentId, addedTime, lastModifiedTime, refCount, pinned, syncFlag) {

    override fun copyWithId(newId: Long): SleeveElement = copy(elementId = newId)

    override fun clear(): Boolean {
        contents.clear()
        indicesCache.clear()
        childrenLoaded = false
        return true
    }

    fun toFolderEntity(): SleeveFolderEntity = SleeveFolderEntity(
        elementId = elementId,
        childCount = childCount,
        fileCount = fileCount,
        folderCount = folderCount,
        defaultFilterId = defaultFilterId,
        thumbnailElementId = thumbnailElementId,
        childrenLoaded = childrenLoaded
    )

    fun addElement(element: SleeveElement, changeSync: Boolean = true, incrementRef: Boolean = true) {
        contents[element.elementName] = element.elementId
        if (element is SleeveFile) {
            // fileCount incremented via DB
        } else if (element is SleeveFolder) {
            // folderCount incremented via DB
        }
    }

    fun getElementIdByName(name: String): Long? = contents[name]

    fun listElements(): List<Long> = contents.values.toList()

    fun listElementsByFilter(filterId: Long): List<Long> = indicesCache[filterId] ?: emptyList()

    fun containsElementId(elementId: Long): Boolean = contents.containsValue(elementId)

    fun removeElementById(elementId: Long): Boolean {
        val entry = contents.entries.find { it.value == elementId }
        return if (entry != null) {
            contents.remove(entry.key)
            true
        } else false
    }

    fun createIndex(filteredElements: List<SleeveElement>, filterId: Long) {
        indicesCache[filterId] = filteredElements.map { it.elementId }
    }

    fun contentSize(): Int = contents.size
}

// ================================================================
// Filter Domain Models
// ================================================================

sealed class SleeveFilter(
    open val type: FilterType
) {
    abstract fun resetFilter()
    abstract fun getPredicate(): String
    abstract fun toJson(): String
    abstract fun fromJson(json: String)
}

data class DateTimeFilter(
    var startDate: Long = 0L,
    var endDate: Long = Long.MAX_VALUE,
    var order: ElementOrder = ElementOrder.DESC
) : SleeveFilter(FilterType.DATETIME) {
    override fun resetFilter() {
        startDate = 0L
        endDate = Long.MAX_VALUE
        order = ElementOrder.DESC
    }

    override fun getPredicate(): String =
        "added_time BETWEEN $startDate AND $endDate ORDER BY added_time ${if (order == ElementOrder.ASC) "ASC" else "DESC"}"

    override fun toJson(): String =
        """{"type":"DATETIME","startDate":$startDate,"endDate":$endDate,"order":"$order"}"""

    override fun fromJson(json: String) {}
}

data class ExifFilter(
    var cameraMake: String? = null,
    var cameraModel: String? = null,
    var lensModel: String? = null,
    var minFocalLength: Float? = null,
    var maxFocalLength: Float? = null,
    var minAperture: Float? = null,
    var maxAperture: Float? = null,
    var minIso: Int? = null,
    var maxIso: Int? = null,
    var minShutterSpeed: Float? = null,
    var maxShutterSpeed: Float? = null,
    var dateFrom: Long? = null,
    var dateTo: Long? = null
) : SleeveFilter(FilterType.EXIF) {
    override fun resetFilter() {
        cameraMake = null; cameraModel = null; lensModel = null
        minFocalLength = null; maxFocalLength = null
        minAperture = null; maxAperture = null
        minIso = null; maxIso = null
        minShutterSpeed = null; maxShutterSpeed = null
        dateFrom = null; dateTo = null
    }

    override fun getPredicate(): String {
        val conditions = mutableListOf<String>()
        cameraMake?.let { conditions.add("camera_make LIKE '%' || ? || '%'") }
        cameraModel?.let { conditions.add("camera_model LIKE '%' || ? || '%'") }
        lensModel?.let { conditions.add("lens_model LIKE '%' || ? || '%'") }
        minFocalLength?.let { conditions.add("focal_length >= ?") }
        maxFocalLength?.let { conditions.add("focal_length <= ?") }
        minAperture?.let { conditions.add("aperture >= ?") }
        maxAperture?.let { conditions.add("aperture <= ?") }
        minIso?.let { conditions.add("iso >= ?") }
        maxIso?.let { conditions.add("iso <= ?") }
        minShutterSpeed?.let { conditions.add("shutter_speed >= ?") }
        maxShutterSpeed?.let { conditions.add("shutter_speed <= ?") }
        dateFrom?.let { conditions.add("capture_date >= ?") }
        dateTo?.let { conditions.add("capture_date <= ?") }
        return conditions.joinToString(" AND ")
    }

    fun getBindArgs(): List<Any?> {
        val args = mutableListOf<Any?>()
        cameraMake?.let { args.add(it) }
        cameraModel?.let { args.add(it) }
        lensModel?.let { args.add(it) }
        minFocalLength?.let { args.add(it) }
        maxFocalLength?.let { args.add(it) }
        minAperture?.let { args.add(it) }
        maxAperture?.let { args.add(it) }
        minIso?.let { args.add(it) }
        maxIso?.let { args.add(it) }
        minShutterSpeed?.let { args.add(it) }
        maxShutterSpeed?.let { args.add(it) }
        dateFrom?.let { args.add(it) }
        dateTo?.let { args.add(it) }
        return args
    }

    override fun toJson(): String =
        """{"type":"EXIF","cameraMake":"$cameraMake","cameraModel":"$cameraModel","lensModel":"$lensModel"}"""

    override fun fromJson(json: String) {}
}

data class ValueFilter(
    var field: String = "",
    var value: String = ""
) : SleeveFilter(FilterType.VALUE) {
    override fun resetFilter() { field = ""; value = "" }
    override fun getPredicate(): String = "$field = ?"
    fun getBindArgs(): List<Any?> = listOf(value)
    override fun toJson(): String = """{"type":"VALUE","field":"$field","value":"$value"}"""
    override fun fromJson(json: String) {}
}

data class RangeFilter(
    var field: String = "",
    var minValue: Double = 0.0,
    var maxValue: Double = Double.MAX_VALUE
) : SleeveFilter(FilterType.RANGE) {
    override fun resetFilter() { field = ""; minValue = 0.0; maxValue = Double.MAX_VALUE }
    override fun getPredicate(): String = "$field BETWEEN ? AND ?"
    fun getBindArgs(): List<Any?> = listOf(minValue, maxValue)
    override fun toJson(): String = """{"type":"RANGE","field":"$field","min":$minValue,"max":$maxValue}"""
    override fun fromJson(json: String) {}
}

data class StringFilter(
    var field: String = "",
    var pattern: String = "",
    var exact: Boolean = false
) : SleeveFilter(FilterType.STRING) {
    override fun resetFilter() { field = ""; pattern = ""; exact = false }
    override fun getPredicate(): String = if (exact) "$field = ?" else "$field LIKE '%' || ? || '%'"
    fun getBindArgs(): List<Any?> = listOf(pattern)
    override fun toJson(): String =
        """{"type":"STRING","field":"$field","pattern":"$pattern","exact":$exact}"""
    override fun fromJson(json: String) {}
}

data class FilterCombo(
    val filterId: Long = 0L,
    val logic: FilterLogic = FilterLogic.AND,
    val filters: MutableList<SleeveFilter> = mutableListOf()
) {
    fun addFilter(filter: SleeveFilter) = filters.add(filter)
    fun removeFilter(filter: SleeveFilter) = filters.remove(filter)
    fun clear() = filters.clear()

    fun getCombinedPredicate(): String {
        val predicates = filters.map { it.getPredicate() }.filter { it.isNotEmpty() }
        return if (predicates.isEmpty()) ""
        else predicates.joinToString(" ${logic.name} ")
    }

    fun getAllBindArgs(): List<Any?> {
        return filters.flatMap { filter ->
            when (filter) {
                is ExifFilter -> filter.getBindArgs()
                is ValueFilter -> filter.getBindArgs()
                is RangeFilter -> filter.getBindArgs()
                is StringFilter -> filter.getBindArgs()
                else -> emptyList()
            }
        }
    }
}

data class FilterPreset(
    val presetId: Long = 0L,
    val name: String,
    val filterCombo: FilterCombo,
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
) {
    fun toEntity(): FilterPresetEntity = FilterPresetEntity(
        presetId = presetId,
        name = name,
        filterJson = filterCombo.toJson(),
        createdAt = createdAt,
        isDefault = isDefault
    )

    companion object {
        fun fromEntity(entity: FilterPresetEntity): FilterPreset {
            val combo = FilterCombo()
            // Parse JSON back to FilterCombo
            return FilterPreset(
                presetId = entity.presetId,
                name = entity.name,
                filterCombo = combo,
                createdAt = entity.createdAt,
                isDefault = entity.isDefault
            )
        }
    }
}

// ================================================================
// Filter helper types
// ================================================================

data class DateRangeFilter(
    val field: String = "capture_date",
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE
)

data class ValueRangeFilter(
    val field: String,
    val from: Double,
    val to: Double
)

data class IntRangeFilter(
    val field: String,
    val from: Int,
    val to: Int
)

// ================================================================
// FilterCombo JSON serialization
// ================================================================

fun FilterCombo.toJson(): String {
    val filterJsons = filters.map { it.toJson() }
    return """{"filterId":$filterId,"logic":"${logic.name}","filters":[${filterJsons.joinToString(",")}]}"""
}

// ================================================================
// Companion object for entity conversion
// ================================================================

fun SleeveElementEntity.toDomain(
    fileEntity: SleeveFileEntity? = null,
    folderEntity: SleeveFolderEntity? = null
): SleeveElement {
    return if (elementType == 0) {
        // FILE
        SleeveFile(
            elementId = elementId,
            elementName = elementName,
            imageId = fileEntity?.imageId ?: 0L,
            filePath = fileEntity?.filePath ?: "",
            fileSize = fileEntity?.fileSize ?: 0L,
            fileExtension = fileEntity?.fileExtension ?: "",
            mimeType = fileEntity?.mimeType ?: "",
            checksum = fileEntity?.checksum ?: 0L,
            currentVersionId = fileEntity?.currentVersionId,
            width = fileEntity?.width ?: 0,
            height = fileEntity?.height ?: 0,
            hasThumbnail = fileEntity?.hasThumbnail ?: false,
            hasFullImage = fileEntity?.hasFullImage ?: false,
            parentId = parentId,
            addedTime = Instant.ofEpochMilli(addedTime),
            lastModifiedTime = Instant.ofEpochMilli(lastModifiedTime),
            refCount = refCount,
            pinned = pinned,
            syncFlag = SyncFlag.entries.getOrElse(syncFlag) { SyncFlag.UNSYNC }
        )
    } else {
        // FOLDER
        SleeveFolder(
            elementId = elementId,
            elementName = elementName,
            childCount = folderEntity?.childCount ?: 0,
            fileCount = folderEntity?.fileCount ?: 0,
            folderCount = folderEntity?.folderCount ?: 0,
            defaultFilterId = folderEntity?.defaultFilterId ?: 0L,
            thumbnailElementId = folderEntity?.thumbnailElementId,
            childrenLoaded = folderEntity?.childrenLoaded ?: false,
            parentId = parentId,
            addedTime = Instant.ofEpochMilli(addedTime),
            lastModifiedTime = Instant.ofEpochMilli(lastModifiedTime),
            refCount = refCount,
            pinned = pinned,
            syncFlag = SyncFlag.entries.getOrElse(syncFlag) { SyncFlag.UNSYNC }
        )
    }
}