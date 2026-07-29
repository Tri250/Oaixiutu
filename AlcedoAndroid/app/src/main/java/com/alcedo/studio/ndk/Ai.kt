package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for AI inference operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Ai_*.
 */
@Keep
object Ai {
    external fun nativeDescribeImage(imageId: Int): String
    external fun nativeRateImage(imageId: Int): String
    external fun nativeSetCredential(provider: String, apiKey: String)
    external fun nativeSetProviderProfile(provider: String, baseUrl: String, model: String)
    external fun nativeRegisterModel(key: String, path: String, sizeBytes: Long)
    external fun nativeListModels(): Array<String>
}
