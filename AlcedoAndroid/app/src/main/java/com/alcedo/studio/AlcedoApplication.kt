package com.alcedo.studio

import android.app.Application
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.security.TempFileManager
import com.alcedo.studio.security.SecurityChecker
import com.alcedo.studio.ui.theme.ThemeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlcedoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install crash handler first
        CrashHandler.initialize(this)

        // Initialize privacy manager and apply retention policy
        PrivacyManager.initialize(this)
        PrivacyManager.applyRetentionPolicy(this)

        // Initialize theme and language persistence
        ThemeManager.initialize(this)
        LanguageManager.initialize(this)

        // Clean up old temp files
        TempFileManager.cleanupOldFiles(this)

        // Run security checks
        val securityStatus = SecurityChecker.checkSecurity(this)
        if (!securityStatus.isSecure && !BuildConfig.DEBUG) {
            Log.w("AlcedoApp", "Security check failed in release build")
        }

        try {
            AppModule.initialize(this)
        } catch (e: Exception) {
            Log.e("AlcedoApp", "Failed to initialize app module", e)
            CrashHandler.setCrashCallback { throwable ->
                Log.e("AlcedoApp", "App crashed after partial init", throwable)
            }
        }
    }
}
