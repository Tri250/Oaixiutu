package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for image export operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Export_*.
 */
@Keep
object Export {
    external fun nativeExportImage(imageId: Int, outPath: String, format: String, quality: Int): Boolean
    external fun nativeExportJpeg(imageId: Int, outPath: String, quality: Int): Boolean
    external fun nativeExportPng(imageId: Int, outPath: String): Boolean
    external fun nativeExportTiff(imageId: Int, outPath: String): Boolean
    external fun nativeExportUltraHdr(sdrId: Int, hdrId: Int, outPath: String, quality: Int): Boolean
    external fun nativePackageProject(outArchive: String): Boolean
}
