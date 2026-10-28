package com.docscan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    // shortcut: LIKE scan over OCR text, switch to an FTS4 table past ~10k pages.
    @Query(
        """
        SELECT d.id, d.title, d.updatedAt,
            (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
            (SELECT p.processedPath FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverPath
        FROM documents d
        WHERE (:folderId IS NULL OR d.folderId = :folderId)
            AND (
                :query = ''
                OR d.title LIKE '%' || :query || '%'
                OR EXISTS (SELECT 1 FROM pages p WHERE p.documentId = d.id AND p.ocrText LIKE '%' || :query || '%')
            )
        ORDER BY d.updatedAt DESC
        """,
    )
    fun observeSummaries(query: String, folderId: Long?): Flow<List<DocumentSummaryRow>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: Long): Flow<DocumentWithPages?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: Long): DocumentWithPages?

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("UPDATE documents SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("UPDATE documents SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    @Query("UPDATE documents SET folderId = :folderId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?, now: Long)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    @Query("SELECT documentId FROM pages WHERE id = :id")
    suspend fun documentIdOfPage(id: Long): Long?

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE documentId = :documentId")
    suspend fun maxPosition(documentId: Long): Int

    @Query("UPDATE pages SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
