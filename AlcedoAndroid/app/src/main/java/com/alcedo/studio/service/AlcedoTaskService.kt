package com.alcedo.studio.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground service for long-running background tasks (export, import, AI processing).
 *
 * Android 14+ (API 34) requires an explicit [android.content.pm.ServiceInfo.foregroundServiceType]
 * declaration in the manifest and at [startForeground] call time. This service uses
 * `FOREGROUND_SERVICE_TYPE_DATA_SYNC` which maps to the `dataSync` type declared in AndroidManifest.xml.
 *
 * The actual work is orchestrated by [com.alcedo.studio.domain.service.BackgroundTaskService];
 * this class is only the Android framework entry point required for foreground notification.
 */
class AlcedoTaskService : Service() {

    companion object {
        private const val TAG = "AlcedoTaskService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AlcedoTaskService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The foreground notification is posted by BackgroundTaskService.startForegroundNotification()
        // which calls service.startForeground() directly. If we reach here without a notification
        // being posted, the service will be killed by the system — this is expected behavior.
        Log.d(TAG, "onStartCommand: startId=$startId")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AlcedoTaskService destroyed")
    }
}
