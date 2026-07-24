package com.alcedo.studio

import android.app.Application
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.crash.CrashReportService
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.GpuPipelineService
import com.alcedo.studio.domain.service.GpuService
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import com.alcedo.studio.security.SecurityChecker
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.utils.MemoryGuard

class AlcedoApplication : Application() {
    private val trimMemoryLock = Any()

    override fun onCreate() {
        super.onCreate()

        try {
            CrashHandler.initialize(this)
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "Failed to install crash handler", e)
        }

        runSafe("PrivacyManager.initialize") { PrivacyManager.initialize(this) }
        runSafe("PrivacyManager.applyRetentionPolicy") { PrivacyManager.applyRetentionPolicy(this) }
        runSafe("TempFileManager.cleanupOldFiles") { TempFileManager.cleanupOldFiles(this) }
        try {
            HapticFeedback.initialize(this)
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "HapticFeedback.initialize failed, haptic feedback disabled", e)
        }

        runSafe("CrashReportService.syncConsent") {
            val consent = PrivacyManager.getConsentStatus().crashReports
            CrashReportService.setUploadEnabled(consent)
            CrashReportService.logBreadcrumb("app_start", "onCreate")
            CrashReportService.flushReports()
        }

        runSafe("SecurityChecker.checkSecurity") {
            val securityStatus = SecurityChecker.checkSecurity(this)
            if (securityStatus.isDebuggerAttached && !BuildConfig.DEBUG) {
                Log.w("AlcedoApp", "Debugger detected in release build!")
            }
            if (securityStatus.isRooted) {
                Log.i("AlcedoApp", "Device appears to be rooted")
            }
        }

        AppModule.initialize(this)

        try {
            AppModule.database.openHelper.writableDatabase
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "Database pre-warm failed", e)
        }

        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                synchronized(trimMemoryLock) {
                    when {
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                            Log.w("AlcedoApp", "onTrimMemory level=$level — low memory, clearing caches")
                            AppModule.thumbnailService.clearMemoryCache()
                            MemoryGuard.emergencyGC()
                        }
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                            Log.d("AlcedoApp", "onTrimMemory level=$level — releasing caches")
                            AppModule.thumbnailService.clearMemoryCache()
                        }
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                            AppModule.thumbnailService.clearMemoryCache()
                        }
                    }
                }
            }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                synchronized(trimMemoryLock) {
                    Log.w("AlcedoApp", "onLowMemory — emergency cleanup")
                    AppModule.thumbnailService.clearMemoryCache()
                    MemoryGuard.emergencyGC()
                }
            }
        })

        var gpuAvailable = false
        try {
            val gpuService = AppModule.gpuService
            gpuService.initialize()
            Log.i("AlcedoApp", "GPU backend: ${gpuService.currentBackend.value.displayName}")
            gpuAvailable = true
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "GpuService.initialize failed, falling back to CPU-only mode", e)
        }

        if (gpuAvailable) {
            try {
                val gpuPipelineService = AppModule.gpuPipelineService
                val supported = gpuPipelineService.checkGpuSupport()
                Log.i("AlcedoApp", "GPU Compute (GLES 3.1) supported: $supported")
                if (supported) {
                    AppModule.pipelineService.gpuPipelineService = gpuPipelineService
                }
            } catch (e: Throwable) {
                Log.e("AlcedoApp", "GpuPipelineService.setup failed, falling back to CPU-only pipeline", e)
            }
        } else {
            Log.i("AlcedoApp", "GPU unavailable — running in CPU-only pipeline mode")
        }
    }

    private inline fun runSafe(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "$tag failed", e)
        }
    }
}
