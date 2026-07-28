package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * Non-destructive edit history. Edits are recorded as a version tree: every
 * [Version] derives from a parent (the initial import has a null parent) and
 * carries an ordered list of [EditTransaction]s. Each transaction captures a
 * delta of [AdjustmentParams] plus optional mask and geometry changes.
 *
 * This mirrors the desktop core/edit/history structures (Version,
 * EditTransaction, EditHistory) so the Kotlin UI can render the version tree
 * and the native pipeline can replay a version deterministically.
 */
@Serializable
data class EditHistory(
    val imageId: String,
    val versions: List<Version>,
    val currentVersionId: String,
) {
    val current: Version?
        get() = versions.firstOrNull { it.id == currentVersionId }

    fun ancestorsOf(versionId: String): List<Version> {
        val ordered = mutableListOf<Version>()
        var current = versions.firstOrNull { it.id == versionId }
        while (current != null) {
            ordered.add(0, current)
            current = current.parentId?.let { pid -> versions.firstOrNull { it.id == pid } }
        }
        return ordered
    }
}

@Serializable
data class Version(
    val id: String,
    val imageId: String,
    val parentId: String?,
    val name: String,
    val createdAt: Long,
    val transactions: List<EditTransaction>,
    val cumulativeParams: AdjustmentParams,
    val isVirtualCopy: Boolean = false,
    val isActive: Boolean = false,
    val note: String? = null,
) {
    val depth: Int get() = 0 // computed via EditHistory.ancestorsOf at runtime
}

@Serializable
data class EditTransaction(
    val id: String,
    val versionId: String,
    val timestamp: Long,
    val label: String,
    val paramDelta: AdjustmentParamsDelta,
    val maskIds: List<String> = emptyList(),
    val geometryDelta: GeometryDelta? = null,
    val source: TransactionSource = TransactionSource.MANUAL,
)

@Serializable
enum class TransactionSource {
    MANUAL, PRESET, BATCH_EDIT, AI_SUGGESTION, IMPORTED, UNDO, REDO
}

/**
 * A sparse delta of [AdjustmentParams]. Only fields explicitly set in [overrides]
 * (matched by name) are applied when replaying. Kept as a map of field name ->
 * raw value string so the delta stays schema-tolerant across versions.
 */
@Serializable
data class AdjustmentParamsDelta(
    val overrides: Map<String, String> = emptyMap(),
) {
    fun isEmpty(): Boolean = overrides.isEmpty()
    fun isNotEmpty(): Boolean = overrides.isNotEmpty()
    operator fun plus(other: AdjustmentParamsDelta): AdjustmentParamsDelta =
        AdjustmentParamsDelta(overrides + other.overrides)
}

@Serializable
data class GeometryDelta(
    val cropLeft: Float? = null,
    val cropTop: Float? = null,
    val cropRight: Float? = null,
    val cropBottom: Float? = null,
    val rotation: Float? = null,
    val flipH: Boolean? = null,
    val flipV: Boolean? = null,
    val perspectiveH: Float? = null,
    val perspectiveV: Float? = null,
) {
    fun isEmpty(): Boolean = cropLeft == null && cropTop == null && cropRight == null &&
        cropBottom == null && rotation == null && flipH == null && flipV == null &&
        perspectiveH == null && perspectiveV == null
}

/** Apply a delta onto a baseline [AdjustmentParams] by field name. */
fun AdjustmentParams.applyDelta(delta: AdjustmentParamsDelta): AdjustmentParams {
    if (delta.isEmpty()) return this
    return copy(
        exposure = delta.overrides["exposure"]?.toFloatOrNull() ?: exposure,
        contrast = delta.overrides["contrast"]?.toFloatOrNull() ?: contrast,
        highlights = delta.overrides["highlights"]?.toFloatOrNull() ?: highlights,
        shadows = delta.overrides["shadows"]?.toFloatOrNull() ?: shadows,
        whites = delta.overrides["whites"]?.toFloatOrNull() ?: whites,
        blacks = delta.overrides["blacks"]?.toFloatOrNull() ?: blacks,
        temperature = delta.overrides["temperature"]?.toFloatOrNull() ?: temperature,
        tint = delta.overrides["tint"]?.toFloatOrNull() ?: tint,
        saturation = delta.overrides["saturation"]?.toFloatOrNull() ?: saturation,
        vibrance = delta.overrides["vibrance"]?.toFloatOrNull() ?: vibrance,
        clarity = delta.overrides["clarity"]?.toFloatOrNull() ?: clarity,
        sharpen = delta.overrides["sharpen"]?.toFloatOrNull() ?: sharpen,
        rotation = delta.overrides["rotation"]?.toFloatOrNull() ?: rotation,
        perspectiveH = delta.overrides["perspectiveH"]?.toFloatOrNull() ?: perspectiveH,
        perspectiveV = delta.overrides["perspectiveV"]?.toFloatOrNull() ?: perspectiveV,
        filmGrainAmount = delta.overrides["filmGrainAmount"]?.toFloatOrNull() ?: filmGrainAmount,
        halationAmount = delta.overrides["halationAmount"]?.toFloatOrNull() ?: halationAmount,
        lutIntensity = delta.overrides["lutIntensity"]?.toFloatOrNull() ?: lutIntensity,
    )
}
