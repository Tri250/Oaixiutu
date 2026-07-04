package com.alcedo.studio.data.model

import java.time.Instant

data class SleeveFolder(
    override val elementId: UInt,
    override val elementName: String,
    val contents: MutableMap<String, UInt> = mutableMapOf(),
    val indicesCache: MutableMap<UInt, List<UInt>> = mutableMapOf(),
    val defaultFilterId: UInt = 0u,
    var fileCount: UInt = 0u,
    var folderCount: UInt = 0u,
    var childrenLoaded: Boolean = false,
    override val addedTime: Instant = Instant.now(),
    override val lastModifiedTime: Instant = Instant.now(),
    override val refCount: UInt = 0u,
    override val pinned: Boolean = false,
    override val syncFlag: SyncFlag = SyncFlag.UNSYNC
) : SleeveElement(elementId, elementName, ElementType.FOLDER, addedTime, lastModifiedTime, refCount, pinned, syncFlag) {

    override fun copyWithId(newId: UInt): SleeveElement = copy(elementId = newId)

    override fun clear(): Boolean {
        contents.clear()
        indicesCache.clear()
        childrenLoaded = false
        return true
    }

    fun addElement(element: SleeveElement, changeSync: Boolean = true, incrementRef: Boolean = true) {
        contents[element.elementName.toString()] = element.elementId
        if (element is SleeveFile) {
            fileCount++
        } else if (element is SleeveFolder) {
            folderCount++
        }
        if (changeSync) {
            // syncFlag = SyncFlag.MODIFIED
        }
    }

    fun getElementIdByName(name: String): UInt? = contents[name]

    fun listElements(): List<UInt> = contents.values.toList()

    fun listElementsByFilter(filterId: UInt): List<UInt> = indicesCache[filterId] ?: emptyList()

    fun containsElementId(elementId: UInt): Boolean = contents.containsValue(elementId)

    fun removeElementById(elementId: UInt): Boolean {
        val entry = contents.entries.find { it.value == elementId }
        return if (entry != null) {
            contents.remove(entry.key)
            true
        } else false
    }

    fun createIndex(filteredElements: List<SleeveElement>, filterId: UInt) {
        indicesCache[filterId] = filteredElements.map { it.elementId }
    }

    fun contentSize(): Int = contents.size
}
