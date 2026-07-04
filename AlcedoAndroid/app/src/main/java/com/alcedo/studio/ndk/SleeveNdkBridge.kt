package com.alcedo.studio.ndk

/**
 * JNI bridge for the Sleeve asset management system.
 *
 * Provides native methods for the inode-like virtual file system
 * that manages photos, folders, and metadata in a unified way.
 */
object SleeveNdkBridge {
    init {
        System.loadLibrary("alcedo_core")
    }

    /** Initialize the global SleeveManager and its root filesystem. */
    external fun nativeInitializeSleeve()

    /** Create a folder at the given path with the specified name.
     * @return Element ID, or -1 on failure. */
    external fun nativeCreateFolder(path: String, name: String): Int

    /** Create a file at the given path with the specified name.
     * @return Element ID, or -1 on failure. */
    external fun nativeCreateFile(path: String, name: String): Int

    /** Delete the element at the given path. */
    external fun nativeDeleteElement(path: String): Boolean

    /** Move an element from src to dst path. */
    external fun nativeMoveElement(src: String, dst: String): Boolean

    /** Copy an element from src to dst path. */
    external fun nativeCopyElement(src: String, dst: String): Boolean

    /** List child element IDs of a folder path. */
    external fun nativeListFolder(path: String): IntArray?

    /** Resolve a path and return element metadata as a JSON string. */
    external fun nativeResolvePath(path: String): String
}