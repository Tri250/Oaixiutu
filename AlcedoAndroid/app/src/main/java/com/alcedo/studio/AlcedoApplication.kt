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

        // Install crash handler first so any subsequent crash is recorded.
        // CrashHandler.initialize also bootstraps CrashReportService.
        try {
            CrashHandler.initialize(this)
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "Failed to install crash handler", e)
        }

        // Each initialization step is fully isolated; nothing here
        // can take down the application.
        runSafe("PrivacyManager.initialize") { PrivacyManager.initialize(this) }
        runSafe("PrivacyManager.applyRetentionPolicy") { PrivacyManager.applyRetentionPolicy(this) }
        runSafe("TempFileManager.cleanupOldFiles") { TempFileManager.cleanupOldFiles(this) }
        try {
            HapticFeedback.initialize(this)
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "HapticFeedback.initialize failed, haptic feedback disabled", e)
        }

        // Wire crash-report upload consent into the reporting service and
        // attempt a best-effort flush of any reports left from a prior run.
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

        // AppModule.initialize 不能被吞掉异常 — 如果失败,整个应用的 DI 容器
        // 将不可用 (所有 AppModule.context 访问都会抛 IllegalStateException),
        // 导致导入等功能完全瘫痪。必须让初始化失败直接崩溃,让用户立即发现问题。
        AppModule.initialize(this)

        // 预热数据库: 在 Application.onCreate 中触发 Room 惰性初始化,
        // 将数据库打开/迁移失败提前到启动阶段,避免首次导入才发现问题
        try {
            AppModule.database.openHelper.writableDatabase
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "Database pre-warm failed", e)
        }

        // Register memory pressure callback
        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                synchronized(trimMemoryLock) {
                    when {
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                            // Running low on memory — aggressive cache cleanup
                            Log.w("AlcedoApp", "onTrimMemory level=$level — low memory, clearing caches")
                            AppModule.thumbnailService.clearMemoryCache()
                            MemoryGuard.emergencyGC()
                        }
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                            // Release non-essential caches
                            Log.d("AlcedoApp", "onTrimMemory level=$level — releasing caches")
                            AppModule.thumbnailService.clearMemoryCache()
                        }
                        level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                            // UI hidden — release UI caches
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

        // ── GPU 能力检测 ──
        // 初始化底层 GpuService（探测 GLES / Vulkan 可用性），并创建可选的
        // GpuPipelineService 注入到 PipelineService 中。真正的 GPU 程序与纹理
        // 创建会在首次 applyPipeline 时按需在 GL 线程上完成，此处仅做能力探测
        // 与服务装配，不创建 GL 上下文。
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
