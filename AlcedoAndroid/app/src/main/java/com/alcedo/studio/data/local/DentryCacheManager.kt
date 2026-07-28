package com.alcedo.studio.data.local

import com.alcedo.studio.data.model.SleeveElement
import com.alcedo.studio.data.model.SleeveFile
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.utils.LruCache
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * In-memory directory-entry cache for the sleeve tree. Caches the children of
 * frequently visited folders so album browsing and the folder tree sidebar
 * don't re-query the native DuckDB layer on every recomposition. Mirrors the
 * desktop core/sleeve/dentry_cache_manager.
 *
 * The cache is bounded by entry count and invalidated per-folder when the
 * sleeve mutates (import, move, delete).
 */
class DentryCacheManager(
    maxFolders: Int = 256,
    private val maxAgeMs: Long = 60_000L,
) {
    private data class Entry(
        val children: List<SleeveElement>,
        val cachedAt: Long,
    )

    private val cache = LruCache<String, Entry>(maxFolders)
    private val lock = ReentrantReadWriteLock()
    private val invalidationListeners = mutableListOf<(String) -> Unit>()

    /** Return cached children for [folderPath] or null if absent/stale. */
    fun get(folderPath: String): List<SleeveElement>? = lock.read {
        val key = PathResolver.normalise(folderPath)
        val entry = cache.get(key) ?: return@read null
        if (System.currentTimeMillis() - entry.cachedAt > maxAgeMs) {
            null
        } else {
            entry.children
        }
    }

    /** Store [children] for [folderPath]. */
    fun put(folderPath: String, children: List<SleeveElement>): Unit = lock.write {
        val key = PathResolver.normalise(folderPath)
        cache.put(key, Entry(children, System.currentTimeMillis()))
    }

    /** Invalidate a single folder's children. */
    fun invalidate(folderPath: String) = lock.write {
        val key = PathResolver.normalise(folderPath)
        cache.remove(key)
        invalidationListeners.forEach { it(key) }
    }

    /** Invalidate [folderPath] and all descendants (used after move/delete). */
    fun invalidateTree(folderPath: String) = lock.write {
        val key = PathResolver.normalise(folderPath)
        val toRemove = cache.keys().filter { PathResolver.isDescendant(key, it) }
        toRemove.forEach { cache.remove(it) }
        invalidationListeners.forEach { it(key) }
    }

    fun clear() = lock.write { cache.clear() }

    /** Register a listener invoked when a folder is invalidated. */
    fun observeInvalidation(listener: (String) -> Unit) = lock.write {
        invalidationListeners.add(listener)
    }

    /** Number of folders currently cached. */
    fun size(): Int = lock.read { cache.size() }

    /** Snapshot counts of files vs folders within a cached listing. */
    fun countTypes(folderPath: String): Pair<Int, Int>? {
        val children = get(folderPath) ?: return null
        val folders = children.count { it is SleeveFolder }
        val files = children.count { it is SleeveFile }
        return folders to files
    }
}
