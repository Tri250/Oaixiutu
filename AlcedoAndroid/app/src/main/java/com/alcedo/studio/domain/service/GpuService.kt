package com.alcedo.studio.domain.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES31
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * GPU backend types supported by Alcedo Studio.
 */
enum class GpuBackend {
    NONE,
    OPENGL_ES,
    VULKAN,
    CPU;

    val displayName: String
        get() = when (this) {
            NONE -> "None"
            OPENGL_ES -> "OpenGL ES"
            VULKAN -> "Vulkan"
            CPU -> "CPU (Software)"
        }
}

/**
 * Detailed GPU device information.
 */
data class GpuDeviceInfo(
    val name: String = "Unknown",
    val vendor: String = "Unknown",
    val renderer: String = "Unknown",
    val version: String = "Unknown",
    val computeUnits: Int = 0,
    val maxWorkGroupSize: IntArray = IntArray(3),
    val maxWorkGroupInvocations: Int = 0,
    val maxTextureSize: Int = 0,
    val maxShaderStorageBlockSize: Int = 0,
    val maxComputeSharedMemorySize: Int = 0,
    val supportsComputeShaders: Boolean = false,
    val supportsEglImage: Boolean = false,
    val supportsFloat16: Boolean = false,
    val supportsFloat32: Boolean = false,
    val totalMemoryBytes: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GpuDeviceInfo) return false
        return name == other.name && vendor == other.vendor &&
               renderer == other.renderer && version == other.version &&
               computeUnits == other.computeUnits
    }

    override fun hashCode(): Int {
        return name.hashCode() * 31 + vendor.hashCode()
    }
}

/**
 * Performance metrics for GPU operations.
 */
data class GpuPerfMetrics(
    val operation: String,
    val backend: GpuBackend,
    val elapsedMs: Double,
    val width: Int,
    val height: Int,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val megapixelsPerSecond: Double
        get() {
            if (elapsedMs <= 0.0) return 0.0
            val mp = (width.toDouble() * height.toDouble()) / 1_000_000.0
            return mp / (elapsedMs / 1000.0)
        }
}

/**
 * GPU service for device capability detection, backend preference management,
 * and performance monitoring.
 *
 * This service manages:
 * - Device GPU capability detection (OpenGL ES, Vulkan)
 * - Backend preference and runtime switching
 * - Performance metrics collection
 * - GPU feature availability queries
 */
class GpuService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AlcedoGpuService"
        private const val PREFS_NAME = "alcedo_gpu_prefs"
        private const val KEY_PREFERRED_BACKEND = "preferred_backend"
        private const val KEY_BACKEND_LOCKED = "backend_locked"
        private const val KEY_PERF_COLLECTION_ENABLED = "perf_collection_enabled"

        @Volatile
        private var instance: GpuService? = null

        fun getInstance(context: Context): GpuService {
            return instance ?: synchronized(this) {
                instance ?: GpuService(context.applicationContext).also { instance = it }
            }
        }
    }

    // Shared preferences
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Backend state
    private val _currentBackend = MutableStateFlow(GpuBackend.NONE)
    val currentBackend: StateFlow<GpuBackend> = _currentBackend.asStateFlow()

    private val _preferredBackend = MutableStateFlow(GpuBackend.OPENGL_ES)
    val preferredBackend: StateFlow<GpuBackend> = _preferredBackend.asStateFlow()

    private val backendLocked = AtomicBoolean(false)

    // Device info
    private val _deviceInfo = MutableStateFlow(GpuDeviceInfo())
    val deviceInfo: StateFlow<GpuDeviceInfo> = _deviceInfo.asStateFlow()

    // Available backends
    private val _availableBackends = MutableStateFlow<List<GpuBackend>>(emptyList())
    val availableBackends: StateFlow<List<GpuBackend>> = _availableBackends.asStateFlow()

    // Performance monitoring
    private val perfMetrics = mutableListOf<GpuPerfMetrics>()
    private val maxPerfHistory = 200
    private val totalGpuTimeMs = AtomicLong(0L)
    private val totalCpuTimeMs = AtomicLong(0L)
    private val operationCount = ConcurrentHashMap<String, Long>()
    private val perfCollectionEnabled = AtomicBoolean(true)

    // Initialized flag
    private val initialized = AtomicBoolean(false)

    /**
     * Initialize the GPU service, detect capabilities, and set up the preferred backend.
     */
    fun initialize(): GpuBackend {
        if (initialized.getAndSet(true)) {
            return _currentBackend.value
        }

        Log.i(TAG, "Initializing GPU service...")

        // Detect available backends
        val available = detectAvailableBackends()
        _availableBackends.value = available
        Log.i(TAG, "Available backends: ${available.map { it.displayName }}")

        // Load preferred backend from preferences
        val savedBackend = prefs.getString(KEY_PREFERRED_BACKEND, null)?.let {
            try { GpuBackend.valueOf(it) } catch (e: IllegalArgumentException) { null }
        }

        val selectedBackend = if (savedBackend != null && savedBackend in available) {
            savedBackend
        } else {
            // Default: OpenGL ES > Vulkan > CPU
            available.firstOrNull()
        } ?: GpuBackend.CPU

        _preferredBackend.value = selectedBackend
        _currentBackend.value = selectedBackend

        // Load backend locked state
        backendLocked.set(prefs.getBoolean(KEY_BACKEND_LOCKED, false))

        // Load perf collection preference
        perfCollectionEnabled.set(prefs.getBoolean(KEY_PERF_COLLECTION_ENABLED, true))

        // Query device info
        _deviceInfo.value = queryDeviceInfo(selectedBackend)

        Log.i(TAG, "GPU service initialized. Backend: ${selectedBackend.displayName}")
        Log.i(TAG, "Device: ${_deviceInfo.value.name} (${_deviceInfo.value.vendor})")

        return selectedBackend
    }

    /**
     * Release GPU resources.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down GPU service")
        initialized.set(false)
        perfMetrics.clear()
        operationCount.clear()
    }

    /**
     * Detect available GPU backends on the device.
     */
    private fun detectAvailableBackends(): List<GpuBackend> {
        val available = mutableListOf<GpuBackend>()

        // OpenGL ES 3.1+ is available on Android 5.0+ (API 21)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val hasGLES31 = checkGLES31Support()
                if (hasGLES31) {
                    available.add(GpuBackend.OPENGL_ES)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenGL ES 3.1 check failed: ${e.message}")
            }
        }

        // Vulkan is available on Android 7.0+ (API 24)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val hasVulkan = checkVulkanSupport()
                if (hasVulkan) {
                    available.add(GpuBackend.VULKAN)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vulkan check failed: ${e.message}")
            }
        }

        // CPU fallback is always available
        available.add(GpuBackend.CPU)

        return available
    }

    /**
     * Check if OpenGL ES 3.1+ compute shaders are supported.
     */
    private fun checkGLES31Support(): Boolean {
        return try {
            // Try to create a temporary EGL context to check GL version
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return false

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return false

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                EGL14.eglTerminate(display)
                return false
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(display)
                return false
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                return false
            }

            EGL14.eglMakeCurrent(display, surface, surface, context)

            val glVersion = GLES31.glGetString(GLES31.GL_VERSION) ?: ""
            val glRenderer = GLES31.glGetString(GLES31.GL_RENDERER) ?: ""

            Log.i(TAG, "GL Version: $glVersion, Renderer: $glRenderer")

            // Check if version is 3.1 or higher
            val supportsCompute = glVersion.startsWith("OpenGL ES 3.") &&
                                  glVersion.substringAfter("OpenGL ES 3.").toFloatOrNull()?.let { it >= 1.0f } ?: false

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)

            supportsCompute
        } catch (e: Exception) {
            Log.w(TAG, "GLES 3.1 check exception: ${e.message}")
            // Most modern Android devices support GLES 3.1
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        }
    }

    /**
     * Check if Vulkan is supported on this device.
     */
    private fun checkVulkanSupport(): Boolean {
        return try {
            // Check if libvulkan.so is available
            System.loadLibrary("vulkan")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libvulkan.so not available: ${e.message}")
            false
        }
    }

    /**
     * Query detailed GPU device information.
     */
    private fun queryDeviceInfo(backend: GpuBackend): GpuDeviceInfo {
        return when (backend) {
            GpuBackend.OPENGL_ES -> queryGLESDeviceInfo()
            GpuBackend.VULKAN -> queryVulkanDeviceInfo()
            GpuBackend.CPU -> GpuDeviceInfo(
                name = "CPU",
                vendor = "Software",
                renderer = "CPU Fallback",
                version = Build.CPU_ABI
            )
            else -> GpuDeviceInfo()
        }
    }

    private fun queryGLESDeviceInfo(): GpuDeviceInfo {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return GpuDeviceInfo()

            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, context)

            val renderer = GLES31.glGetString(GLES31.GL_RENDERER) ?: "Unknown"
            val vendor = GLES31.glGetString(GLES31.GL_VENDOR) ?: "Unknown"
            val glVersion = GLES31.glGetString(GLES31.GL_VERSION) ?: "Unknown"

            val maxWorkGroupSize = IntArray(3)
            GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 0, maxWorkGroupSize, 0)
            GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 1, maxWorkGroupSize, 1)
            GLES31.glGetIntegeri_v(GLES31.GL_MAX_COMPUTE_WORK_GROUP_SIZE, 2, maxWorkGroupSize, 2)

            val maxInvocations = IntArray(1)
            GLES31.glGetIntegerv(GLES31.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, maxInvocations, 0)

            val maxTexture = IntArray(1)
            GLES31.glGetIntegerv(GLES31.GL_MAX_TEXTURE_SIZE, maxTexture, 0)

            val maxSSBO = IntArray(1)
            GLES31.glGetIntegerv(GLES31.GL_MAX_SHADER_STORAGE_BLOCK_SIZE, maxSSBO, 0)

            val maxSharedMem = IntArray(1)
            GLES31.glGetIntegerv(GLES31.GL_MAX_COMPUTE_SHARED_MEMORY_SIZE, maxSharedMem, 0)

            // Check float support
            val extensions = GLES31.glGetString(GLES31.GL_EXTENSIONS) ?: ""
            val supportsFloat16 = extensions.contains("GL_EXT_color_buffer_half_float")
            val supportsFloat32 = extensions.contains("GL_EXT_color_buffer_float")
            val supportsEglImage = extensions.contains("GL_OES_EGL_image")

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)

            GpuDeviceInfo(
                name = renderer,
                vendor = vendor,
                renderer = renderer,
                version = glVersion,
                maxWorkGroupSize = maxWorkGroupSize,
                maxWorkGroupInvocations = maxInvocations[0],
                maxTextureSize = maxTexture[0],
                maxShaderStorageBlockSize = maxSSBO[0],
                maxComputeSharedMemorySize = maxSharedMem[0],
                supportsComputeShaders = true,
                supportsEglImage = supportsEglImage,
                supportsFloat16 = supportsFloat16,
                supportsFloat32 = supportsFloat32
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query GLES device info: ${e.message}")
            GpuDeviceInfo(name = "OpenGL ES", vendor = "Unknown")
        }
    }

    private fun queryVulkanDeviceInfo(): GpuDeviceInfo {
        return GpuDeviceInfo(
            name = "Vulkan Device",
            vendor = "Vulkan",
            renderer = "Vulkan",
            version = "1.1",
            supportsComputeShaders = true
        )
    }

    /**
     * Set the preferred GPU backend. If the backend is locked, this is ignored.
     */
    fun setPreferredBackend(backend: GpuBackend): Boolean {
        if (backendLocked.get()) {
            Log.w(TAG, "Backend is locked, cannot change to ${backend.displayName}")
            return false
        }

        if (backend !in _availableBackends.value) {
            Log.w(TAG, "Backend ${backend.displayName} is not available")
            return false
        }

        _preferredBackend.value = backend
        _currentBackend.value = backend
        prefs.edit().putString(KEY_PREFERRED_BACKEND, backend.name).apply()

        // Refresh device info
        _deviceInfo.value = queryDeviceInfo(backend)

        Log.i(TAG, "Backend changed to: ${backend.displayName}")
        return true
    }

    /**
     * Lock the current backend to prevent automatic switching.
     */
    fun lockBackend(locked: Boolean) {
        backendLocked.set(locked)
        prefs.edit().putBoolean(KEY_BACKEND_LOCKED, locked).apply()
        Log.i(TAG, "Backend lock: $locked")
    }

    fun isBackendLocked(): Boolean = backendLocked.get()

    /**
     * Check if a specific backend is available on this device.
     */
    fun isBackendAvailable(backend: GpuBackend): Boolean {
        return backend in _availableBackends.value
    }

    /**
     * Check if a specific GPU feature is available.
     */
    fun hasFeature(feature: String): Boolean {
        val info = _deviceInfo.value
        return when (feature.lowercase()) {
            "compute_shaders" -> info.supportsComputeShaders
            "egl_image" -> info.supportsEglImage
            "float16" -> info.supportsFloat16
            "float32" -> info.supportsFloat32
            "vulkan" -> isBackendAvailable(GpuBackend.VULKAN)
            else -> false
        }
    }

    /**
     * Record a performance metric for an operation.
     */
    fun recordPerfMetric(metric: GpuPerfMetrics) {
        if (!perfCollectionEnabled.get()) return

        synchronized(perfMetrics) {
            perfMetrics.add(metric)
            if (perfMetrics.size > maxPerfHistory) {
                perfMetrics.removeAt(0)
            }
        }

        if (metric.success) {
            if (metric.backend == GpuBackend.CPU) {
                totalCpuTimeMs.addAndGet(metric.elapsedMs.toLong())
            } else {
                totalGpuTimeMs.addAndGet(metric.elapsedMs.toLong())
            }
        }

        operationCount.merge(metric.operation, 1L) { old, new -> old + new }
    }

    /**
     * Get performance metrics for the last N operations.
     */
    fun getRecentMetrics(count: Int = 50): List<GpuPerfMetrics> {
        synchronized(perfMetrics) {
            return perfMetrics.takeLast(count)
        }
    }

    /**
     * Get average execution time for a specific operation type.
     */
    fun getAverageTimeMs(operation: String): Double {
        synchronized(perfMetrics) {
            val relevant = perfMetrics.filter { it.operation == operation && it.success }
            if (relevant.isEmpty()) return 0.0
            return relevant.sumOf { it.elapsedMs } / relevant.size
        }
    }

    /**
     * Get total GPU vs CPU time for performance comparison.
     */
    fun getGpuCpuTimeRatio(): Float {
        val gpu = totalGpuTimeMs.get()
        val cpu = totalCpuTimeMs.get()
        return if (cpu > 0) gpu.toFloat() / cpu.toFloat() else 0f
    }

    /**
     * Get operation count statistics.
     */
    fun getOperationCounts(): Map<String, Long> {
        return ConcurrentHashMap(operationCount)
    }

    /**
     * Enable or disable performance metric collection.
     */
    fun setPerfCollectionEnabled(enabled: Boolean) {
        perfCollectionEnabled.set(enabled)
        prefs.edit().putBoolean(KEY_PERF_COLLECTION_ENABLED, enabled).apply()
    }

    fun isPerfCollectionEnabled(): Boolean = perfCollectionEnabled.get()

    /**
     * Clear all performance metrics.
     */
    fun clearMetrics() {
        synchronized(perfMetrics) {
            perfMetrics.clear()
        }
        totalGpuTimeMs.set(0L)
        totalCpuTimeMs.set(0L)
        operationCount.clear()
    }

    /**
     * Get a human-readable summary of the GPU service state.
     */
    fun getSummary(): String {
        val info = _deviceInfo.value
        return buildString {
            appendLine("=== Alcedo GPU Service ===")
            appendLine("Backend: ${_currentBackend.value.displayName}")
            appendLine("Device: ${info.name}")
            appendLine("Vendor: ${info.vendor}")
            appendLine("Version: ${info.version}")
            appendLine("Compute Shaders: ${if (info.supportsComputeShaders) "YES" else "NO"}")
            appendLine("Max Work Group: ${info.maxWorkGroupSize.contentToString()}")
            appendLine("Max Invocations: ${info.maxWorkGroupInvocations}")
            appendLine("Max Texture: ${info.maxTextureSize}")
            appendLine("Available: ${_availableBackends.value.map { it.displayName }}")
            appendLine("Locked: ${backendLocked.get()}")
            appendLine("Perf Collection: ${perfCollectionEnabled.get()}")
            appendLine("Metrics: ${perfMetrics.size} records")
        }
    }
}