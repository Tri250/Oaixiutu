package com.alcedo.studio.data.model

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.security.MessageDigest

enum class OperatorType {
    EXPOSURE, CONTRAST, SATURATION, VIBRANCE, HIGHLIGHTS, SHADOWS,
    WHITE_BALANCE, TONE_CURVE, HSL, COLOR_WHEEL, GEOMETRY, CROP,
    FILM_GRAIN, HALATION, LUT, DISPLAY_TRANSFORM, RAW_DECODE,
    SHARPEN, CLARITY, TINT, TONE_REGION, LENS_CORRECTION
}

data class EditTransaction(
    val transactionId: UInt,
    val operatorType: OperatorType,
    val paramsBefore: JsonObject,
    val paramsAfter: JsonObject,
    val timestamp: Instant = Instant.now()
)

data class VersionNode(
    val versionId: String,
    val commitId: String
)

data class Version(
    val versionId: String = generateHash128(),
    val displayName: String = "",
    val boundImageId: UInt = 0u,
    val addedTime: Instant = Instant.now(),
    val lastModifiedTime: Instant = Instant.now(),
    val creationNonce: ULong = 0u,
    val materializedParams: JsonObject? = null,
    val transactions: MutableList<EditTransaction> = mutableListOf(),
    var cursor: Int = 0,
    val versionHash: String = ""
) {
    fun appendTransaction(tx: EditTransaction) {
        if (cursor < transactions.size) {
            transactions.subList(cursor, transactions.size).clear()
        }
        transactions.add(tx)
        cursor = transactions.size
    }

    fun undo(): EditTransaction? {
        if (cursor > 0) {
            cursor--
            return transactions[cursor]
        }
        return null
    }

    fun redo(): EditTransaction? {
        if (cursor < transactions.size) {
            cursor++
            return transactions[cursor - 1]
        }
        return null
    }

    fun appliedTransactions(): List<EditTransaction> = transactions.take(cursor)

    companion object {
        fun default(boundImageId: UInt, params: JsonObject?): Version =
            Version(
                versionId = generateHash128(),
                displayName = "Default",
                boundImageId = boundImageId,
                materializedParams = params
            )

        fun empty(boundImageId: UInt, displayName: String, materializedParams: JsonObject? = null): Version =
            Version(
                versionId = generateHash128(),
                displayName = displayName,
                boundImageId = boundImageId,
                materializedParams = materializedParams
            )
    }
}

data class WorkingVersion(
    val versionId: String = "",
    val boundImageId: UInt = 0u,
    val transactions: MutableList<EditTransaction> = mutableListOf(),
    var cursor: Int = 0,
    var headPipelineParams: JsonObject? = null
) {
    private var txIdGenerator: UInt = 0u

    fun appendTransaction(tx: EditTransaction) {
        if (cursor < transactions.size) {
            transactions.subList(cursor, transactions.size).clear()
        }
        transactions.add(tx.copy(transactionId = txIdGenerator++))
        cursor = transactions.size
    }

    fun undo(): Boolean {
        if (cursor > 0) {
            cursor--
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (cursor < transactions.size) {
            cursor++
            return true
        }
        return false
    }

    fun appliedTransactions(): List<EditTransaction> = transactions.take(cursor)
}

data class EditHistory(
    val historyId: String = generateHash128(),
    val boundImageId: UInt = 0u,
    val addedTime: Instant = Instant.now(),
    val lastModifiedTime: Instant = Instant.now(),
    val versionOrder: MutableList<VersionNode> = mutableListOf(),
    val versionStorage: MutableMap<String, Version> = mutableMapOf(),
    var defaultVersionId: String = "",
    var activeVersionId: String = "",
    val importPipelineParams: JsonObject = JsonObject(emptyMap()),
    var activePipelineParams: JsonObject? = null
) {
    init {
        if (defaultVersionId.isEmpty()) {
            val default = Version.default(boundImageId, importPipelineParams)
            defaultVersionId = default.versionId
            activeVersionId = default.versionId
            versionStorage[default.versionId] = default
            versionOrder.add(VersionNode(default.versionId, ""))
        }
    }

    fun getVersion(versionId: String): Version? = versionStorage[versionId]

    fun getDefaultVersion(): Version? = versionStorage[defaultVersionId]

    fun getActiveVersion(): Version? = versionStorage[activeVersionId]

    fun createVersion(displayName: String = ""): String {
        val version = Version.empty(boundImageId, displayName.ifEmpty { "Version ${versionOrder.size + 1}" })
        versionStorage[version.versionId] = version
        versionOrder.add(VersionNode(version.versionId, ""))
        return version.versionId
    }

    fun setActiveVersion(versionId: String) {
        if (versionStorage.containsKey(versionId)) {
            activeVersionId = versionId
        }
    }

    fun cloneForFile(newBoundImageId: UInt): EditHistory {
        return copy(
            historyId = generateHash128(),
            boundImageId = newBoundImageId,
            versionOrder = versionOrder.map { it.copy() }.toMutableList(),
            versionStorage = versionStorage.mapValues { it.value.copy() }.toMutableMap()
        )
    }
}

fun generateHash128(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest((System.currentTimeMillis() + kotlin.random.Random.nextLong()).toString().toByteArray())
    return bytes.take(16).joinToString("") { "%02x".format(it) }
}
