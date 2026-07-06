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

class AlcedoApplication : Application() {
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
        runSafe("HapticFeedback.initialize") { HapticFeedback.initialize(this) }

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

        runSafe("AppModule.initialize") { AppModule.initialize(this) }

        // ── GPU 能力检测 ──
        // 初始化底层 GpuService（探测 GLES / Vulkan 可用性），并创建可选的
        // GpuPipelineService 注入到 PipelineService 中。真正的 GPU 程序与纹理
        // 创建会在首次 applyPipeline 时按需在 GL 线程上完成，此处仅做能力探测
        // 与服务装配，不创建 GL 上下文。
        runSafe("GpuService.initialize") {
            val gpuService = GpuService.getInstance(this)
            gpuService.initialize()
            Log.i("AlcedoApp", "GPU backend: ${gpuService.currentBackend.value.displayName}")
        }

        runSafe("GpuPipelineService.setup") {
            val gpuPipelineService = GpuPipelineService(this)
            val supported = gpuPipelineService.checkGpuSupport()
            Log.i("AlcedoApp", "GPU Compute (GLES 3.1) supported: $supported")
            if (supported) {
                // 注入到 PipelineService，使其在 applyPipeline 中优先尝试 GPU 路径。
                // 注意：GPU 渲染器的真正 initialize() 需在 GL 线程上调用，
                // 此处仅完成服务装配；GPU 不可用或不支持时 PipelineService 会自动
                // 回退到 CPU 原生管线。
                AppModule.pipelineService.gpuPipelineService = gpuPipelineService
            }
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
