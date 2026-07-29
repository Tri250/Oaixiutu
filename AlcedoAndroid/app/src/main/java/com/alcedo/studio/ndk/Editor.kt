package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for editing operator operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Editor_*.
 */
@Keep
object Editor {
    external fun nativeListOperators(): Array<String>
    external fun nativeApplyOperator(imageId: Int, opName: String, paramJson: String): Int
    external fun nativeSetOperatorParam(imageId: Int, opName: String, paramJson: String)
    external fun nativeGetOperatorParam(opName: String): String
    external fun nativeResetOperator(imageId: Int, opName: String)
    external fun nativeCopyAdjustments(srcFileId: Int, destFileId: Int): Boolean
}
