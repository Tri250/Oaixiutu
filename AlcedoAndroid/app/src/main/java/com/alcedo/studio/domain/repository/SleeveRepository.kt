package com.alcedo.studio.domain.repository

import com.alcedo.studio.data.model.SleeveElement
import com.alcedo.studio.data.model.SleeveFile
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.data.model.SleeveTree
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the sleeve (virtual project filesystem). Implemented by
 * [com.alcedo.studio.data.repository.SleeveRepositoryImpl], which delegates the
 * authoritative tree to the native DuckDB sleeve and mirrors it into Room.
 */
interface SleeveRepository {

    fun observeChildren(folderPath: String): Flow<List<SleeveElement>>
    fun observeTree(): Flow<SleeveTree>

    suspend fun listChildren(folderPath: String): List<SleeveElement>
    suspend fun getTree(): SleeveTree
    suspend fun getFolder(folderPath: String): SleeveFolder?
    suspend fun getFile(sleevePath: String): SleeveFile?

    suspend fun createFolder(parentPath: String, name: String): SleeveFolder
    suspend fun importFile(parentPath: String, uri: String, name: String): SleeveFile
    suspend fun moveElement(srcPath: String, destPath: String): Boolean
    suspend fun deleteElement(path: String): Boolean
    suspend fun renameElement(path: String, newName: String): SleeveElement?

    suspend fun countFolders(): Int
    suspend fun countFiles(): Int
}
