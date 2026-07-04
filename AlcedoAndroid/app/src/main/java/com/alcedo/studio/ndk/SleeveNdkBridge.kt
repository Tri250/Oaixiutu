package com.alcedo.studio.ndk

object SleeveNdkBridge {
    init {
        System.loadLibrary("alcedo")
    }

    external fun nativeSleeveInitialize(): Boolean
    external fun nativeSleeveGetElement(path: String): String
    external fun nativeSleeveCreateElement(parentPath: String, name: String, type: Int): Long
    external fun nativeSleeveDeleteElement(path: String): Boolean
    external fun nativeSleeveCopyElement(src: String, dest: String): Boolean
    external fun nativeSleeveMoveElement(src: String, dest: String): Boolean
    external fun nativeSleeveListFolder(path: String): String
    external fun nativeSleeveGetPipelineInfo(): String

    fun initialize(): Boolean = nativeSleeveInitialize()
    fun getElement(path: String): String = nativeSleeveGetElement(path)
    fun createElement(parentPath: String, name: String, isFolder: Boolean): Long =
        nativeSleeveCreateElement(parentPath, name, if (isFolder) 2 else 1)
    fun deleteElement(path: String): Boolean = nativeSleeveDeleteElement(path)
    fun copyElement(src: String, dest: String): Boolean = nativeSleeveCopyElement(src, dest)
    fun moveElement(src: String, dest: String): Boolean = nativeSleeveMoveElement(src, dest)
    fun listFolder(path: String): String = nativeSleeveListFolder(path)
}
