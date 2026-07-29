package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.model.ImageMetadata
import com.alcedo.studio.data.model.isRawExtension
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.MemoryGuard
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAW / raster image decoding service. Uses the native decoder (libraw-backed)
 * for RAW files and the platform decoder for rasters, with memory-aware sizing.
 * Each decoded image is tracked as a native handle the pipeline can consume.
 */
@Singleton
class DecodeService @Inject constructor(
    private val memoryGuard: MemoryGuard,
) {

    data class DecodedImage(
        val handle: Long,
        val width: Int,
        val height: Int,
        val isRaw: Boolean,
    ) {
        val isValid: Boolean get() = handle != 0L
    }

    /** Decode [uri] for editing at a memory-appropriate resolution. */
    suspend fun decode(uri: Uri): DecodedImage? = withContext(ThreadPool.compute) {
        val path = uri.toString()
        val isRaw = isRawExtension(path.substringAfterLast('.', ""))
        val maxDim = memoryGuard.suggestedMaxDim()
        val handle = if (isRaw) {
            NdkSafeCall.handle {
                AlcedoNativeBridge.nativeDecodeRaw(uri.toString(), maxDim, "AHD")
            }
        } else {
            NdkSafeCall.handle {
                AlcedoNativeBridge.nativeDecodeImage(uri.toString(), maxDim)
            }
        }
        if (handle == 0L) {
            Log.w(TAG, "native decode returned 0 for $uri")
            return@withContext null
        }
        val dims = NdkSafeCall.call(default = intArrayOf(0, 0, 4)) {
            AlcedoNativeBridge.nativeImageDimensions(handle)
        }
        DecodedImage(handle, dims.getOrElse(0) { 0 }, dims.getOrElse(1) { 0 }, isRaw)
    }

    /** Decode a thumbnail-sized bitmap directly (no native handle kept). */
    suspend fun decodeThumbnail(uri: Uri, size: Int = 320): Bitmap? = withContext(ThreadPool.thumbnail) {
        // Prefer the native fast thumbnail path; fall back to the platform decoder.
        val native = NdkSafeCall.callOrNull {
            AlcedoNativeBridge.nativeDecodeThumbnail(uri.toString(), size)
        }
        native ?: BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, size)
    }

    /** Extract EXIF/metadata as an [ImageMetadata] (best-effort). */
    suspend fun extractMetadata(uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        val json = NdkSafeCall.callOrNull {
            AlcedoNativeBridge.nativeExtractMetadata(uri.toString())
        } ?: return@withContext null
        runCatching {
            val obj = Json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), json)
            val str = { k: String -> obj[k]?.jsonPrimitive?.content }
            ImageMetadata(
                imageId = uri.toString(),
                make = str("make"),
                model = str("model"),
                lensModel = str("lensModel"),
                focalLength = str("focalLength")?.toFloatOrNull(),
                aperture = str("aperture")?.toFloatOrNull(),
                shutterSpeed = str("shutterSpeed"),
                iso = str("iso")?.toIntOrNull(),
                width = str("width")?.toIntOrNull() ?: 0,
                height = str("height")?.toIntOrNull() ?: 0,
                captureDate = str("captureDate"),
                isRaw = isRawExtension(uri.toString().substringAfterLast('.', "")),
            )
        }.onFailure { Log.w(TAG, "metadata parse failed", it) }.getOrNull()
    }

    /** Render a decoded native image to a [Bitmap] for preview display. */
    suspend fun toBitmap(image: DecodedImage, maxWidth: Int = 2048): Bitmap? =
        withContext(ThreadPool.compute) {
            if (!image.isValid) return@withContext null
            NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeImageToBitmap(image.handle, maxWidth) }
        }

    /** Release a decoded image handle. */
    fun release(image: DecodedImage) {
        if (!image.isValid) return
        NdkSafeCall.run { AlcedoNativeBridge.nativeReleaseImage(image.handle) }
    }

    companion object {
        private const val TAG = "DecodeService"
    }
}
