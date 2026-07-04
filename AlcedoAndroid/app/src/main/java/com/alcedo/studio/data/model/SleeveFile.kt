package com.alcedo.studio.data.model

import java.time.Instant

data class SleeveFile(
    override val elementId: UInt,
    override val elementName: String,
    val imageId: UInt,
    val image: ImageModel? = null,
    val editHistory: EditHistory? = null,
    val currentVersionId: String? = null,
    override val addedTime: Instant = Instant.now(),
    override val lastModifiedTime: Instant = Instant.now(),
    override val refCount: UInt = 0u,
    override val pinned: Boolean = false,
    override val syncFlag: SyncFlag = SyncFlag.UNSYNC
) : SleeveElement(elementId, elementName, ElementType.FILE, addedTime, lastModifiedTime, refCount, pinned, syncFlag) {

    override fun copyWithId(newId: UInt): SleeveElement = copy(elementId = newId)

    override fun clear(): Boolean {
        image?.clearData()
        return true
    }
}
