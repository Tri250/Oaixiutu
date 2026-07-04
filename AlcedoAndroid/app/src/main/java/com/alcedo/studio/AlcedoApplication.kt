package com.alcedo.studio

import android.app.Application
import com.alcedo.studio.di.AppModule

class AlcedoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
    }
}
