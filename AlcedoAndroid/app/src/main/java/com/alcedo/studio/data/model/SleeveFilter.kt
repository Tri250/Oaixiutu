package com.alcedo.studio.data.model

enum class FilterType {
    DATETIME, EXIF, VALUE, RANGE, DEFAULT
}

enum class ElementOrder {
    ASC, DESC
}

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

    override fun getPredicate(): String = "added_time BETWEEN $startDate AND $endDate ORDER BY added_time ${if (order == ElementOrder.ASC) "ASC" else "DESC"}"

    override fun toJson(): String = """{"startDate":$startDate,"endDate":$endDate,"order":"$order"}"""

    override fun fromJson(json: String) {
        // Parse JSON
    }
}

data class ExifFilter(
    var cameraMake: String? = null,
    var cameraModel: String? = null,
    var lensModel: String? = null,
    var minIso: Int? = null,
    var maxIso: Int? = null
) : SleeveFilter(FilterType.EXIF) {
    override fun resetFilter() {
        cameraMake = null
        cameraModel = null
        lensModel = null
        minIso = null
        maxIso = null
    }

    override fun getPredicate(): String {
        val conditions = mutableListOf<String>()
        cameraMake?.let { conditions.add("camera_make LIKE '%$it%'") }
        cameraModel?.let { conditions.add("camera_model LIKE '%$it%'") }
        lensModel?.let { conditions.add("lens_model LIKE '%$it%'") }
        minIso?.let { conditions.add("iso >= $it") }
        maxIso?.let { conditions.add("iso <= $it") }
        return conditions.joinToString(" AND ")
    }

    override fun toJson(): String = """{"cameraMake":"$cameraMake","cameraModel":"$cameraModel"}"""

    override fun fromJson(json: String) {}
}

data class ValueFilter(
    var field: String = "",
    var value: String = ""
) : SleeveFilter(FilterType.VALUE) {
    override fun resetFilter() {
        field = ""
        value = ""
    }

    override fun getPredicate(): String = "$field = '$value'"
    override fun toJson(): String = """{"field":"$field","value":"$value"}"""
    override fun fromJson(json: String) {}
}

data class RangeFilter(
    var field: String = "",
    var minValue: Double = 0.0,
    var maxValue: Double = Double.MAX_VALUE
) : SleeveFilter(FilterType.RANGE) {
    override fun resetFilter() {
        field = ""
        minValue = 0.0
        maxValue = Double.MAX_VALUE
    }

    override fun getPredicate(): String = "$field BETWEEN $minValue AND $maxValue"
    override fun toJson(): String = """{"field":"$field","min":$minValue,"max":$maxValue}"""
    override fun fromJson(json: String) {}
}

data class FilterCombo(
    val filterId: UInt = 0u,
    val filters: MutableList<SleeveFilter> = mutableListOf()
) {
    fun addFilter(filter: SleeveFilter) = filters.add(filter)
    fun removeFilter(filter: SleeveFilter) = filters.remove(filter)
    fun clear() = filters.clear()

    fun getCombinedPredicate(): String {
        return filters.map { it.getPredicate() }.filter { it.isNotEmpty() }.joinToString(" AND ")
    }
}
