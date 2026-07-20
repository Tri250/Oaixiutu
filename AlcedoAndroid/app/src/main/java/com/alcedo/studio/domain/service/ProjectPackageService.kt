package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.*
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectPackageService {
    companion object {
        private const val TAG = "ProjectPackageService"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val HISTORY_ENTRY = "edit_histories.json"
        private const val IMAGE_META_ENTRY = "image_metadata.json"
        private const val ASSETS_DIR = "assets/"
        private const val VERSION_KEY = "package_version"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ── Serialization ──

    suspend fun serializeProject(
        project: Project,
        sleeveRepository: SleeveRepository,
        editHistoryRepository: EditHistoryRepository,
        imageRepository: ImageRepository
    ): ProjectPackage = withContext(Dispatchers.IO) {
        Log.i(TAG, "Serializing project: ${project.projectName}")

        // Collect sleeve tree
        val sleeveTree = collectSleeveTree(project.sleeveRootId.toLong(), sleeveRepository)

        // Collect edit histories
        val editHistories = mutableMapOf<UInt, EditHistory>()
        val allImages = imageRepository.getAllImages()
        for (image in allImages) {
            val history = editHistoryRepository.getHistory(image.imageId.toUInt())
            if (history != null) {
                editHistories[image.imageId.toUInt()] = history
            }
        }

        // Collect image metadata
        val imageMetadata = allImages.map { image ->
            ImageMetaSnapshot(
                imageId = image.imageId.toUInt(),
                imagePath = image.imagePath,
                imageName = image.imageName,
                imageType = image.imageType.name,
                checksum = image.checksum.toULong(),
                exifDisplay = image.exifDisplay
            )
        }

        // Collect embedded assets (thumbnails)
        val embeddedAssets = collectEmbeddedAssets(allImages, imageRepository)

        ProjectPackage(
            version = project.packageVersion,
            project = project,
            sleeveTree = sleeveTree,
            editHistories = editHistories,
            imageMetadata = imageMetadata,
            embeddedAssets = embeddedAssets,
            createdAt = Instant.now()
        )
    }

    suspend fun writePackageToFile(pkg: ProjectPackage, filePath: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Writing package to: $filePath")

        val file = File(filePath)
        file.parentFile?.mkdirs()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)

            // Write manifest (project metadata + sleeve tree)
            zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            val manifestJson = json.encodeToString(ProjectPackage.serializer(), pkg.copy(
                embeddedAssets = emptyList(), // Assets written separately
                editHistories = emptyMap()    // Histories written separately
            ))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Write edit histories
            if (pkg.editHistories.isNotEmpty()) {
                zos.putNextEntry(ZipEntry(HISTORY_ENTRY))
                val histories = pkg.editHistories.mapValues { (_, history) ->
                    serializeHistory(history)
                }
                val historiesJson = json.encodeToString(
                    kotlinx.serialization.builtins.MapSerializer(
                        serializer<String>(),
                        serializer<String>()
                    ), histories.mapKeys { it.key.toString() }
                )
                zos.write(compressString(historiesJson))
                zos.closeEntry()
            }

            // Write image metadata
            if (pkg.imageMetadata.isNotEmpty()) {
                zos.putNextEntry(ZipEntry(IMAGE_META_ENTRY))
                val metaJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(ImageMetaSnapshot.serializer()),
                    pkg.imageMetadata
                )
                zos.write(metaJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // Write embedded assets
            for (asset in pkg.embeddedAssets) {
                val entryName = "$ASSETS_DIR${asset.assetType.name.lowercase()}/${asset.assetId}"
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(asset.data)
                zos.closeEntry()
            }
        }
    }

    // ── Deserialization ──

    suspend fun deserializeProject(filePath: String): ProjectPackage = withContext(Dispatchers.IO) {
        Log.i(TAG, "Deserializing project from: $filePath")

        val file = File(filePath)
        if (!file.exists()) throw FileNotFoundException("Project file not found: $filePath")

        var pkg: ProjectPackage? = null
        val editHistories = mutableMapOf<UInt, EditHistory>()
        val embeddedAssets = mutableListOf<EmbeddedAsset>()

        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == MANIFEST_ENTRY -> {
                        val manifestJson = zis.bufferedReader().readText()
                        pkg = json.decodeFromString(ProjectPackage.serializer(), manifestJson)
                    }
                    entry.name == HISTORY_ENTRY -> {
                        val compressed = zis.readBytes()
                        val historiesJson = decompressString(compressed)
                        val historyMap = json.decodeFromString<Map<String, String>>(historiesJson)
                        for ((key, value) in historyMap) {
                            val imageId = key.toUInt()
                            val history = deserializeHistory(value)
                            editHistories[imageId] = history
                        }
                    }
                    entry.name == IMAGE_META_ENTRY -> {
                        // Already handled in manifest
                    }
                    entry.name.startsWith(ASSETS_DIR) -> {
                        val parts = entry.name.removePrefix(ASSETS_DIR).split("/")
                        if (parts.size >= 2) {
                            val assetType = try {
                                AssetType.valueOf(parts[0].uppercase())
                            } catch (_: Exception) {
                                AssetType.THUMBNAIL
                            }
                            val assetId = parts[1]
                            val data = zis.readBytes()
                            embeddedAssets.add(EmbeddedAsset(
                                assetId = assetId,
                                assetType = assetType,
                                data = data
                            ))
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val result = pkg ?: throw IllegalStateException("Manifest not found in project package")

        // Validate deserialized edit histories
        val validatedHistories = editHistories.mapValues { (imageId, history) ->
            if (history.boundImageId == 0u || imageId == 0u) {
                Log.w(TAG, "Edit history with invalid boundImageId=$imageId, skipping")
                history.copy(boundImageId = imageId)
            } else {
                history
            }
        }.filterKeys { it != 0u }

        // Validate deserialized image metadata
        val validatedImageMeta = result.imageMetadata.filter { meta ->
            if (meta.imageId == 0u || meta.imageName.isBlank()) {
                Log.w(TAG, "Image metadata with invalid imageId=${meta.imageId}, skipping")
                false
            } else {
                true
            }
        }

        result.copy(
            editHistories = validatedHistories,
            embeddedAssets = embeddedAssets,
            imageMetadata = validatedImageMeta
        )
    }

    // ── Package Versioning ──

    suspend fun migratePackage(
        filePath: String,
        targetVersion: Int = Project.CURRENT_PACKAGE_VERSION
    ): PackageMigration = withContext(Dispatchers.IO) {
        val pkg = deserializeProject(filePath)
        val fromVersion = pkg.version

        if (fromVersion >= targetVersion) {
            return@withContext PackageMigration(fromVersion, fromVersion, Instant.now())
        }

        var currentPkg = pkg
        for (version in fromVersion until targetVersion) {
            currentPkg = applyMigration(currentPkg, version, version + 1)
        }

        val migrated = currentPkg.copy(version = targetVersion)
        writePackageToFile(migrated, filePath)

        PackageMigration(
            fromVersion = fromVersion,
            toVersion = targetVersion,
            appliedAt = Instant.now()
        )
    }

    private fun applyMigration(pkg: ProjectPackage, fromVersion: Int, toVersion: Int): ProjectPackage {
        Log.i(TAG, "Migrating package from v$fromVersion to v$toVersion")

        return when (fromVersion) {
            1 -> when (toVersion) {
                2 -> migrateV1ToV2(pkg)
                else -> pkg
            }
            else -> pkg
        }
    }

    private fun migrateV1ToV2(pkg: ProjectPackage): ProjectPackage {
        // Future migration: add new fields, restructure data, etc.
        return pkg.copy(version = 2)
    }

    suspend fun getPackageVersion(filePath: String): Int? {
        return withContext(Dispatchers.IO) {
            try {
                deserializeProject(filePath).version
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get package version", e)
                null
            }
        }
    }

    // ── Asset Embedding ──

    suspend fun embedThumbnail(imageId: UInt, bitmap: Bitmap): EmbeddedAsset = withContext(Dispatchers.IO) {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 85, bos)
        EmbeddedAsset(
            assetId = imageId.toString(),
            assetType = AssetType.THUMBNAIL,
            data = bos.toByteArray(),
            mimeType = "image/webp"
        )
    }

    suspend fun embedPreview(imageId: UInt, bitmap: Bitmap): EmbeddedAsset = withContext(Dispatchers.IO) {
        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        EmbeddedAsset(
            assetId = imageId.toString(),
            assetType = AssetType.PREVIEW,
            data = bos.toByteArray(),
            mimeType = "image/jpeg"
        )
    }

    suspend fun extractThumbnail(asset: EmbeddedAsset): Bitmap? = withContext(Dispatchers.IO) {
        if (asset.assetType != AssetType.THUMBNAIL) return@withContext null
        BitmapFactory.decodeByteArray(asset.data, 0, asset.data.size)
    }

    suspend fun extractPreview(asset: EmbeddedAsset): Bitmap? = withContext(Dispatchers.IO) {
        if (asset.assetType != AssetType.PREVIEW) return@withContext null
        BitmapFactory.decodeByteArray(asset.data, 0, asset.data.size)
    }

    // ── Private Helpers ──

    private suspend fun collectSleeveTree(
        rootId: Long,
        sleeveRepository: SleeveRepository
    ): SleeveTreeSnapshot = withContext(Dispatchers.IO) {
        if (rootId == 0L) return@withContext SleeveTreeSnapshot()

        val elements = mutableListOf<SleeveElementSnapshot>()
        collectSleeveElements(rootId, null, sleeveRepository, elements)

        SleeveTreeSnapshot(rootId = rootId, elements = elements)
    }

    private suspend fun collectSleeveElements(
        parentId: Long,
        parentSnapshotId: Long?,
        sleeveRepository: SleeveRepository,
        result: MutableList<SleeveElementSnapshot>
    ) {
        val children = sleeveRepository.getChildren(parentId)
        for (child in children) {
            val snapshot = when (child) {
                is SleeveFile -> SleeveElementSnapshot(
                    elementId = child.elementId,
                    elementName = child.elementName,
                    elementType = "FILE",
                    parentId = parentSnapshotId,
                    imageId = child.imageId,
                    currentVersionId = child.currentVersionId
                )
                is SleeveFolder -> SleeveElementSnapshot(
                    elementId = child.elementId,
                    elementName = child.elementName,
                    elementType = "FOLDER",
                    parentId = parentSnapshotId
                )
                else -> continue
            }
            result.add(snapshot)

            if (child is SleeveFolder) {
                collectSleeveElements(child.elementId, child.elementId, sleeveRepository, result)
            }
        }
    }

    private suspend fun collectEmbeddedAssets(
        images: List<ImageModel>,
        imageRepository: ImageRepository
    ): List<EmbeddedAsset> = withContext(Dispatchers.IO) {
        val assets = mutableListOf<EmbeddedAsset>()
        for (image in images) {
            val thumbnail = imageRepository.getThumbnail(image.imageId)
            if (thumbnail != null) {
                assets.add(embedThumbnail(image.imageId.toUInt(), thumbnail))
            }
        }
        assets
    }

    private fun serializeHistory(history: EditHistory): String {
        // Serialize to a compact JSON representation
        val map = mapOf(
            "historyId" to history.historyId,
            "boundImageId" to history.boundImageId.toString(),
            "addedTime" to history.addedTime.toEpochMilli().toString(),
            "lastModifiedTime" to history.lastModifiedTime.toEpochMilli().toString(),
            "defaultVersionId" to history.defaultVersionId,
            "activeVersionId" to history.activeVersionId,
            "importPipelineParams" to history.importPipelineParams.toString(),
            "activePipelineParams" to (history.activePipelineParams?.toString() ?: ""),
            "versionOrder" to json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(VersionNode.serializer()),
                history.versionOrder
            ),
            "versionStorage" to json.encodeToString(
                kotlinx.serialization.builtins.MapSerializer(
                    serializer<String>(),
                    serializer<String>()
                ),
                history.versionStorage.mapValues { serializeVersion(it.value) }
            )
        )
        return json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                serializer<String>(),
                serializer<String>()
            ), map
        )
    }

    private fun serializeVersion(version: Version): String {
        val map = mapOf(
            "versionId" to version.versionId,
            "displayName" to version.displayName,
            "boundImageId" to version.boundImageId.toString(),
            "addedTime" to version.addedTime.toEpochMilli().toString(),
            "lastModifiedTime" to version.lastModifiedTime.toEpochMilli().toString(),
            "creationNonce" to version.creationNonce.toString(),
            "materializedParams" to (version.materializedParams?.toString() ?: ""),
            "transactions" to json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    serializer<String>()
                ),
                version.transactions.map { serializeTransaction(it) }
            ),
            "cursor" to version.cursor.toString(),
            "versionHash" to version.versionHash
        )
        return json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                serializer<String>(),
                serializer<String>()
            ), map
        )
    }

    private fun serializeTransaction(tx: EditTransaction): String {
        val map = mapOf(
            "transactionId" to tx.transactionId.toString(),
            "operatorType" to tx.operatorType.name,
            "paramsBefore" to tx.paramsBefore.toString(),
            "paramsAfter" to tx.paramsAfter.toString(),
            "timestamp" to tx.timestamp.toEpochMilli().toString()
        )
        return json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                serializer<String>(),
                serializer<String>()
            ), map
        )
    }

    private fun deserializeHistory(data: String): EditHistory {
        val map = json.decodeFromString<Map<String, String>>(data)
        val versionOrder = json.decodeFromString<List<VersionNode>>(map["versionOrder"] ?: "[]")
        val versionStorageData = json.decodeFromString<Map<String, String>>(map["versionStorage"] ?: "{}")
        val versionStorage = versionStorageData.mapValues { deserializeVersion(it.value) }.toMutableMap()

        return EditHistory(
            historyId = map["historyId"] ?: generateHash128(),
            boundImageId = (map["boundImageId"]?.toUIntOrNull() ?: 0u),
            addedTime = map["addedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            lastModifiedTime = map["lastModifiedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            defaultVersionId = map["defaultVersionId"] ?: "",
            activeVersionId = map["activeVersionId"] ?: "",
            importPipelineParams = map["importPipelineParams"]?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap()),
            activePipelineParams = map["activePipelineParams"]?.takeIf { it.isNotEmpty() }?.let { JsonObject(emptyMap()) },
            versionOrder = versionOrder.toMutableList(),
            versionStorage = versionStorage
        )
    }

    private fun deserializeVersion(data: String): Version {
        val map = json.decodeFromString<Map<String, String>>(data)
        val transactions = try {
            val txList = json.decodeFromString<List<String>>(map["transactions"] ?: "[]")
            txList.map { deserializeTransaction(it) }
        } catch (_: Exception) {
            emptyList()
        }

        // Fallback for version structure changes: creationNonce may have been
        // stored as an Int in older package versions, so try both ULong and
        // Int parsing before falling back to 0.
        val creationNonce = map["creationNonce"]?.toULongOrNull()
            ?: map["creationNonce"]?.toIntOrNull()?.toULong()
            ?: 0u

        return Version(
            versionId = map["versionId"] ?: generateHash128(),
            displayName = map["displayName"] ?: "",
            boundImageId = (map["boundImageId"]?.toUIntOrNull() ?: 0u),
            addedTime = map["addedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            lastModifiedTime = map["lastModifiedTime"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            creationNonce = creationNonce,
            materializedParams = map["materializedParams"]?.takeIf { it.isNotEmpty() }?.let { JsonObject(emptyMap()) },
            transactions = transactions.toMutableList(),
            cursor = map["cursor"]?.toIntOrNull() ?: 0,
            versionHash = map["versionHash"] ?: ""
        )
    }

    private fun deserializeTransaction(data: String): EditTransaction {
        val map = json.decodeFromString<Map<String, String>>(data)
        return EditTransaction(
            transactionId = map["transactionId"]?.toUIntOrNull() ?: 0u,
            operatorType = try {
                OperatorType.valueOf(map["operatorType"] ?: "EXPOSURE")
            } catch (_: Exception) {
                OperatorType.EXPOSURE
            },
            paramsBefore = map["paramsBefore"]?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap()),
            paramsAfter = map["paramsAfter"]?.let { JsonObject(emptyMap()) } ?: JsonObject(emptyMap()),
            timestamp = map["timestamp"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
        )
    }

    // ── Compression ──

    private fun compressString(input: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzos ->
            gzos.write(input.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }

    private fun decompressString(input: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(input)).use { gzis ->
            gzis.bufferedReader().readText()
        }
    }

    private suspend fun <T> runInIO(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}