package com.alcedo.studio.ndk

import com.alcedo.studio.domain.service.DecodeService
import com.alcedo.studio.domain.service.NativeDecodeResult
import com.alcedo.studio.domain.service.NativeThumbnailResult
import com.alcedo.studio.domain.service.NativeRawInfoResult

/**
 * JNI bridge for native decoding operations.
 *
 * Provides native methods for RAW decode, metadata extraction,
 * thumbnail generation, and format detection. All methods are
 * implemented in the alcedo_core native library.
 */
object DecodeNdkBridge {
    init {
        System.loadLibrary("alcedo_core")
    }

    /**
     * Progress callback interface for native decode operations.
     */
    interface DecodeProgressCallback {
        fun onProgress(jobId: Long, progress: Float, stage: String)
        fun onComplete(jobId: Long, success: Boolean, error: String)
    }

    // ── Format Detection ──

    /**
     * Detect the image format from file header bytes.
     * @return Format string: "DNG", "NEF", "CR2", "ARW", "JPEG", "PNG", etc.
     */
    external fun nativeDetectFormat(filePath: String): String

    /**
     * Check if the file is a supported RAW format.
     */
    external fun nativeIsRawFormat(filePath: String): Boolean

    // ── RAW Image Info ──

    /**
     * Read RAW image information quickly without full decode.
     * Returns structured info about the RAW file.
     */
    external fun nativeReadRawInfo(filePath: String): NativeRawInfoResult?

    // ── Full RAW Decode ──

    /**
     * Full RAW decode with all options.
     *
     * @param filePath Absolute path to the RAW file
     * @param demosaic Demosaic algorithm (0=RCD, 1=AMAZE, 2=DCB, 3=BILINEAR, 4=VNG4)
     * @param highlightReconstruction Enable highlight reconstruction
     * @param useCameraMatrix Apply camera color matrix to sRGB
     * @param halfResolution Decode at half resolution
     * @param outputFloat Output float32 RGB data
     * @param extractThumbnail Extract embedded thumbnail
     * @param extractPreview Extract embedded JPEG preview
     * @param maxThumbnailDim Maximum thumbnail dimension
     * @param wbIlluminant White balance illuminant (6=camera auto, 0=daylight, 1=tungsten)
     * @return Decoded result with RGB data, thumbnails, and metadata
     */
    external fun nativeDecodeRaw(
        filePath: String,
        demosaic: Int,
        highlightReconstruction: Boolean,
        useCameraMatrix: Boolean,
        halfResolution: Boolean,
        outputFloat: Boolean,
        extractThumbnail: Boolean,
        extractPreview: Boolean,
        maxThumbnailDim: Int,
        wbIlluminant: Int
    ): NativeDecodeResult?

    /**
     * Decode RAW from a memory buffer.
     */
    external fun nativeDecodeRawFromMemory(
        data: ByteArray,
        demosaic: Int,
        highlightReconstruction: Boolean,
        useCameraMatrix: Boolean,
        halfResolution: Boolean,
        outputFloat: Boolean
    ): NativeDecodeResult?

    // ── Metadata Extraction ──

    /**
     * Extract all metadata from an image file.
     * @return JSON string with structured metadata
     */
    external fun nativeExtractMetadata(filePath: String): String?

    /**
     * Extract metadata from a memory buffer.
     */
    external fun nativeExtractMetadataFromMemory(data: ByteArray): String?

    /**
     * Extract only EXIF metadata.
     */
    external fun nativeExtractExif(filePath: String): String?

    /**
     * Extract XMP metadata.
     */
    external fun nativeExtractXmp(filePath: String): String?

    /**
     * Extract ICC profile.
     */
    external fun nativeExtractIccProfile(filePath: String): ByteArray?

    /**
     * Extract DNG color metadata.
     */
    external fun nativeExtractDngColor(filePath: String): FloatArray?

    // ── Thumbnail Generation ──

    /**
     * Generate a thumbnail from an image file.
     * Tries embedded thumbnail first, then generates from decoded data.
     *
     * @param filePath Absolute path to the image file
     * @param maxDimension Maximum dimension of the thumbnail
     * @param useEmbedded Try to use embedded thumbnail
     * @return Thumbnail data (JPEG or raw RGB) with dimensions
     */
    external fun nativeGenerateThumbnail(
        filePath: String,
        maxDimension: Int,
        useEmbedded: Boolean
    ): NativeThumbnailResult?

    /**
     * Generate thumbnail from decoded float RGB data.
     */
    external fun nativeGenerateThumbnailFromRGB(
        rgbData: FloatArray,
        width: Int,
        height: Int,
        maxDimension: Int
    ): ByteArray?

    /**
     * Extract the embedded JPEG thumbnail from a RAW file.
     */
    external fun nativeExtractEmbeddedThumbnail(filePath: String): ByteArray?

    /**
     * Extract the embedded JPEG preview from a RAW file.
     */
    external fun nativeExtractEmbeddedPreview(filePath: String): ByteArray?

    // ── Demosaic Operations ──

    /**
     * Demosaic a RAW Bayer pattern to RGB float data.
     *
     * @param rawCfaData 16-bit RAW CFA data
     * @param width Width in pixels
     * @param height Height in pixels
     * @param bayerPattern Bayer pattern: 0=RGGB, 1=BGGR, 2=GRBG, 3=GBRG
     * @param whiteLevel White level for normalization
     * @param blackLevel Black level for subtraction
     * @param demosaicMethod Demosaic algorithm: 0=RCD, 1=AMAZE, 2=DCB, 3=BILINEAR, 4=VNG4
     * @return Float RGB data (width * height * 3)
     */
    external fun nativeDemosaic(
        rawCfaData: ShortArray,
        width: Int,
        height: Int,
        bayerPattern: Int,
        whiteLevel: Int,
        blackLevel: Int,
        demosaicMethod: Int
    ): FloatArray?

    // ── White Balance ──

    /**
     * Apply white balance multipliers to float RGB data.
     */
    external fun nativeApplyWhiteBalance(
        rgbData: FloatArray,
        width: Int,
        height: Int,
        rMultiplier: Float,
        gMultiplier: Float,
        bMultiplier: Float
    )

    // ── Color Matrix ──

    /**
     * Apply a 3x3 color matrix to float RGB data.
     */
    external fun nativeApplyColorMatrix(
        rgbData: FloatArray,
        width: Int,
        height: Int,
        matrix: FloatArray  // 9 elements, row-major
    )

    // ── Highlight Reconstruction ──

    /**
     * Apply highlight reconstruction to float RGB data.
     */
    external fun nativeReconstructHighlights(
        rgbData: FloatArray,
        width: Int,
        height: Int,
        whiteLevel: Int,
        mode: Int  // 0=CLIP, 1=UNCLIP, 2=BLEND, 3=RECONSTRUCT
    )

    // ── Black Level ──

    /**
     * Subtract black level from 16-bit RAW data.
     */
    external fun nativeSubtractBlackLevel(
        rawData: ShortArray,
        width: Int,
        height: Int,
        blackLevelR: Int,
        blackLevelG1: Int,
        blackLevelG2: Int,
        blackLevelB: Int
    )

    // ── Cancellation ──

    /**
     * Cancel a specific decode operation.
     */
    external fun nativeCancelDecode(jobId: Long)

    /**
     * Cancel all pending decode operations.
     */
    external fun nativeCancelAllDecodes()

    // ── Scheduler Control ──

    /**
     * Initialize the native decoder scheduler with thread count.
     */
    external fun nativeInitScheduler(threadCount: Int)

    /**
     * Shutdown the native decoder scheduler.
     */
    external fun nativeShutdownScheduler()

    /**
     * Set the progress callback for native decode operations.
     */
    external fun nativeSetProgressCallback(callback: DecodeProgressCallback?)
}