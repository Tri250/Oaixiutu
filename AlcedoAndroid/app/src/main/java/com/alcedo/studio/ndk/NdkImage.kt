package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for image import / load / metadata operations.
 * Named NdkImage to avoid clash with android.media.Image.
 * Matches C++ Java_com_alcedo_studio_ndk_Image_*.
 *
 * NOTE: The C++ JNI class name is "Image", so the Kotlin class name
 * must also be "Image" for JNI resolution. We use a file-level
 * @JvmName strategy — the class is declared as `Image` but the file
 * is named NdkImage.kt for clarity.
 */
@Keep
object Image {
    external fun nativeImportImage(path: String, name: String): Int
    external fun nativeImportBatch(paths: Array<String>): IntArray
    external fun nativeLoadImage(path: String): Int
    external fun nativeLoadThumbnail(path: String, maxSize: Int): Int
    external fun nativeRemoveImage(imageId: Int)
    external fun nativeGetImageInfo(imageId: Int): String
}
