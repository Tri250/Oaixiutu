package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Kotlin to the native colour-science module (core/edit/operators/cst).
 * Provides colour-space transform matrices, ACES 2.0 / OpenDRT display transform
 * selection and the LMT (Look Modification Table) application path.
 */
@Singleton
class ColorScienceBridge @Inject constructor() {

    /** Compute a 3x3 matrix (row-major, length 9) from [fromSpace] to [toSpace]. */
    fun conversionMatrix(fromSpace: String, toSpace: String): FloatArray {
        if (!NdkSafeCall.ensureLoaded()) return identityMatrix()
        return NdkSafeCall.call(default = identityMatrix()) {
            AlcedoNativeBridge.nativeColorScienceMatrix(fromSpace, toSpace)
        }.also {
            if (it.size != 9) {
                Log.w(TAG, "matrix unexpected size ${it.size}; falling back to identity")
                return identityMatrix()
            }
        }
    }

    /** Apply a 3x3 matrix to an RGBA pixel buffer (length N, returned N). */
    fun applyMatrix(matrix: FloatArray, input: IntArray): IntArray {
        if (matrix.size != 9) return input
        if (!NdkSafeCall.ensureLoaded()) return input
        return NdkSafeCall.call(default = input) {
            AlcedoNativeBridge.nativeColorScienceApply(matrix, input)
        }
    }

    /** Resolve a lens-correction profile JSON for [lensId], or null. */
    fun lensCorrectionProfile(lensId: String): String? {
        if (!NdkSafeCall.ensureLoaded()) return null
        return NdkSafeCall.callOrNull {
            AlcedoNativeBridge.nativeLensCorrectionProfile(lensId)
        }
    }

    /** True when the native colour-science module reports Vulkan acceleration. */
    fun isAccelerated(): Boolean = NdkSafeCall.call(default = false) {
        AlcedoNativeBridge.nativeGpuAvailable()
    }

    /** Canonical list of supported display transforms. */
    fun displayTransforms(): List<String> = listOf("OpenDRT", "ACES 2.0", "Rec709", "BT.2390", "Filmic")

    /** Canonical list of supported output colour spaces. */
    fun outputColorSpaces(): List<String> =
        listOf("sRGB", "Display P3", "Rec2020", "AdobeRGB", "ACEScg", "ACES2065-1")

    /** Canonical list of supported EOTFs. */
    fun eotfs(): List<String> = listOf("sRGB", "Linear", "BT.1886", "PQ", "HLG")

    fun identityMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )

    companion object {
        private const val TAG = "ColorScienceBridge"
    }
}
