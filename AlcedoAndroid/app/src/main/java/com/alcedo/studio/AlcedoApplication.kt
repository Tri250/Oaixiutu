package com.alcedo.studio

import android.app.Application
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import com.alcedo.studio.security.SecurityChecker
import com.alcedo.studio.ui.common.CoachMarkManager
import com.alcedo.studio.ui.common.HapticFeedback

class AlcedoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install crash handler first so any subsequent crash is recorded.
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
        runSafe("CoachMarkManager.initialize") { CoachMarkManager.initialize(this) }

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
    }

    private inline fun runSafe(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e("AlcedoApp", "$tag failed", e)
        }
    }
}
