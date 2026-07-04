package com.alcedo.studio

import android.app.Application
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import com.alcedo.studio.security.SecurityChecker

class AlcedoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install crash handler first
        CrashHandler.initialize(this)

        // Initialize privacy manager and apply retention policy
        PrivacyManager.initialize(this)
        PrivacyManager.applyRetentionPolicy(this)

        // Clean up old temp files
        TempFileManager.cleanupOldFiles(this)

        // Run security checks
        try {
            val securityStatus = SecurityChecker.checkSecurity(this)
            if (securityStatus.isDebuggerAttached && !BuildConfig.DEBUG) {
                Log.w("AlcedoApp", "Debugger detected in release build!")
            }
            if (securityStatus.isRooted) {
                Log.i("AlcedoApp", "Device appears to be rooted")
            }
        } catch (e: Exception) {
            Log.e("AlcedoApp", "Security check failed", e)
        }

        try {
            AppModule.initialize(this)
        } catch (e: Exception) {
            Log.e("AlcedoApp", "Failed to initialize app module", e)
        }
    }
}
