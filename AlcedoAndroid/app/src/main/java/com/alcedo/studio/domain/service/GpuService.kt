package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vulkan GPU service. Reports device capability and lifetime-manages the native
 * Vulkan context (context/vulkan_context.cpp). The editor requires
 * `android.hardware.vulkan.compute` v1, declared in the manifest.
 */
@Singleton
class GpuService @Inject constructor() {

    /** True when the native Vulkan context initialised successfully. */
    fun isAvailable(): Boolean = NdkSafeCall.call(default = false) {
        AlcedoNativeBridge.nativeGpuAvailable()
    }

    /** Human-readable device name (driver/GPU), or null. */
    fun deviceName(): String? = NdkSafeCall.callOrNull {
        AlcedoNativeBridge.nativeGpuDeviceName()
    }

    /** Native library version string (e.g. "0.3.0-native"). */
    fun nativeVersion(): String = NdkSafeCall.call(default = "unknown") {
        AlcedoNativeBridge.nativeVersion()
    }

    /** Notify the native layer of low memory so it can shed caches. */
    fun onLowMemory() {
        NdkSafeCall.run { AlcedoNativeBridge.nativeOnLowMemory() }
    }

    /** Ensure the GPU context is up; called lazily before pipeline creation. */
    fun ensureReady(): Boolean = NdkSafeCall.ensureLoaded() && isAvailable()

    companion object {
        private const val TAG = "GpuService"

        /** Compute the ideal number of parallel pipeline stages for this device. */
        fun suggestedConcurrency(): Int {
            val cpus = Runtime.getRuntime().availableProcessors()
            return cpus.coerceIn(2, 4)
        }
    }
}
