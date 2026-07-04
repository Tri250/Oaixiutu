package com.alcedo.studio.utils

import java.util.LinkedHashMap

class LruCache<K, V>(private val maxSize: Int) {
    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }
    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) { cache[key] }

    fun put(key: K, value: V): V? = synchronized(lock) { cache.put(key, value) }

    fun remove(key: K): V? = synchronized(lock) { cache.remove(key) }

    fun clear() = synchronized(lock) { cache.clear() }

    fun size(): Int = synchronized(lock) { cache.size }

    fun contains(key: K): Boolean = synchronized(lock) { cache.containsKey(key) }

    fun evict(key: K, value: V): Boolean = synchronized(lock) {
        if (cache[key] == value) {
            cache.remove(key)
            true
        } else false
    }
}
