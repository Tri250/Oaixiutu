package com.alcedo.studio.data.local

import com.alcedo.studio.data.model.SleeveConstants
import com.alcedo.studio.data.model.SleeveFile
import com.alcedo.studio.data.model.SleeveFolder

/**
 * Resolves logical sleeve paths to parent/child components and back, matching
 * the desktop core/sleeve/path_resolver. Sleeve paths use '/' as the separator
 * and are always absolute (rooted at '/').
 */
object PathResolver {

    const val SEPARATOR = SleeveConstants.PATH_SEPARATOR
    const val ROOT = SleeveConstants.ROOT_PATH

    /** Normalise a path: ensure leading '/', collapse duplicate separators, strip trailing '/'. */
    fun normalise(path: String): String {
        if (path.isBlank()) return ROOT
        var p = path.trim()
        if (!p.startsWith(SEPARATOR)) p = SEPARATOR + p
        while (p.contains("//")) p = p.replace("//", SEPARATOR)
        if (p.length > 1 && p.endsWith(SEPARATOR)) p = p.dropLast(1)
        return p.ifBlank { ROOT }
    }

    /** The parent path of [path], or [ROOT] if [path] is the root. */
    fun parent(path: String): String {
        val p = normalise(path)
        if (p == ROOT) return ROOT
        val idx = p.lastIndexOf(SEPARATOR)
        return if (idx <= 0) ROOT else p.substring(0, idx)
    }

    /** The leaf name of [path], or "/" for the root. */
    fun name(path: String): String {
        val p = normalise(path)
        if (p == ROOT) return SEPARATOR
        return p.substring(p.lastIndexOf(SEPARATOR) + 1)
    }

    /** Join [parent] and [child] into a normalised path. */
    fun join(parent: String, child: String): String {
        val p = normalise(parent)
        val c = child.trim().trim(SEPARATOR.first())
        if (c.isEmpty()) return p
        return if (p == ROOT) SEPARATOR + c else "$p$SEPARATOR$c"
    }

    /** Split [path] into its path components, excluding the empty root token. */
    fun components(path: String): List<String> {
        val p = normalise(path)
        if (p == ROOT) return emptyList()
        return p.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    /** Depth of [path]: root = 0, /A = 1, /A/B = 2, ... */
    fun depth(path: String): Int = components(path).size

    /** True if [candidate] is the same as or a descendant of [ancestor]. */
    fun isDescendant(ancestor: String, candidate: String): Boolean {
        val a = normalise(ancestor)
        val c = normalise(candidate)
        if (a == ROOT) return true
        if (a == c) return true
        return c.startsWith(a + SEPARATOR)
    }

    /** The relative path from [ancestor] to [descendant], or null if not a descendant. */
    fun relativePath(ancestor: String, descendant: String): String? {
        val a = normalise(ancestor)
        val d = normalise(descendant)
        if (!isDescendant(a, d)) return null
        if (a == d) return ""
        val rel = if (a == ROOT) d.removePrefix(SEPARATOR) else d.removePrefix(a + SEPARATOR)
        return rel
    }

    /** Build a sleeve folder path for a folder object. */
    fun folderPath(folder: SleeveFolder): String = normalise(folder.sleevePath)

    /** Build a sleeve file path for a file object. */
    fun filePath(file: SleeveFile): String = normalise(file.sleevePath)

    /** A file-system-safe display name derived from a path. */
    fun displayName(path: String): String {
        val n = name(path)
        return if (n == SEPARATOR) "Root" else n
    }
}
