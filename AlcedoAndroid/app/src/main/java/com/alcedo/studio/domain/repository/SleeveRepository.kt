package com.alcedo.studio.domain.repository

import com.alcedo.studio.data.model.*
import com.alcedo.studio.data.local.SleeveDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SleeveRepository {
    suspend fun addElement(element: SleeveElement, parentId: UInt? = null)
    suspend fun getElement(id: UInt): SleeveElement?
    suspend fun getChildren(parentId: UInt): List<SleeveElement>
    suspend fun removeElement(id: UInt)
    suspend fun createFolder(name: String, parentId: UInt? = null): SleeveFolder
    suspend fun createFile(name: String, imageId: UInt, parentId: UInt? = null): SleeveFile
}

class SleeveRepositoryImpl(private val db: SleeveDatabase) : SleeveRepository {
    private var nextElementId: UInt = 1u

    override suspend fun addElement(element: SleeveElement, parentId: UInt?) = withContext(Dispatchers.IO) {
        db.insertElement(element, parentId)
    }

    override suspend fun getElement(id: UInt): SleeveElement? = withContext(Dispatchers.IO) {
        db.getElementById(id)
    }

    override suspend fun getChildren(parentId: UInt): List<SleeveElement> = withContext(Dispatchers.IO) {
        db.getChildrenByParentId(parentId)
    }

    override suspend fun removeElement(id: UInt) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("sleeve_elements", "element_id = ?", arrayOf(id.toString()))
    }

    override suspend fun createFolder(name: String, parentId: UInt?): SleeveFolder = withContext(Dispatchers.IO) {
        val id = nextElementId++
        val folder = SleeveFolder(elementId = id, elementName = name)
        db.insertElement(folder, parentId)
        folder
    }

    override suspend fun createFile(name: String, imageId: UInt, parentId: UInt?): SleeveFile = withContext(Dispatchers.IO) {
        val id = nextElementId++
        val file = SleeveFile(elementId = id, elementName = name, imageId = imageId)
        db.insertElement(file, parentId)
        file
    }
}
