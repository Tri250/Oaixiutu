package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for pipeline execution.
 * Matches C++ Java_com_alcedo_studio_ndk_Pipeline_*.
 */
@Keep
object Pipeline {
    external fun nativeExecute(imageId: Int, paramJson: String): Int
    external fun nativeExportParams(imageId: Int): String
    external fun nativeImportParams(imageId: Int, paramJson: String)
    external fun nativeSetRenderRegion(imageId: Int, x: Int, y: Int, scaleX: Float, scaleY: Float, refW: Int, refH: Int)
    external fun nativeSetRenderRes(fullRes: Boolean, maxSide: Int)
    external fun nativeIsVulkanAvailable(): Boolean
}
