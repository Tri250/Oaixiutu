package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for thumbnail generation and cache operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Thumbnail_*.
 */
@Keep
object Thumbnail {
    external fun nativeGenerateThumbnail(imageId: Int, targetSize: Int): Boolean
    external fun nativeGetThumbnailBytes(imageId: Int): ByteArray?
    external fun nativeCacheThumbnail(imageId: Int, bytes: ByteArray)
    external fun nativeEvictThumbnail(imageId: Int)
    external fun nativeClearThumbnailCache()
}
