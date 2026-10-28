package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.MAX_FOLDER_NAME_LENGTH
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.repository.FolderRepository

class ObserveFoldersUseCase(private val repository: FolderRepository) {
    operator fun invoke() = repository.observeFolders()
}

class CreateFolderUseCase(private val repository: FolderRepository) {
    /** Returns the new folder id, or null when the name is blank. */
    suspend operator fun invoke(name: String): Long? =
        cleanFolderName(name)?.let { repository.createFolder(it) }
}

class RenameFolderUseCase(private val repository: FolderRepository) {
    suspend operator fun invoke(id: Long, name: String) {
        cleanFolderName(name)?.let { repository.renameFolder(id, it) }
    }
}

class DeleteFolderUseCase(private val repository: FolderRepository) {
    /** The folder's documents are kept; they become unfiled. */
    suspend operator fun invoke(id: Long) = repository.deleteFolder(id)
}

class MoveDocumentsUseCase(private val repository: DocumentRepository) {
    /** [folderId] null takes the documents out of any folder. */
    suspend operator fun invoke(documentIds: List<Long>, folderId: Long?) {
        if (documentIds.isNotEmpty()) repository.moveToFolder(documentIds, folderId)
    }
}

internal fun cleanFolderName(name: String): String? =
    name.trim().take(MAX_FOLDER_NAME_LENGTH).takeIf { it.isNotEmpty() }
