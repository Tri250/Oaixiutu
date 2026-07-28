package com.alcedo.studio.utils

import java.util.LinkedHashMap

/**
 * A bounded LRU cache backed by [LinkedHashMap] with access-order eviction.
 * Thread-safe via external synchronisation on the cache instance. Used for
 * decoded-image, thumbnail and embedding caches.
 */
class LruCache<K, V>(
    private val maxSize: Int,
) {
    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    private val map: LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxSize
    }

    private val lock = Any()

    /** Returns the cached value or null. */
    fun get(key: K): V? = synchronized(lock) { map[key] }

    /** Inserts [value] for [key], evicting the least recently used entry if needed. */
    fun put(key: K, value: V): V? = synchronized(lock) { map.put(key, value) }

    fun getOrPut(key: K, default: () -> V): V = synchronized(lock) {
        map[key] ?: default().also { map[key] = it }
    }

    fun remove(key: K): V? = synchronized(lock) { map.remove(key) }

    fun contains(key: K): Boolean = synchronized(lock) { map.containsKey(key) }

    fun size(): Int = synchronized(lock) { map.size }

    fun clear() = synchronized(lock) { map.clear() }

    fun keys(): Set<K> = synchronized(lock) { LinkedHashSet(map.keys) }

    fun snapshot(): Map<K, V> = synchronized(lock) { LinkedHashMap(map) }
}

/**
 * A weighted LRU cache used for byte-budgeted caches (e.g. bitmaps where each
 * entry has an estimated size in bytes). Eviction is driven by [sizeOf].
 */
class WeightedLruCache<K, V>(
    private val maxWeight: Long,
    private val sizeOf: (K, V) -> Long,
) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var currentWeight: Long = 0L
    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) {
        map[key]?.also { /* access-order updated by LinkedHashMap */ }
    }

    fun put(key: K, value: V) {
        synchronized(lock) {
            val previous = map.remove(key)
            if (previous != null) currentWeight -= sizeOf(key, previous)
            map[key] = value
            currentWeight += sizeOf(key, value)
            trimToSize()
        }
    }

    fun remove(key: K): V? = synchronized(lock) {
        map.remove(key)?.also { currentWeight -= sizeOf(key, it) }
    }

    fun weight(): Long = synchronized(lock) { currentWeight }

    fun size(): Int = synchronized(lock) { map.size }

    fun clear() = synchronized(lock) {
        map.clear()
        currentWeight = 0L
    }

    private fun trimToSize() {
        val iter = map.entries.iterator()
        while (iter.hasNext() && currentWeight > maxWeight) {
            val (k, v) = iter.next()
            iter.remove()
            currentWeight -= sizeOf(k, v)
        }
    }
}
