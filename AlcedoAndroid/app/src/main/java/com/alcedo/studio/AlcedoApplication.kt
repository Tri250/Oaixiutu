package com.alcedo.studio

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.domain.service.GpuService
import com.alcedo.studio.domain.service.PresetService
import com.alcedo.studio.domain.service.SleeveService
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alcedo application entry point. Registered as `android:name=".AlcedoApplication"`
 * in the manifest. Annotated with [HiltAndroidApp] so Hilt generates the
 * dependency-injection component and injects fields on this class.
 *
 * Responsibilities:
 *  - Initialise [ContextProvider] so non-Hilt utilities can reach the app context.
 *  - Load the native `alcedo_native` library (best-effort; the app degrades to a
 *    software path when Vulkan is unavailable).
 *  - Open the native sleeve database and seed built-in presets.
 *  - Hook [ComponentCallbacks2] memory pressure to shed native + thumbnail caches.
 *  - Schedule periodic temp-file sweeping.
 */
@HiltAndroidApp
class AlcedoApplication : Application(), ComponentCallbacks2 {

    @Inject lateinit var sleeveService: SleeveService
    @Inject lateinit var presetService: PresetService
    @Inject lateinit var gpuService: GpuService
    @Inject lateinit var tempFileManager: TempFileManager
    @Inject lateinit var sleeveDatabase: SleeveDatabase
    @Inject lateinit var privacyManager: PrivacyManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Make the app context available to non-Hilt utility classes BEFORE any
        // service touches them. This must happen first.
        ContextProvider.init(this)

        // Native library load is best-effort: emulators and devices without the
        // declared Vulkan compute feature still boot, just with degraded paths.
        val nativeOk = NdkSafeCall.ensureLoaded()
        if (nativeOk) {
            Log.i(TAG, "alcedo_native loaded: ${gpuService.nativeVersion()}")
            // Pass cache/temp dirs to the native layer so it can write scratch files.
            NdkSafeCall.run { AlcedoNativeBridge.nativeSetCacheDir(cacheDir.absolutePath) }
            NdkSafeCall.run { AlcedoNativeBridge.nativeSetTempDir(noBackupFilesDir.absolutePath) }
        } else {
            Log.w(TAG, "alcedo_native unavailable; running in software/degraded mode. " +
                "Error: ${AlcedoNativeBridge.lastLoadError}")
        }

        // Open the sleeve (project filesystem) database and seed built-in presets.
        appScope.launch {
            runCatching { sleeveService.open(SleeveService.defaultDbPath()) }
                .onFailure { Log.w(TAG, "sleeve open failed", it) }
            runCatching { presetService.ensureBuiltIns() }
                .onFailure { Log.w(TAG, "preset seeding failed", it) }
            // Sweep orphaned temp files left over from a previous crash.
            runCatching { tempFileManager.sweepOrphans() }
        }

        Log.i(TAG, "AlcedoApplication ready (native=$nativeOk, " +
            "gpu=${gpuService.isAvailable()}, privacy=${privacyManager.javaClass.simpleName})")
    }

    // ---- ComponentCallbacks2: memory pressure -----------------------------

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE -> {
                // Aggressive: drop caches across all layers.
                gpuService.onLowMemory()
                NdkSafeCall.run { AlcedoNativeBridge.nativeOnLowMemory() }
                tempFileManager.cleanupAll()
            }
            TRIM_MEMORY_MODERATE, TRIM_MEMORY_BACKGROUND -> {
                NdkSafeCall.run { AlcedoNativeBridge.nativeOnLowMemory() }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Theme/locale change; Compose recomposes automatically. No state to
        // rebuild here, but the callback is wired for future use (e.g. clearing
        // bitmap caches that depend on density).
    }

    override fun onLowMemory() {
        super.onLowMemory()
        gpuService.onLowMemory()
        NdkSafeCall.run { AlcedoNativeBridge.nativeOnLowMemory() }
    }

    companion object {
        private const val TAG = "AlcedoApplication"
    }
}
