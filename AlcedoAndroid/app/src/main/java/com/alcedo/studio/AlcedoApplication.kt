package com.alcedo.studio

import android.app.Application
import android.util.Log
import com.alcedo.studio.crash.CrashHandler
import com.alcedo.studio.di.AppModule

class AlcedoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Install crash handler first
        CrashHandler.initialize(this)

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
