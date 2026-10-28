package com.docscan.app.domain.repository

import com.docscan.app.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    /** Alphabetical, with document counts. */
    fun observeFolders(): Flow<List<Folder>>
    suspend fun createFolder(name: String): Long
    suspend fun renameFolder(id: Long, name: String)
    /** Documents in the folder are kept and become unfiled. */
    suspend fun deleteFolder(id: Long)
    suspend fun deleteAllFolders()
}
