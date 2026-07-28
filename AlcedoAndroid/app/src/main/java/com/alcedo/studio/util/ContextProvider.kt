package com.alcedo.studio.util

import android.app.Application
import android.content.Context

/**
 * Application-wide context provider. Holds a reference to the application context
 * set during [AlcedoApplication.onCreate] so non-component classes (repositories,
 * services, helpers) can access it without constructor injection of [Context].
 *
 * For Hilt-provided singletons prefer injecting [Context] / [Application] directly;
 * this is a fallback for utility code and JNI callbacks.
 */
object ContextProvider {

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()

    /** Initialise with the application context. Safe to call once. */
    fun init(application: Application) {
        synchronized(lock) {
            if (appContext == null) {
                appContext = application.applicationContext
            }
        }
    }

    /** The application context, or null if [init] has not been called. */
    fun context(): Context? = appContext

    /** The application context, throwing if not yet initialised. */
    fun requireContext(): Context =
        appContext ?: error("ContextProvider not initialised. Call init(application) first.")

    val isInitialised: Boolean get() = appContext != null

    /** The application's private files directory. */
    fun filesDir(): java.io.File =
        requireContext().filesDir

    /** The application's cache directory. */
    fun cacheDir(): java.io.File =
        requireContext().cacheDir

    /** A dedicated subdirectory under files dir, created if missing. */
    fun subdir(name: String): java.io.File {
        val dir = java.io.File(filesDir(), name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** A dedicated subdirectory under cache dir, created if missing. */
    fun cacheSubdir(name: String): java.io.File {
        val dir = java.io.File(cacheDir(), name)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
