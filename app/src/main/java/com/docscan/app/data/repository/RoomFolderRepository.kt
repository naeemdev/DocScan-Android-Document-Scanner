package com.docscan.app.data.repository

import com.docscan.app.data.local.AppDatabase
import com.docscan.app.data.local.FolderEntity
import com.docscan.app.domain.model.Folder
import com.docscan.app.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFolderRepository(
    database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : FolderRepository {
    private val dao = database.folderDao()

    override fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { rows -> rows.map { Folder(it.id, it.name, it.documentCount) } }

    override suspend fun createFolder(name: String): Long = dao.insert(FolderEntity(name = name, createdAt = clock()))

    override suspend fun renameFolder(id: Long, name: String) = dao.rename(id, name)

    // The documents' foreign key is ON DELETE SET NULL, so they stay and become unfiled.
    override suspend fun deleteFolder(id: Long) = dao.delete(id)

    override suspend fun deleteAllFolders() = dao.deleteAll()
}
