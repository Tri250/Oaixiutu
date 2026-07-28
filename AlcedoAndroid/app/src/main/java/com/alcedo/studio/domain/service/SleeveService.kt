package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.data.local.DentryCacheManager
import com.alcedo.studio.data.model.SleeveElement
import com.alcedo.studio.data.model.SleeveFile
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.data.model.SleeveTree
import com.alcedo.studio.domain.repository.SleeveRepository
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleeve filesystem service. Coordinates the native DuckDB sleeve (the source of
 * truth) with the Room mirror exposed by [SleeveRepository], and maintains the
 * [DentryCacheManager] for fast folder listings.
 */
@Singleton
class SleeveService @Inject constructor(
    private val sleeveRepository: SleeveRepository,
    private val dentryCache: DentryCacheManager,
) {

    private var nativeHandle: Long = 0L

    val tree: Flow<SleeveTree> get() = sleeveRepository.observeTree()

    /** Open (or create) the native DuckDB sleeve at [dbPath]. */
    suspend fun open(dbPath: String): Boolean = withContext(ThreadPool.database) {
        close()
        nativeHandle = NdkSafeCall.handle {
            AlcedoNativeBridge.nativeSleeveOpen(dbPath)
        }
        dentryCache.clear()
        nativeHandle != 0L
    }

    fun close() {
        if (nativeHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeSleeveClose(nativeHandle) }
            nativeHandle = 0L
        }
        dentryCache.clear()
    }

    /** List children of [folderPath], using the dentry cache. */
    suspend fun listChildren(folderPath: String): List<SleeveElement> = withContext(ThreadPool.database) {
        dentryCache.get(folderPath)?.let { return@withContext it }
        val children = sleeveRepository.listChildren(folderPath)
        dentryCache.put(folderPath, children)
        children
    }

    suspend fun createFolder(parentPath: String, name: String): SleeveFolder = withContext(ThreadPool.database) {
        if (nativeHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeSleeveCreateFolder(nativeHandle, parentPath, name) }
        }
        val folder = sleeveRepository.createFolder(parentPath, name)
        dentryCache.invalidateTree(parentPath)
        folder
    }

    suspend fun move(srcPath: String, destPath: String): Boolean = withContext(ThreadPool.database) {
        if (nativeHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeSleeveMoveElement(nativeHandle, srcPath, destPath) }
        }
        val ok = sleeveRepository.moveElement(srcPath, destPath)
        if (ok) {
            dentryCache.invalidateTree(PathResolver_parent(srcPath))
            dentryCache.invalidateTree(PathResolver_parent(destPath))
        }
        ok
    }

    suspend fun delete(path: String): Boolean = withContext(ThreadPool.database) {
        if (nativeHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeSleeveDeleteElement(nativeHandle, path) }
        }
        val ok = sleeveRepository.deleteElement(path)
        if (ok) dentryCache.invalidateTree(PathResolver_parent(path))
        ok
    }

    suspend fun countFolders(): Int = sleeveRepository.countFolders()
    suspend fun countFiles(): Int = sleeveRepository.countFiles()

    /** Resolve a logical sleeve path to a physical file path. */
    fun resolvePath(logicalPath: String): String? {
        if (nativeHandle == 0L) return null
        return NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeSleeveResolvePath(nativeHandle, logicalPath) }
    }

    private fun PathResolver_parent(path: String): String =
        com.alcedo.studio.data.local.PathResolver.parent(path)

    companion object {
        private const val TAG = "SleeveService"

        /** Default location for the native sleeve database. */
        fun defaultDbPath(): String =
            java.io.File(ContextProvider.requireContext().filesDir, "sleeve.alcd-db").absolutePath
    }
}
