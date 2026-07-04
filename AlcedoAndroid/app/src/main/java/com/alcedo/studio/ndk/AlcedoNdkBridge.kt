package com.alcedo.studio.ndk

object AlcedoNdkBridge {
    init {
        System.loadLibrary("alcedo_core")
    }

    external fun stringFromJNI(): String
}
