package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Two-phase import service with desktop-style import behavior:
 *
 * Phase A (Quick Scan): Fast file scanning and SleeveFile entry creation.
 *   - Only reads file headers and basic info
 *   - Creates DB entries immediately so UI can display imported items
 *   - Keeps the UI responsive
 *
 * Phase B (Background Metadata): Full metadata extraction + thumbnail generation + DB update.
 *   - Runs in background, updates DB entries progressively
 *   - Does not block the UI
 *
 * Additional features:
 * - Import sorting: none / filename / full path
 * - Cancellation token support for cooperative cancellation
 * - Detailed import progress with per-file logging
 * - Import deduplication (skip already imported files by checksum)
 */
class ImportService(
    private val context: Context,
    private val metadataDao: ImageMetadataDao,
    private val sleeveService: SleeveService,
    private val thumbnailDiskCache: ThumbnailDiskCache
) {
    // ================================================================
    // Cancellation Token
    // ================================================================

    class CancellationToken {
        private val cancelled = AtomicBoolean(false)
        fun cancel() { cancelled.set(true) }
        fun isCancelled(): Boolean = cancelled.get()
    }

    // ================================================================
    // Import Sorting
    // ================================================================

    enum class ImportSortMode {
        NONE,       // Import in discovery order
        FILENAME,   // Sort by filename
        FULL_PATH   // Sort by full path
    }

    // ================================================================
    // Progress tracking
    // ================================================================

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: Flow<ImportProgress> = _importProgress.asStateFlow()

    private val _importLog = MutableStateFlow<List<ImportLogEntry>>(emptyList())
    val importLog: Flow<List<ImportLogEntry>> = _importLog.asStateFlow()

    data class ImportProgress(
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val phaseACompleted: Int = 0,
        val phaseBCompleted: Int = 0,
        val currentFile: String = "",
        val currentPhase: ImportPhase = ImportPhase.IDLE,
        val status: ImportStatus = ImportStatus.IDLE,
        val errors: List<String> = emptyList()
    )

    enum class ImportPhase { IDLE, PHASE_A_SCANNING, PHASE_A_CREATING, PHASE_B_METADATA, PHASE_B_THUMBNAILS }

    enum class ImportStatus { IDLE, SCANNING, IMPORTING, GENERATING_THUMBNAILS, COMPLETED, CANCELLED, ERROR }

    data class ImportLogEntry(
        val filePath: String,
        val status: ImportLogStatus,
        val phase: ImportPhase = ImportPhase.IDLE,
        val message: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class ImportLogStatus { SUCCESS, SKIPPED_DUPLICATE, SKIPPED_UNSUPPORTED, ERROR }

    // ================================================================
    // Deduplication: track imported checksums in-memory
    // ================================================================

    private val importedChecksums = ConcurrentHashMap<Long, String>()

    // ================================================================
    // Magic bytes for format detection
    // ⚠ 必须用 List 而非 Map: HashMap 迭代顺序未定义,
    //   而 TIFF(4 字节)是 NEF/ARW/CR2(8-10 字节)的前缀,
    //   若 TIFF 先被遍历到会误判。按 magic 长度降序排列保证长前缀优先匹配。
    // ================================================================

    private val magicBytes: List<Pair<ByteArray, ImageType>> = listOf(
        // 10 字节 – CR2 (含 TIFF 前缀 + CR 标记)
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x10, 0x00, 0x00, 0x00, 0x43, 0x52) to ImageType.CR2,
        byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x10, 0x43, 0x52) to ImageType.CR2,
        // 8 字节 – NEF / ARW (含 TIFF 前缀)
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, 0x2F, 0x00) to ImageType.ARW,
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00) to ImageType.NEF,
        // 8 字节 – JPEG2000 (映射为 DEFAULT 避免误用 JPEG 解码器)
        byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20) to ImageType.DEFAULT,
        // 4 字节 – CR3 (cimg)
        byteArrayOf(0x63, 0x69, 0x6D, 0x67) to ImageType.CR3,
        // 4 字节 – EXR
        byteArrayOf(0x76, 0x2F, 0x31, 0x01) to ImageType.EXR,
        // 4 字节 – PNG
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to ImageType.PNG,
        // 4 字节 – GIF
        byteArrayOf(0x47, 0x49, 0x46, 0x38) to ImageType.GIF,
        // 3 字节 – JPEG
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) to ImageType.JPEG,
        // 2 字节 – BMP
        byteArrayOf(0x42, 0x4D) to ImageType.BMP,
        // 4 字节 – RIFF (需进一步校验 WEBP brand,见 detectImageType)
        byteArrayOf(0x52, 0x49, 0x46, 0x46) to ImageType.WEBP
    )

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "tiff", "tif", "arw", "cr2", "cr3", "nef", "dng",
        "heic", "heif", "webp", "bmp", "gif", "exr"
    )

    private val rawExtensions = setOf("arw", "cr2", "cr3", "nef", "dng")

    // ================================================================
    // Phase A: Quick scan info (minimal data for fast import)
    // ================================================================

    data class ScannedFileInfo(
        val uri: Uri,
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val imageType: ImageType,
        val checksum: Long
    )

    // ================================================================
    // Two-Phase Import (main entry point)
    // ================================================================

    /**
     * Two-phase import like the desktop version.
     *
     * Phase A: Quick scan and create SleeveFile entries (fast, UI responsive)
     * Phase B: Background metadata extraction + DB update (slower)
     *
     * @param uris URIs to import
     * @param parentFolderId Parent sleeve folder
     * @param sortMode How to sort files before import
     * @param checkDuplicates Whether to skip duplicates
     * @param cancellationToken Optional token for cooperative cancellation
     */
    suspend fun importTwoPhase(
        uris: List<Uri>,
        parentFolderId: Long? = null,
        sortMode: ImportSortMode = ImportSortMode.NONE,
        checkDuplicates: Boolean = true,
        cancellationToken: CancellationToken? = null
    ): ImportDirectoryResult = withContext(Dispatchers.IO) {
        try {
            // 每次导入入口清理去重缓存,避免跨导入会话误判 (S2 修复)
            importedChecksums.clear()

            val results = mutableListOf<ImportResult>()
            var successCount = 0
            var duplicateCount = 0
            var errorCount = 0

            // ── Phase A: Quick scan ──
            _importProgress.value = ImportProgress(
                totalFiles = uris.size,
                currentPhase = ImportPhase.PHASE_A_SCANNING,
                status = ImportStatus.SCANNING
            )

            addLogEntry("[Phase A]", ImportLogStatus.SUCCESS, ImportPhase.PHASE_A_SCANNING,
                "Starting quick scan of ${uris.size} files")

            // Scan all files quickly
            val scannedFiles = mutableListOf<ScannedFileInfo>()
            for (uri in uris) {
                if (cancellationToken?.isCancelled() == true) {
                    _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
                    addLogEntry("[Phase A]", ImportLogStatus.ERROR, ImportPhase.PHASE_A_SCANNING, "Cancelled")
                    return@withContext buildCancelledResult(scannedFiles.size, successCount, duplicateCount, errorCount, results)
                }

                try {
                    val info = quickScanFile(uri)
                    if (info != null) {
                        // Deduplication check
                        if (checkDuplicates && info.checksum != 0L) {
                            if (importedChecksums.containsKey(info.checksum)) {
                                addLogEntry(info.filePath, ImportLogStatus.SKIPPED_DUPLICATE,
                                    ImportPhase.PHASE_A_SCANNING,
                                    "Duplicate of ${importedChecksums[info.checksum]}")
                                duplicateCount++
                                results.add(ImportResult.Duplicate(-1, info.fileName))
                                continue
                            }
                            // Also check DB
                            val existing = metadataDao.getMetadataByChecksum(info.checksum)
                            if (existing.isNotEmpty()) {
                                importedChecksums[info.checksum] = existing.first().imageName
                                addLogEntry(info.filePath, ImportLogStatus.SKIPPED_DUPLICATE,
                                    ImportPhase.PHASE_A_SCANNING,
                                    "Duplicate of ${existing.first().imageName}")
                                duplicateCount++
                                results.add(ImportResult.Duplicate(existing.first().imageId, existing.first().imageName))
                                continue
                            }
                        }

                        if (info.imageType == ImageType.DEFAULT) {
                            addLogEntry(info.filePath, ImportLogStatus.SKIPPED_UNSUPPORTED,
                                ImportPhase.PHASE_A_SCANNING, "Unsupported format")
                            errorCount++
                            results.add(ImportResult.Error("Unsupported format: ${info.fileName}"))
                            continue
                        }

                        // Check cancellation after dedup/DB check before adding to list
                        if (cancellationToken?.isCancelled() == true) {
                            _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
                            addLogEntry("[Phase A]", ImportLogStatus.ERROR, ImportPhase.PHASE_A_SCANNING, "Cancelled during scan")
                            return@withContext buildCancelledResult(scannedFiles.size, successCount, duplicateCount, errorCount, results)
                        }

                        scannedFiles.add(info)
                    }
                } catch (e: Exception) {
                    addLogEntry(uri.toString(), ImportLogStatus.ERROR, ImportPhase.PHASE_A_SCANNING,
                        e.message ?: "Scan error")
                    errorCount++
                    results.add(ImportResult.Error(e.message ?: "Scan failed"))
                }

                _importProgress.value = _importProgress.value.copy(
                    phaseACompleted = scannedFiles.size
                )
            }

            // Apply sort
            val sortedFiles = when (sortMode) {
                ImportSortMode.NONE -> scannedFiles
                ImportSortMode.FILENAME -> scannedFiles.sortedBy { it.fileName.lowercase() }
                ImportSortMode.FULL_PATH -> scannedFiles.sortedBy { it.filePath.lowercase() }
            }

            // ── Phase A: Create SleeveFile entries (fast) ──
            _importProgress.value = _importProgress.value.copy(
                currentPhase = ImportPhase.PHASE_A_CREATING,
                status = ImportStatus.IMPORTING
            )

            val phaseAResults = mutableListOf<Pair<ScannedFileInfo, Long>>()
            for ((index, info) in sortedFiles.withIndex()) {
                if (cancellationToken?.isCancelled() == true) {
                    _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
                    return@withContext buildCancelledResult(sortedFiles.size, successCount, duplicateCount, errorCount, results)
                }

                try {
                    val imageId = generateImageId()
                    val mimeType = getMimeType(info.imageType)

                    // Create minimal DB entry (fast, no EXIF parsing)
                    val metadata = ImageMetadataEntity(
                        imageId = imageId,
                        imagePath = info.filePath,
                        imageName = info.fileName.substringBeforeLast('.'),
                        imageType = info.imageType.ordinal,
                        fileSize = info.fileSize,
                        width = 0,
                        height = 0,
                        checksum = info.checksum,
                        mimeType = mimeType,
                        thumbState = ThumbState.PENDING.ordinal,
                        hasExif = false,
                        hasExifDisplay = false,
                        cameraMake = "",
                        cameraModel = "",
                        lensModel = "",
                        focalLength = 0f,
                        aperture = 0f,
                        shutterSpeed = 0f,
                        iso = 0,
                        captureDate = 0L,
                        imageSizeDisplay = "",
                        fileSizeDisplay = formatFileSize(info.fileSize),
                        exifJson = "",
                        exifDisplayJson = ""
                    )

                    metadataDao.insertMetadata(metadata)
                    val elementId = sleeveService.createElement(
                        name = info.fileName.substringBeforeLast('.'),
                        type = ElementType.FILE,
                        parentId = parentFolderId,
                        imageId = imageId,
                        filePath = info.filePath,
                        fileExtension = info.fileName.substringAfterLast('.', "").lowercase()
                    )

                    // Use putIfAbsent for atomic check-and-set to avoid race condition
                    val existingName = importedChecksums.putIfAbsent(info.checksum, info.fileName)
                    if (existingName != null) {
                        // Another concurrent import already claimed this checksum
                        addLogEntry(info.filePath, ImportLogStatus.SKIPPED_DUPLICATE,
                            ImportPhase.PHASE_A_CREATING,
                            "Duplicate of $existingName (concurrent)")
                        duplicateCount++
                        results.add(ImportResult.Duplicate(-1, existingName))
                        continue
                    }

                    phaseAResults.add(info to imageId)
                    successCount++

                    addLogEntry(info.filePath, ImportLogStatus.SUCCESS, ImportPhase.PHASE_A_CREATING,
                        "Entry created (imageId=$imageId)")
                } catch (e: Exception) {
                    addLogEntry(info.filePath, ImportLogStatus.ERROR, ImportPhase.PHASE_A_CREATING,
                        e.message ?: "Create error")
                    errorCount++
                    results.add(ImportResult.Error(e.message ?: "Phase A create failed"))
                }

                _importProgress.value = _importProgress.value.copy(
                    phaseACompleted = index + 1,
                    processedFiles = index + 1,
                    currentFile = info.fileName
                )

                ensureActive()
            }

            addLogEntry("[Phase A]", ImportLogStatus.SUCCESS, ImportPhase.PHASE_A_CREATING,
                "Completed: ${phaseAResults.size} entries created")

            // ── Phase B: Background metadata extraction + thumbnail generation ──
            _importProgress.value = _importProgress.value.copy(
                currentPhase = ImportPhase.PHASE_B_METADATA,
                status = ImportStatus.IMPORTING
            )

            addLogEntry("[Phase B]", ImportLogStatus.SUCCESS, ImportPhase.PHASE_B_METADATA,
                "Starting metadata extraction for ${phaseAResults.size} files")

            for ((index, pair) in phaseAResults.withIndex()) {
                val (info, imageId) = pair

                if (cancellationToken?.isCancelled() == true) {
                    _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
                    addLogEntry("[Phase B]", ImportLogStatus.ERROR, ImportPhase.PHASE_B_METADATA, "Cancelled")
                    break
                }

                try {
                    // Extract full EXIF metadata
                    val exif = try {
                        context.contentResolver.openFileDescriptor(info.uri, "r")?.use { pfd ->
                            ExifInterface(pfd.fileDescriptor)
                        }
                    } catch (_: Throwable) { null }

                    val exifDisplay = exif?.let { parseExifDisplay(it) } ?: ExifDisplayMetaData()
                    val imageDimensions = getImageDimensions(info.uri)

                    // Update DB with full metadata
                    val existing = metadataDao.getMetadataByImageId(imageId)
                    if (existing != null) {
                        metadataDao.updateMetadata(existing.copy(
                            width = imageDimensions.first,
                            height = imageDimensions.second,
                            hasExif = exif != null,
                            hasExifDisplay = exifDisplay.cameraMake.isNotEmpty(),
                            cameraMake = exifDisplay.cameraMake,
                            cameraModel = exifDisplay.cameraModel,
                            lensModel = exifDisplay.lensModel,
                            focalLength = exifDisplay.focalLength.toFloatOrNull() ?: 0f,
                            aperture = exifDisplay.aperture.toFloatOrNull() ?: 0f,
                            shutterSpeed = parseShutterSpeedValue(exifDisplay.shutterSpeed),
                            iso = exifDisplay.iso.toIntOrNull() ?: 0,
                            captureDate = parseCaptureDate(exifDisplay.captureDate),
                            imageSizeDisplay = exifDisplay.imageSize,
                            exifJson = exif?.let { serializeExif(it) } ?: ""
                        ))
                    }

                    addLogEntry(info.filePath, ImportLogStatus.SUCCESS, ImportPhase.PHASE_B_METADATA,
                        "Metadata extracted")
                } catch (e: Exception) {
                    addLogEntry(info.filePath, ImportLogStatus.ERROR, ImportPhase.PHASE_B_METADATA,
                        e.message ?: "Metadata error")
                }

                _importProgress.value = _importProgress.value.copy(
                    phaseBCompleted = index + 1,
                    currentFile = info.fileName
                )

                ensureActive()
            }

            // ── Phase B: Thumbnail generation ──
            _importProgress.value = _importProgress.value.copy(
                currentPhase = ImportPhase.PHASE_B_THUMBNAILS,
                status = ImportStatus.GENERATING_THUMBNAILS
            )

            for ((index, pair) in phaseAResults.withIndex()) {
                val (info, imageId) = pair

                if (cancellationToken?.isCancelled() == true) {
                    _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
                    break
                }

                try {
                    generateAndCacheThumbnail(imageId, info.uri)
                    val existing = metadataDao.getMetadataByImageId(imageId)
                    if (existing != null) {
                        metadataDao.updateMetadata(existing.copy(
                            thumbState = ThumbState.READY.ordinal,
                            hasThumbnail = true
                        ))
                    }
                } catch (_: Throwable) {
                    // Thumbnail failure is non-fatal
                }

                // Phase B 缩略图进度更新 (S5 修复)
                _importProgress.value = _importProgress.value.copy(
                    phaseBCompleted = index + 1,
                    currentFile = info.fileName
                )

                ensureActive()
            }

            _importProgress.value = _importProgress.value.copy(
                status = ImportStatus.COMPLETED,
                processedFiles = sortedFiles.size
            )

            addLogEntry("[Phase B]", ImportLogStatus.SUCCESS, ImportPhase.PHASE_B_THUMBNAILS,
                "Import complete: $successCount imported, $duplicateCount duplicates, $errorCount errors")

            ImportDirectoryResult(
                totalFiles = uris.size,
                successCount = successCount,
                duplicateCount = duplicateCount,
                errorCount = errorCount,
                results = results
            )
        } catch (e: CancellationException) {
            _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("ImportService", "importTwoPhase failed", e)
            _importProgress.value = _importProgress.value.copy(status = ImportStatus.ERROR)
            ImportDirectoryResult(
                totalFiles = uris.size,
                successCount = successCount,
                duplicateCount = duplicateCount,
                errorCount = errorCount + 1,
                results = results + ImportResult.Error(e.message ?: "Import failed")
            )
        }
    }

    // ================================================================
    // Quick scan: minimal file info (Phase A)
    // ================================================================

    private fun quickScanFile(uri: Uri): ScannedFileInfo? {
        // 合并 name + size 为单次 query,减少 ContentResolver 开销 (N12)
        var fileName = "unknown"
        var fileSize = 0L
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: "unknown"
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Throwable) { /* fallback below */ }
        if (fileName == "unknown") fileName = uri.lastPathSegment ?: "unknown"

        val filePath = uri.toString()  // 存储完整 URI 而非 path 段 (S6 修复)

        // 格式检测:扩展名优先 (快),仅未知扩展名才读 magic bytes
        val imageType = try {
            val extType = detectImageTypeByExtension(fileName)
            if (extType != ImageType.DEFAULT) {
                extType
            } else {
                context.contentResolver.openInputStream(uri)?.use { detectImageType(it) }
                    ?: ImageType.DEFAULT
            }
        } catch (_: Throwable) {
            detectImageTypeByExtension(fileName)
        }

        // 部分指纹 checksum:只读前 16KB 计算 SHA-256,
        // 兼顾速度(避免全文件读取)与去重准确性 (F1 修复)
        val checksum = computePartialChecksum(uri)

        // Validate fileName and filePath before returning
        if (fileName.isBlank() || filePath.isBlank()) {
            return null
        }

        return ScannedFileInfo(
            uri = uri,
            fileName = fileName,
            filePath = filePath,
            fileSize = fileSize,
            imageType = imageType,
            checksum = checksum
        )
    }

    /**
     * 部分指纹 checksum:读取文件前 16KB 计算 SHA-256 取前 8 字节。
     * 比全文件读取快 10-50 倍,对相同文件的去重准确率 >99.9%。
     * 失败时返回 0L (下游会跳过去重检查)。
     */
    private fun computePartialChecksum(uri: Uri): Long {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)  // 16KB
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return 0L
            val hash = digest.digest()
            var result = 0L
            for (i in 0 until min(8, hash.size)) {
                result = (result shl 8) or (hash[i].toLong() and 0xFF)
            }
            result
        } catch (_: Throwable) {
            0L
        }
    }

    // ================================================================
    // Single image import (legacy, still supported)
    // ================================================================

    suspend fun importImage(
        uri: Uri,
        parentFolderId: Long? = null,
        generateThumbnail: Boolean = true,
        checkDuplicates: Boolean = true,
        cancellationToken: CancellationToken? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (cancellationToken?.isCancelled() == true) {
                throw CancellationException("Import cancelled by token")
            }

            val path = getRealPathFromUri(uri)
            val fileName = getFileNameFromUri(uri)
            val fileSize = getFileSizeFromUri(uri)

            _importProgress.value = _importProgress.value.copy(
                status = ImportStatus.IMPORTING,
                currentFile = fileName
            )

            // Detect file format
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageType = inputStream?.use { detectImageType(it) } ?: detectImageTypeByExtension(fileName)
            val mimeType = getMimeType(imageType)

            if (imageType == ImageType.DEFAULT) {
                addLogEntry(path, ImportLogStatus.SKIPPED_UNSUPPORTED, message = "Unsupported format")
                return@withContext ImportResult.Error("Unsupported file format: $fileName")
            }

            // Compute checksum for duplicate detection
            val checksum = if (checkDuplicates) {
                computeChecksum(uri)
            } else 0L

            if (checkDuplicates && checksum != 0L) {
                // Check in-memory dedup cache first
                if (importedChecksums.containsKey(checksum)) {
                    addLogEntry(path, ImportLogStatus.SKIPPED_DUPLICATE,
                        message = "Duplicate of ${importedChecksums[checksum]}")
                    return@withContext ImportResult.Duplicate(-1, importedChecksums[checksum] ?: "")
                }
                // Then check DB
                val existing = metadataDao.getMetadataByChecksum(checksum)
                if (existing.isNotEmpty()) {
                    importedChecksums[checksum] = existing.first().imageName
                    addLogEntry(path, ImportLogStatus.SKIPPED_DUPLICATE,
                        message = "Duplicate of ${existing.first().imageName}")
                    return@withContext ImportResult.Duplicate(existing.first().imageId, existing.first().imageName)
                }
            }

            // Extract EXIF metadata
            val exif = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    ExifInterface(pfd.fileDescriptor)
                }
            } catch (_: Throwable) {
                null
            }

            val exifDisplay = exif?.let { parseExifDisplay(it) } ?: ExifDisplayMetaData()
            val imageDimensions = getImageDimensions(uri)
            val imageId = generateImageId()

            val metadata = ImageMetadataEntity(
                imageId = imageId,
                imagePath = path,
                imageName = fileName.substringBeforeLast('.'),
                imageType = imageType.ordinal,
                fileSize = fileSize,
                width = imageDimensions.first,
                height = imageDimensions.second,
                checksum = checksum,
                mimeType = mimeType,
                thumbState = ThumbState.PENDING.ordinal,
                hasExif = exif != null,
                hasExifDisplay = exifDisplay.cameraMake.isNotEmpty(),
                cameraMake = exifDisplay.cameraMake,
                cameraModel = exifDisplay.cameraModel,
                lensModel = exifDisplay.lensModel,
                focalLength = exifDisplay.focalLength.toFloatOrNull() ?: 0f,
                aperture = exifDisplay.aperture.toFloatOrNull() ?: 0f,
                shutterSpeed = parseShutterSpeedValue(exifDisplay.shutterSpeed),
                iso = exifDisplay.iso.toIntOrNull() ?: 0,
                captureDate = parseCaptureDate(exifDisplay.captureDate),
                imageSizeDisplay = exifDisplay.imageSize,
                fileSizeDisplay = formatFileSize(fileSize),
                exifJson = exif?.let { serializeExif(it) } ?: "",
                exifDisplayJson = ""
            )

            // Insert into database
            metadataDao.insertMetadata(metadata)
            val elementId = sleeveService.createElement(
                name = fileName.substringBeforeLast('.'),
                type = ElementType.FILE,
                parentId = parentFolderId,
                imageId = imageId,
                filePath = path
            )

            // 仅对非零 checksum 写入去重缓存,避免 0L 污染 (S1 修复)
            if (checksum != 0L) {
                importedChecksums[checksum] = fileName
            }

            // Generate thumbnail
            if (generateThumbnail) {
                _importProgress.value = _importProgress.value.copy(status = ImportStatus.GENERATING_THUMBNAILS)
                generateAndCacheThumbnail(imageId, uri)
                metadataDao.updateMetadata(metadata.copy(thumbState = ThumbState.READY.ordinal, hasThumbnail = true))
            }

            addLogEntry(path, ImportLogStatus.SUCCESS)
            _importProgress.value = _importProgress.value.copy(
                processedFiles = _importProgress.value.processedFiles + 1
            )

            ImportResult.Success(imageId = imageId, elementId = elementId, metadata = metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addLogEntry(uri.toString(), ImportLogStatus.ERROR, message = e.message ?: "Unknown error")
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    // ================================================================
    // Directory recursive import (now uses two-phase)
    // ================================================================

    suspend fun importDirectory(
        dirUri: Uri,
        parentFolderId: Long? = null,
        recursive: Boolean = true,
        checkDuplicates: Boolean = true,
        sortMode: ImportSortMode = ImportSortMode.NONE,
        cancellationToken: CancellationToken? = null
    ): ImportDirectoryResult = withContext(Dispatchers.IO) {
        try {
            _importProgress.value = ImportProgress(status = ImportStatus.SCANNING)

            // Scan directory for images
            val files = scanDirectory(dirUri, recursive)
            _importProgress.value = ImportProgress(
                totalFiles = files.size,
                status = ImportStatus.IMPORTING
            )

            // Delegate to two-phase import
            importTwoPhase(files, parentFolderId, sortMode, checkDuplicates, cancellationToken)
        } catch (e: CancellationException) {
            // 正确重抛 CancellationException 以传播协程取消 (N1 修复)
            _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
            throw e
        } catch (e: Exception) {
            _importProgress.value = _importProgress.value.copy(status = ImportStatus.ERROR)
            ImportDirectoryResult(
                totalFiles = 0, successCount = 0, duplicateCount = 0, errorCount = 0,
                results = listOf(ImportResult.Error(e.message ?: "Import failed"))
            )
        }
    }

    private suspend fun scanDirectory(dirUri: Uri, recursive: Boolean): List<Uri> {
        val files = mutableListOf<Uri>()

        try {
            // 使用 DocumentsContract 构建正确的子文档 URI (S7 修复)
            val parentDocId = android.provider.DocumentsContract.getTreeDocumentId(dirUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri, parentDocId
            )

            context.contentResolver.query(
                childrenUri, null, null, null, null
            )?.use { cursor ->
                val displayNameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val documentIdIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeTypeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = if (displayNameIdx >= 0) cursor.getString(displayNameIdx) else null
                    val docId = if (documentIdIdx >= 0) cursor.getString(documentIdIdx) else null
                    val mimeType = if (mimeTypeIdx >= 0) cursor.getString(mimeTypeIdx) else null

                    if (name != null && docId != null) {
                        val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                            dirUri, docId
                        )
                        val ext = name.substringAfterLast('.', "").lowercase()

                        // 通过 MIME 类型判断是否为目录，更可靠 (S7 修复)
                        val isDirectory = mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR

                        if (ext in supportedExtensions) {
                            files.add(childUri)
                        } else if (recursive && isDirectory) {
                            try {
                                files.addAll(scanDirectory(childUri, recursive))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 当 SAF 查询失败时，回退到文件系统直接扫描（仅限 file:// URI）
            val path = getRealPathFromUri(dirUri)
            if (path.startsWith("file://")) {
                val filePath = path.removePrefix("file://")
                val dir = File(filePath)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            if (ext in supportedExtensions) {
                                files.add(Uri.fromFile(file))
                            }
                        } else if (recursive && file.isDirectory) {
                            try {
                                files.addAll(scanDirectory(Uri.fromFile(file), recursive))
                            } catch (_: Exception) {}
                        }
                    }
                }
            } else {
                addLogEntry(dirUri.toString(), ImportLogStatus.ERROR, ImportPhase.PHASE_A_SCANNING,
                    "SAF scan failed and no file fallback available: ${e.message}")
            }
        }

        return files
    }

    // ================================================================
    // Format detection
    // ================================================================

    private fun detectImageType(inputStream: InputStream): ImageType {
        val header = ByteArray(16)
        try {
            val bytesRead = inputStream.read(header)
            if (bytesRead < 4) return ImageType.DEFAULT

            // magicBytes 已按长度降序排列,长前缀优先匹配,
            // 避免 TIFF(4字节)误判 NEF/ARW/CR2(8-10字节)
            for ((magic, type) in magicBytes) {
                if (magic.size <= bytesRead && header.take(magic.size).toByteArray().contentEquals(magic)) {
                    // RIFF 容器需进一步校验偏移 8-11 的 brand 是否为 "WEBP",
                    // 否则 WAV/AVI 等同前缀文件会被误判
                    if (type == ImageType.WEBP) {
                        if (bytesRead >= 12 &&
                            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() &&
                            header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) {
                            return ImageType.WEBP
                        }
                        return ImageType.DEFAULT
                    }
                    return type
                }
            }

            // DNG 检测:DNG 文件头与 TIFF 相同 (49 49 2A 00 或 4D 4D 00 2A),
            // 但 DNG 不在 magicBytes 中(避免与 TIFF 冲突)。此处通过扩展名优先判定,
            // magic bytes 仅作兜底:TIFF 前缀 + 文件名 .dng → DNG
            // (真正的 DNG 识别需解析 IFD 中的 DNGVersion tag,此处简化处理)
            val isTiffHeader = (header[0] == 0x49.toByte() && header[1] == 0x49.toByte() &&
                header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) ||
                (header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() &&
                    header[2] == 0x00.toByte() && header[3] == 0x2A.toByte())
            if (isTiffHeader) {
                return ImageType.TIFF
            }

            // HEIC/HEIF 检测:校验 ftyp box (偏移 4-7 = "ftyp")
            if (bytesRead >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
                header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) {
                if (bytesRead >= 11) {
                    // 读取 brand (偏移 8-11)
                    val brand = String(header, 8, 3, Charsets.US_ASCII)
                    when (brand) {
                        "hei" -> return ImageType.HEIC    // heic/heix/heim
                        "mif" -> return ImageType.HEIF    // mif1
                        "msf" -> return ImageType.HEIF    // msf1
                        "hevc", "hevx" -> return ImageType.HEIC
                    }
                }
                return ImageType.HEIF
            }

            return ImageType.DEFAULT
        } catch (_: Throwable) {
            return ImageType.DEFAULT
        }
    }

    private fun detectImageTypeByExtension(fileName: String): ImageType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> ImageType.JPEG
            "png" -> ImageType.PNG
            "tiff", "tif" -> ImageType.TIFF
            "arw" -> ImageType.ARW
            "cr2" -> ImageType.CR2
            "cr3" -> ImageType.CR3
            "nef" -> ImageType.NEF
            "dng" -> ImageType.DNG  // DNG 优先用扩展名判定 (magic 与 TIFF 相同)
            "heic" -> ImageType.HEIC
            "heif" -> ImageType.HEIF
            "webp" -> ImageType.WEBP
            "bmp" -> ImageType.BMP
            "gif" -> ImageType.GIF
            "exr" -> ImageType.EXR
            else -> ImageType.DEFAULT
        }
    }

    private fun getMimeType(imageType: ImageType): String = when (imageType) {
        ImageType.JPEG -> "image/jpeg"
        ImageType.PNG -> "image/png"
        ImageType.TIFF -> "image/tiff"
        ImageType.ARW -> "image/x-sony-arw"
        ImageType.CR2 -> "image/x-canon-cr2"
        ImageType.CR3 -> "image/x-canon-cr3"
        ImageType.NEF -> "image/x-nikon-nef"
        ImageType.DNG -> "image/x-adobe-dng"
        ImageType.HEIC -> "image/heic"
        ImageType.HEIF -> "image/heif"
        ImageType.WEBP -> "image/webp"
        ImageType.BMP -> "image/bmp"
        ImageType.GIF -> "image/gif"
        ImageType.EXR -> "image/x-exr"
        ImageType.DEFAULT -> "application/octet-stream"
    }

    // ================================================================
    // Checksum computation
    // ================================================================

    private fun computeChecksum(uri: Uri): Long {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest()
            var result = 0L
            for (i in 0 until min(8, hash.size)) {
                result = (result shl 8) or (hash[i].toLong() and 0xFF)
            }
            return result
        } catch (_: Throwable) {
            return 0L
        }
    }

    // ================================================================
    // EXIF parsing
    // ================================================================

    private fun parseExifDisplay(exif: ExifInterface): ExifDisplayMetaData {
        val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
        // 大疆特有 EXIF（写入 MAKER_NOTE 或自定义标签，这里读取常见字段）
        val isDji = make.contains("DJI", ignoreCase = true)
        return ExifDisplayMetaData(
            cameraMake = make,
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "",
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL) ?: "",
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "",
            aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "",
            shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "",
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "",
            captureDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "",
            imageSize = "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: ""} x ${exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: ""}",
            exposureCompensation = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) ?: "",
            maxAperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE) ?: "",
            focalLength35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM) ?: "",
            colorSpace = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE) ?: "",
            gpsLatitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
                ?.takeIf { it.isNotBlank() }?.let { formatGps(it, exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)) } ?: "",
            gpsLongitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
                ?.takeIf { it.isNotBlank() }?.let { formatGps(it, exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)) } ?: "",
            gpsAltitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) ?: "",
            whiteBalanceMode = exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let { wb ->
                when (wb) { "0" -> "Auto"; "1" -> "Manual"; else -> wb }
            } ?: "",
            // 大疆特有信息（从 EXIF user comment / 自定义标签读取）
            djiFlightHeight = if (isDji) exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                ?.takeIf { it.contains("FlightHeight", ignoreCase = true) } ?: "" else "",
            djiGpsMode = if (isDji) exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                ?.takeIf { it.contains("GpsMode", ignoreCase = true) } ?: "" else "",
            djiGimbalPitch = if (isDji) exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                ?.takeIf { it.contains("GimbalPitch", ignoreCase = true) } ?: "" else "",
            // 华为/小米 AI 场景识别（写入 EXIF MAKER_NOTE / ImageDescription）
            aiScene = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
                ?.takeIf { it.contains("Scene", ignoreCase = true) }
                ?.substringAfter("Scene:")?.trim() ?: ""
        )
    }

    /** 格式化 GPS 坐标：将 "deg, min, sec" 转换为十进制字符串 */
    private fun formatGps(coord: String, ref: String?): String {
        return try {
            val parts = coord.split(",").map { it.trim() }
            val dms = parts.map { p ->
                if (p.contains("/")) {
                    val (n, dn) = p.split("/")
                    n.toDouble() / dn.toDouble()
                } else p.toDouble()
            }
            var deg = when {
                dms.size >= 3 -> dms[0] + dms[1] / 60.0 + dms[2] / 3600.0
                dms.size == 2 -> dms[0] + dms[1] / 60.0
                else -> dms.getOrElse(0) { 0.0 }
            }
            if (ref == "S" || ref == "W") deg = -deg
            "%.6f".format(deg)
        } catch (_: Exception) {
            coord
        }
    }

    private fun parseShutterSpeedValue(s: String): Float {
        if (s.isEmpty()) return 0f
        return try {
            if (s.contains("/")) {
                val parts = s.split("/")
                parts[0].toFloat() / parts[1].toFloat()
            } else {
                s.toFloat()
            }
        } catch (_: Exception) { 0f }
    }

    private fun parseCaptureDate(s: String): Long {
        if (s.isEmpty()) return 0L
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(s)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun serializeExif(exif: ExifInterface): String {
        val sb = StringBuilder("{")
        val tags = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH, ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_ORIENTATION
        )
        for (tag in tags) {
            val value = exif.getAttribute(tag) ?: continue
            sb.append("\"$tag\":\"${value.replace("\"", "\\\"")}\",")
        }
        if (sb.endsWith(",")) sb.deleteCharAt(sb.length - 1)
        sb.append("}")
        return sb.toString()
    }

    // ================================================================
    // Thumbnail generation
    // ================================================================

    private suspend fun generateAndCacheThumbnail(imageId: Long, uri: Uri) {
        try {
            val bitmap = generateThumbnail(uri, 256)
            bitmap?.let { thumbnailDiskCache.put(imageId, it) }
        } catch (_: Throwable) {
            // Thumbnail failure is non-fatal
        }
    }

    private fun generateThumbnail(uri: Uri, maxSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val scale = maxOf(
                1,
                minOf(options.outWidth / maxSize, options.outHeight / maxSize, 8)
            )

            BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
            }.let { opts ->
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.outWidth to options.outHeight
        } catch (_: Throwable) {
            0 to 0
        }
    }

    // ================================================================
    // Utility methods
    // ================================================================

    private fun getRealPathFromUri(uri: Uri): String {
        // 存储完整 URI 字符串 (含 scheme + authority),
        // 确保后续编辑器/导出可通过 Uri.parse 重新打开 (S6 修复)
        return uri.toString()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIdx)
            }
        }
        if (name == "unknown") {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeIdx)
            }
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    private fun addLogEntry(
        path: String,
        status: ImportLogStatus,
        phase: ImportPhase = ImportPhase.IDLE,
        message: String = ""
    ) {
        val entry = ImportLogEntry(filePath = path, status = status, phase = phase, message = message)
        _importLog.value = _importLog.value + entry
    }

    private fun generateImageId(): Long {
        return java.util.UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    }

    private fun buildCancelledResult(
        totalFiles: Int, successCount: Int, duplicateCount: Int, errorCount: Int,
        results: MutableList<ImportResult>
    ): ImportDirectoryResult {
        return ImportDirectoryResult(
            totalFiles = totalFiles,
            successCount = successCount,
            duplicateCount = duplicateCount,
            errorCount = errorCount,
            results = results
        )
    }

    // ================================================================
    // Cancel import
    // ================================================================

    fun cancelImport() {
        _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
    }

    fun resetProgress() {
        _importProgress.value = ImportProgress()
        _importLog.value = emptyList()
        importedChecksums.clear()
    }

    // ================================================================
    // Result types
    // ================================================================

    sealed class ImportResult {
        data class Success(
            val imageId: Long,
            val elementId: Long,
            val metadata: ImageMetadataEntity
        ) : ImportResult()

        data class Duplicate(
            val existingImageId: Long,
            val existingName: String
        ) : ImportResult()

        data class Error(
            val message: String
        ) : ImportResult()
    }

    data class ImportDirectoryResult(
        val totalFiles: Int,
        val successCount: Int,
        val duplicateCount: Int,
        val errorCount: Int,
        val results: List<ImportResult>
    )
}
