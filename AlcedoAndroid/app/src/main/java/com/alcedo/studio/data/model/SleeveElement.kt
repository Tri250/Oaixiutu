package com.alcedo.studio.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.json.*
import org.json.JSONObject
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
    abstract fun getBindArgs(): Array<Any?>
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
        "added_time BETWEEN ? AND ? ORDER BY added_time ${if (order == ElementOrder.ASC) "ASC" else "DESC"}"

    override fun getBindArgs(): Array<Any?> = arrayOf(startDate, endDate)

    override fun toJson(): String =
        """{"type":"DATETIME","startDate":$startDate,"endDate":$endDate,"order":"$order"}"""

    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        startDate = obj.optLong("startDate", 0L)
        endDate = obj.optLong("endDate", Long.MAX_VALUE)
        order = runCatching { ElementOrder.valueOf(obj.optString("order", "DESC")) }
            .getOrDefault(ElementOrder.DESC)
    }
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

    override fun getBindArgs(): Array<Any?> {
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
        return args.toTypedArray()
    }

    override fun toJson(): String =
        """{"type":"EXIF","cameraMake":"$cameraMake","cameraModel":"$cameraModel","lensModel":"$lensModel"}"""

    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        cameraMake = obj.optString("cameraMake", "").takeIf { it.isNotEmpty() && it != "null" }
        cameraModel = obj.optString("cameraModel", "").takeIf { it.isNotEmpty() && it != "null" }
        lensModel = obj.optString("lensModel", "").takeIf { it.isNotEmpty() && it != "null" }
        minFocalLength = obj.optFloatOrNull("minFocalLength")
        maxFocalLength = obj.optFloatOrNull("maxFocalLength")
        minAperture = obj.optFloatOrNull("minAperture")
        maxAperture = obj.optFloatOrNull("maxAperture")
        minIso = obj.optIntOrNull("minIso")
        maxIso = obj.optIntOrNull("maxIso")
        minShutterSpeed = obj.optFloatOrNull("minShutterSpeed")
        maxShutterSpeed = obj.optFloatOrNull("maxShutterSpeed")
        dateFrom = obj.optLongOrNull("dateFrom")
        dateTo = obj.optLongOrNull("dateTo")
    }

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null
}

data class ValueFilter(
    var field: String = "",
    var value: String = ""
) : SleeveFilter(FilterType.VALUE) {
    override fun resetFilter() { field = ""; value = "" }
    override fun getPredicate(): String = "$field = ?"
    override fun getBindArgs(): Array<Any?> = arrayOf(value)
    override fun toJson(): String = """{"type":"VALUE","field":"$field","value":"$value"}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        field = obj.optString("field", "")
        value = obj.optString("value", "")
    }
}

data class RangeFilter(
    var field: String = "",
    var minValue: Double = 0.0,
    var maxValue: Double = Double.MAX_VALUE
) : SleeveFilter(FilterType.RANGE) {
    override fun resetFilter() { field = ""; minValue = 0.0; maxValue = Double.MAX_VALUE }
    override fun getPredicate(): String = "$field BETWEEN ? AND ?"
    override fun getBindArgs(): Array<Any?> = arrayOf(minValue, maxValue)
    override fun toJson(): String = """{"type":"RANGE","field":"$field","min":$minValue,"max":$maxValue}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        field = obj.optString("field", "")
        minValue = obj.optDouble("min", 0.0)
        maxValue = obj.optDouble("max", Double.MAX_VALUE)
    }
}

data class StringFilter(
    var field: String = "",
    var pattern: String = "",
    var exact: Boolean = false
) : SleeveFilter(FilterType.STRING) {
    override fun resetFilter() { field = ""; pattern = ""; exact = false }
    override fun getPredicate(): String = if (exact) "$field = ?" else "$field LIKE '%' || ? || '%'"
    override fun getBindArgs(): Array<Any?> = arrayOf(pattern)
    override fun toJson(): String =
        """{"type":"STRING","field":"$field","pattern":"$pattern","exact":$exact}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        field = obj.optString("field", "")
        pattern = obj.optString("pattern", "")
        exact = obj.optBoolean("exact", false)
    }
}

data class FilterCombo(
    val filterId: Long = 0L,
    val logic: FilterLogic = FilterLogic.AND,
    val filters: MutableList<SleeveFilter> = mutableListOf(),
    val rootNode: FilterNode? = null
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
        return filters.flatMap { it.getBindArgs().toList() }
    }

    fun toEntity(): FilterEntity = FilterEntity(
        filterId = filterId,
        name = "",
        filterJson = toJson()
    )

    companion object {
        fun fromEntity(entity: FilterEntity): FilterCombo {
            return fromJson(entity.filterJson)
        }

        fun fromJson(jsonString: String): FilterCombo {
            return try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(jsonString).jsonObject
                val filterId = element["filterId"]?.jsonPrimitive?.longOrNull ?: 0L
                val logicName = element["logic"]?.jsonPrimitive?.contentOrNull ?: "AND"
                val logic = runCatching { FilterLogic.valueOf(logicName) }.getOrDefault(FilterLogic.AND)

                val filters = mutableListOf<SleeveFilter>()
                element["filters"]?.jsonArray?.forEach { filterElement ->
                    parseFilterNode(filterElement.jsonObject)?.let { filters.add(it) }
                }

                val rootNode = element["rootNode"]?.let { parseNodeTree(it.jsonObject) }

                FilterCombo(filterId = filterId, logic = logic, filters = filters, rootNode = rootNode)
            } catch (_: Exception) {
                FilterCombo()
            }
        }

        private fun parseFilterNode(obj: kotlinx.serialization.json.JsonObject): SleeveFilter? {
            val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
            return when (type) {
                "RATING" -> RatingFilterNode(
                    minRating = obj["minRating"]?.jsonPrimitive?.intOrNull ?: 0,
                    maxRating = obj["maxRating"]?.jsonPrimitive?.intOrNull ?: 5
                )
                "FILE_TYPE" -> FileTypeFilterNode(
                    imageTypes = obj["imageTypes"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList()
                )
                "DATE_RANGE" -> DateRangeFilterNode(
                    from = obj["from"]?.jsonPrimitive?.longOrNull ?: 0L,
                    to = obj["to"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
                )
                "CAMERA" -> CameraFilterNode(
                    cameraMake = obj["cameraMake"]?.jsonPrimitive?.contentOrNull,
                    cameraModel = obj["cameraModel"]?.jsonPrimitive?.contentOrNull
                )
                "LENS" -> LensFilterNode(
                    lensModel = obj["lensModel"]?.jsonPrimitive?.contentOrNull
                )
                "TAG" -> TagFilterNode(
                    tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                )
                "COLLECTION" -> CollectionFilterNode(
                    collectionId = obj["collectionId"]?.jsonPrimitive?.longOrNull ?: 0L
                )
                "EXIF" -> ExifFilter(
                    cameraMake = obj["cameraMake"]?.jsonPrimitive?.contentOrNull,
                    cameraModel = obj["cameraModel"]?.jsonPrimitive?.contentOrNull,
                    lensModel = obj["lensModel"]?.jsonPrimitive?.contentOrNull
                )
                "DATETIME" -> DateTimeFilter(
                    startDate = obj["startDate"]?.jsonPrimitive?.longOrNull ?: 0L,
                    endDate = obj["endDate"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
                )
                else -> null
            }
        }

        private fun parseNodeTree(obj: kotlinx.serialization.json.JsonObject): FilterNode? {
            val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: return null
            return when (kind) {
                "LEAF" -> {
                    val filter = obj["filter"]?.jsonObject?.let { parseFilterNode(it) } ?: return null
                    FilterNode.Leaf(filter)
                }
                "AND" -> {
                    val children = obj["children"]?.jsonArray?.mapNotNull { parseNodeTree(it.jsonObject) } ?: emptyList()
                    FilterNode.And(children)
                }
                "OR" -> {
                    val children = obj["children"]?.jsonArray?.mapNotNull { parseNodeTree(it.jsonObject) } ?: emptyList()
                    FilterNode.Or(children)
                }
                "NOT" -> {
                    val child = obj["child"]?.jsonObject?.let { parseNodeTree(it) } ?: return null
                    FilterNode.Not(child)
                }
                else -> null
            }
        }
    }
}

// ================================================================
// FilterNode Tree Structure (AND/OR/NOT logic)
// ================================================================

sealed class FilterNode {
    abstract fun toJsonElement(): kotlinx.serialization.json.JsonElement
    abstract fun evaluate(imageId: Long, context: FilterEvaluationContext): Boolean

    data class Leaf(val filter: SleeveFilter) : FilterNode() {
        override fun toJsonElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("kind", "LEAF")
            put("filter", filter.toJsonNodeElement())
        }

        override fun evaluate(imageId: Long, context: FilterEvaluationContext): Boolean {
            return context.evaluateLeaf(imageId, filter)
        }
    }

    data class And(val children: List<FilterNode>) : FilterNode() {
        override fun toJsonElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("kind", "AND")
            put("children", buildJsonArray { children.forEach { add(it.toJsonElement()) } })
        }

        override fun evaluate(imageId: Long, context: FilterEvaluationContext): Boolean {
            return children.all { it.evaluate(imageId, context) }
        }
    }

    data class Or(val children: List<FilterNode>) : FilterNode() {
        override fun toJsonElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("kind", "OR")
            put("children", buildJsonArray { children.forEach { add(it.toJsonElement()) } })
        }

        override fun evaluate(imageId: Long, context: FilterEvaluationContext): Boolean {
            return children.any { it.evaluate(imageId, context) }
        }
    }

    data class Not(val child: FilterNode) : FilterNode() {
        override fun toJsonElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("kind", "NOT")
            put("child", child.toJsonElement())
        }

        override fun evaluate(imageId: Long, context: FilterEvaluationContext): Boolean {
            return !child.evaluate(imageId, context)
        }
    }
}

// ================================================================
// Filter Evaluation Context (injected by service layer)
// ================================================================

interface FilterEvaluationContext {
    fun evaluateLeaf(imageId: Long, filter: SleeveFilter): Boolean
}

// ================================================================
// Specific Filter Types for FilterNode tree
// ================================================================

data class RatingFilterNode(
    var minRating: Int = 0,
    var maxRating: Int = 5
) : SleeveFilter(FilterType.RANGE) {
    override fun resetFilter() { minRating = 0; maxRating = 5 }
    override fun getPredicate(): String = "rating BETWEEN ? AND ?"
    override fun getBindArgs(): Array<Any?> = arrayOf(minRating, maxRating)
    override fun toJson(): String = """{"type":"RATING","minRating":$minRating,"maxRating":$maxRating}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        minRating = obj.optInt("minRating", 0)
        maxRating = obj.optInt("maxRating", 5)
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "RATING")
        put("minRating", minRating)
        put("maxRating", maxRating)
    }
}

data class FileTypeFilterNode(
    var imageTypes: List<String> = emptyList()
) : SleeveFilter(FilterType.VALUE) {
    override fun resetFilter() { imageTypes = emptyList() }
    override fun getPredicate(): String =
        if (imageTypes.isEmpty()) "" else "image_type IN (${imageTypes.joinToString(",") { "?" }})"
    override fun getBindArgs(): Array<Any?> = imageTypes.toTypedArray()
    override fun toJson(): String = """{"type":"FILE_TYPE","imageTypes":${imageTypes.joinToString(",", "[", "]") { "\"$it\"" }}}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        val typesArray = obj.optJSONArray("imageTypes")
        imageTypes = if (typesArray != null) {
            (0 until typesArray.length()).map { typesArray.getString(it) }
        } else emptyList()
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "FILE_TYPE")
        put("imageTypes", buildJsonArray { imageTypes.forEach { add(it) } })
    }
}

data class DateRangeFilterNode(
    var from: Long = 0L,
    var to: Long = Long.MAX_VALUE
) : SleeveFilter(FilterType.DATETIME) {
    override fun resetFilter() { from = 0L; to = Long.MAX_VALUE }
    override fun getPredicate(): String = "capture_date BETWEEN ? AND ?"
    override fun getBindArgs(): Array<Any?> = arrayOf(from, to)
    override fun toJson(): String = """{"type":"DATE_RANGE","from":$from,"to":$to}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        from = obj.optLong("from", 0L)
        to = obj.optLong("to", Long.MAX_VALUE)
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "DATE_RANGE")
        put("from", from)
        put("to", to)
    }
}

data class CameraFilterNode(
    var cameraMake: String? = null,
    var cameraModel: String? = null
) : SleeveFilter(FilterType.EXIF) {
    override fun resetFilter() { cameraMake = null; cameraModel = null }
    override fun getPredicate(): String {
        val conditions = mutableListOf<String>()
        cameraMake?.let { conditions.add("camera_make LIKE '%' || ? || '%'") }
        cameraModel?.let { conditions.add("camera_model LIKE '%' || ? || '%'") }
        return conditions.joinToString(" AND ")
    }
    override fun getBindArgs(): Array<Any?> = listOf(cameraMake, cameraModel).filterNotNull().toTypedArray()
    override fun toJson(): String = """{"type":"CAMERA","cameraMake":"$cameraMake","cameraModel":"$cameraModel"}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        cameraMake = obj.optString("cameraMake", "").takeIf { it.isNotEmpty() && it != "null" }
        cameraModel = obj.optString("cameraModel", "").takeIf { it.isNotEmpty() && it != "null" }
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "CAMERA")
        cameraMake?.let { put("cameraMake", it) }
        cameraModel?.let { put("cameraModel", it) }
    }
}

data class LensFilterNode(
    var lensModel: String? = null
) : SleeveFilter(FilterType.EXIF) {
    override fun resetFilter() { lensModel = null }
    override fun getPredicate(): String = lensModel?.let { "lens_model LIKE '%' || ? || '%'" } ?: ""
    override fun getBindArgs(): Array<Any?> = listOf(lensModel).filterNotNull().toTypedArray()
    override fun toJson(): String = """{"type":"LENS","lensModel":"$lensModel"}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        lensModel = obj.optString("lensModel", "").takeIf { it.isNotEmpty() && it != "null" }
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "LENS")
        lensModel?.let { put("lensModel", it) }
    }
}

data class TagFilterNode(
    var tags: List<String> = emptyList()
) : SleeveFilter(FilterType.STRING) {
    override fun resetFilter() { tags = emptyList() }
    override fun getPredicate(): String =
        if (tags.isEmpty()) "" else "label IN (${tags.joinToString(",") { "?" }})"
    override fun getBindArgs(): Array<Any?> = tags.toTypedArray()
    override fun toJson(): String = """{"type":"TAG","tags":${tags.joinToString(",", "[", "]") { "\"$it\"" }}}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        val tagsArray = obj.optJSONArray("tags")
        tags = if (tagsArray != null) {
            (0 until tagsArray.length()).map { tagsArray.getString(it) }
        } else emptyList()
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "TAG")
        put("tags", buildJsonArray { tags.forEach { add(it) } })
    }
}

data class CollectionFilterNode(
    var collectionId: Long = 0L
) : SleeveFilter(FilterType.COMBO) {
    override fun resetFilter() { collectionId = 0L }
    override fun getPredicate(): String = "image_id IN (SELECT image_id FROM collection_images WHERE collection_id = ?)"
    override fun getBindArgs(): Array<Any?> = arrayOf(collectionId)
    override fun toJson(): String = """{"type":"COLLECTION","collectionId":$collectionId}"""
    override fun fromJson(json: String) {
        val obj = JSONObject(json)
        collectionId = obj.optLong("collectionId", 0L)
    }
    fun toJsonNodeElement(): kotlinx.serialization.json.JsonObject = buildJsonObject {
        put("type", "COLLECTION")
        put("collectionId", collectionId)
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
            val combo = FilterCombo.fromJson(entity.filterJson)
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
    val rootNodeJson = rootNode?.toJsonElement()?.toString()
    return """{"filterId":$filterId,"logic":"${logic.name}","filters":[${filterJsons.joinToString(",")}],"rootNode":${rootNodeJson ?: "null"}}"""
}

// ================================================================
// JSON builder helpers for FilterNode
// ================================================================

private fun buildJsonObject(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): kotlinx.serialization.json.JsonObject {
    return kotlinx.serialization.json.buildJsonObject(builder)
}

private fun buildJsonArray(builder: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit): kotlinx.serialization.json.JsonArray {
    return kotlinx.serialization.json.buildJsonArray(builder)
}

private fun SleeveFilter.toJsonNodeElement(): kotlinx.serialization.json.JsonObject {
    return when (this) {
        is RatingFilterNode -> toJsonNodeElement()
        is FileTypeFilterNode -> toJsonNodeElement()
        is DateRangeFilterNode -> toJsonNodeElement()
        is CameraFilterNode -> toJsonNodeElement()
        is LensFilterNode -> toJsonNodeElement()
        is TagFilterNode -> toJsonNodeElement()
        is CollectionFilterNode -> toJsonNodeElement()
        else -> buildJsonObject { put("type", type.name) }
    }
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