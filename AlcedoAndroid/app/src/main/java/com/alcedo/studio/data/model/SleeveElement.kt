package com.alcedo.studio.data.model

import java.time.Instant

enum class ElementType {
    FILE, FOLDER
}

enum class SyncFlag {
    UNSYNC, MODIFIED, SYNCED, DELETED
}

sealed class SleeveElement(
    open val elementId: UInt,
    open val elementName: String,
    open val type: ElementType,
    open val addedTime: Instant = Instant.now(),
    open val lastModifiedTime: Instant = Instant.now(),
    open val refCount: UInt = 0u,
    open val pinned: Boolean = false,
    open val syncFlag: SyncFlag = SyncFlag.UNSYNC
) {
    abstract fun copyWithId(newId: UInt): SleeveElement
    abstract fun clear(): Boolean
}
