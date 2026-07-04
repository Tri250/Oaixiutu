package com.alcedo.studio.domain.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpuBackend {
    NONE, OPENGL_ES, VULKAN, CPU;

    val displayName: String
        get() = when (this) {
            NONE -> "None"
            OPENGL_ES -> "OpenGL ES"
            VULKAN -> "Vulkan"
            CPU -> "CPU (Software)"
        }
}

data class GpuDeviceInfo(
    val name: String = "Unknown",
    val vendor: String = "Unknown",
    val renderer: String = "Unknown",
    val version: String = "Unknown",
    val supportsComputeShaders: Boolean = false,
    val supportsEglImage: Boolean = false,
    val supportsFloat16: Boolean = false,
    val supportsFloat32: Boolean = false
)

data class GpuPerfMetrics(
    val operation: String,
    val backend: GpuBackend,
    val elapsedMs: Double,
    val width: Int,
    val height: Int,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class GpuService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: GpuService? = null

        fun getInstance(context: Context): GpuService {
            return instance ?: synchronized(this) {
                instance ?: GpuService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _currentBackend = MutableStateFlow(GpuBackend.NONE)
    val currentBackend: StateFlow<GpuBackend> = _currentBackend.asStateFlow()

    private val _preferredBackend = MutableStateFlow(GpuBackend.OPENGL_ES)
    val preferredBackend: StateFlow<GpuBackend> = _preferredBackend.asStateFlow()

    private val _deviceInfo = MutableStateFlow(GpuDeviceInfo())
    val deviceInfo: StateFlow<GpuDeviceInfo> = _deviceInfo.asStateFlow()

    private val _availableBackends = MutableStateFlow<List<GpuBackend>>(emptyList())
    val availableBackends: StateFlow<List<GpuBackend>> = _availableBackends.asStateFlow()

    fun initialize(): GpuBackend {
        _currentBackend.value = GpuBackend.CPU
        _availableBackends.value = listOf(GpuBackend.CPU)
        return GpuBackend.CPU
    }

    fun shutdown() {}
    fun setPreferredBackend(backend: GpuBackend): Boolean = false
    fun lockBackend(locked: Boolean) {}
    fun isBackendLocked(): Boolean = false
    fun isBackendAvailable(backend: GpuBackend): Boolean = backend in _availableBackends.value
    fun hasFeature(feature: String): Boolean = false
    fun recordPerfMetric(metric: GpuPerfMetrics) {}
    fun getRecentMetrics(count: Int = 50): List<GpuPerfMetrics> = emptyList()
    fun getAverageTimeMs(operation: String): Double = 0.0
    fun getGpuCpuTimeRatio(): Float = 0f
    fun getOperationCounts(): Map<String, Long> = emptyMap()
    fun setPerfCollectionEnabled(enabled: Boolean) {}
    fun isPerfCollectionEnabled(): Boolean = false
    fun clearMetrics() {}
    fun getSummary(): String = "GpuService stub"
}
