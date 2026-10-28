package com.docscan.app.domain.repository

import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    /**
     * Newest first. A blank [query] returns everything; otherwise matches title or recognised text.
     * A null [folderId] means every folder.
     */
    fun observeSummaries(query: String, folderId: Long?): Flow<List<DocumentSummary>>
    fun observeDocument(id: Long): Flow<Document?>
    suspend fun getDocument(id: Long): Document?
    suspend fun createDocument(title: String, pages: List<NewPage>): Long
    suspend fun addPages(documentId: Long, pages: List<NewPage>)
    suspend fun renameDocument(id: Long, title: String)
    suspend fun deleteDocument(id: Long)
    suspend fun deleteAllDocuments()
    suspend fun moveToFolder(documentIds: List<Long>, folderId: Long?)
    suspend fun updatePage(page: Page)
    suspend fun deletePage(id: Long)
    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>)
}
