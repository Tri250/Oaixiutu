package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for RAW decode operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Raw_*.
 */
@Keep
object Raw {
    external fun nativeDecodeRaw(path: String, decodeRes: Int): Int
    external fun nativeDecodeRawAsync(path: String): Int
    external fun nativeSetRawBackend(backend: Int)
    external fun nativeGetRawMetadata(imageId: Int): String
    external fun nativeSetWhiteBalance(imageId: Int, cct: Float, tint: Float)
}
