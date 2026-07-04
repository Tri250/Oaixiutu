package com.alcedo.studio.ndk

/**
 * JNI bridge for general Alcedo core utilities.
 */
object AlcedoNdkBridge {
    init {
        System.loadLibrary("alcedo_core")
    }

    /** Legacy string test. */
    external fun stringFromJNI(): String

    /** Generate a unique monotonically-increasing ID (uint32). */
    external fun nativeGenerateId(): Long

    /** Current timestamp in milliseconds. */
    external fun nativeGetTimestampMillis(): Long

    /** Current timestamp in microseconds. */
    external fun nativeGetTimestampMicros(): Long

    /** Set the native log level (0=VERBOSE..6=SILENT). */
    external fun nativeSetLogLevel(level: Int)
}
