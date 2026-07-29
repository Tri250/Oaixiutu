package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for scope analysis operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Scope_*.
 */
@Keep
object Scope {
    external fun nativeSubmitFrame(imageId: Int, scopeMask: Int, histogramBins: Int, waveformW: Int, waveformH: Int)
    external fun nativeGetScopeJson(): String
    external fun nativeGetHistogram(): IntArray
    external fun nativeGetWaveform(): FloatArray
    external fun nativeGetVectorscope(): FloatArray
    external fun nativeReleaseResources()
}
