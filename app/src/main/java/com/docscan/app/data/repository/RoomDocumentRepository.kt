package com.docscan.app.data.repository

import androidx.room.withTransaction
import com.docscan.app.data.local.AppDatabase
import com.docscan.app.data.local.DocumentEntity
import com.docscan.app.data.local.DocumentSummaryRow
import com.docscan.app.data.local.DocumentWithPages
import com.docscan.app.data.local.PageEntity
import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.Point
import com.docscan.app.domain.model.SignaturePlacement
import com.docscan.app.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDocumentRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentRepository {
    private val dao = database.documentDao()

    override fun observeSummaries(query: String, folderId: Long?): Flow<List<DocumentSummary>> =
        dao.observeSummaries(query, folderId).map { rows -> rows.map { it.toDomain() } }

    override fun observeDocument(id: Long): Flow<Document?> =
        dao.observeDocument(id).map { it?.toDomain() }

    override suspend fun getDocument(id: Long): Document? = dao.getDocument(id)?.toDomain()

    override suspend fun createDocument(title: String, pages: List<NewPage>): Long =
        database.withTransaction {
            val now = clock()
            val id = dao.insertDocument(DocumentEntity(title = title, createdAt = now, updatedAt = now))
            dao.insertPages(pages.mapIndexed { index, page -> page.toEntity(id, index) })
            id
        }

    override suspend fun addPages(documentId: Long, pages: List<NewPage>) =
        database.withTransaction {
            val start = dao.maxPosition(documentId) + 1
            dao.insertPages(pages.mapIndexed { index, page -> page.toEntity(documentId, start + index) })
            dao.touch(documentId, clock())
        }

    override suspend fun renameDocument(id: Long, title: String) = dao.rename(id, title, clock())

    override suspend fun deleteDocument(id: Long) = dao.deleteDocument(id)

    override suspend fun deleteAllDocuments() = dao.deleteAllDocuments()

    override suspend fun moveToFolder(documentIds: List<Long>, folderId: Long?) =
        dao.moveToFolder(documentIds, folderId, clock())

    override suspend fun updatePage(page: Page) = database.withTransaction {
        dao.updatePage(page.toEntity())
        dao.touch(page.documentId, clock())
    }

    override suspend fun deletePage(id: Long) = database.withTransaction {
        val documentId = dao.documentIdOfPage(id) ?: return@withTransaction
        dao.deletePage(id)
        dao.touch(documentId, clock())
    }

    override suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) =
        database.withTransaction {
            orderedPageIds.forEachIndexed { position, pageId -> dao.setPosition(pageId, position) }
            dao.touch(documentId, clock())
        }
}

private fun DocumentSummaryRow.toDomain() = DocumentSummary(id, title, updatedAt, pageCount, coverPath)

private fun DocumentWithPages.toDomain() = Document(
    id = document.id,
    title = document.title,
    createdAt = document.createdAt,
    updatedAt = document.updatedAt,
    pages = pages.sortedBy { it.position }.map { it.toDomain() },
    folderId = document.folderId,
)

private fun PageEntity.toDomain() = Page(
    id = id,
    documentId = documentId,
    position = position,
    originalPath = originalPath,
    processedPath = processedPath,
    adjustments = PageAdjustments(
        filter = runCatching { PageFilter.valueOf(filter) }.getOrDefault(PageFilter.ORIGINAL),
        rotation = rotation,
        crop = crop?.let(::decodeCrop),
        brightness = brightness,
        contrast = contrast,
        signature = if (signaturePath != null && signatureX != null && signatureY != null && signatureWidth != null) {
            SignaturePlacement(signaturePath, signatureX, signatureY, signatureWidth)
        } else {
            null
        },
    ),
    ocrText = ocrText,
)

private fun Page.toEntity() = adjustments.toEntity(id, documentId, position, originalPath, processedPath, ocrText)

private fun NewPage.toEntity(documentId: Long, position: Int) =
    adjustments.toEntity(0, documentId, position, originalPath, processedPath, ocrText)

private fun PageAdjustments.toEntity(
    id: Long,
    documentId: Long,
    position: Int,
    originalPath: String,
    processedPath: String,
    ocrText: String?,
) = PageEntity(
    id = id,
    documentId = documentId,
    position = position,
    originalPath = originalPath,
    processedPath = processedPath,
    filter = filter.name,
    rotation = rotation,
    ocrText = ocrText,
    crop = crop?.let(::encodeCrop),
    brightness = brightness,
    contrast = contrast,
    signaturePath = signature?.imagePath,
    signatureX = signature?.x,
    signatureY = signature?.y,
    signatureWidth = signature?.width,
)

internal fun encodeCrop(crop: CropQuad): String = crop.corners.joinToString(",") { "${it.x},${it.y}" }

/** Unreadable values fall back to no crop rather than failing the whole document. */
internal fun decodeCrop(value: String): CropQuad? {
    val numbers = value.split(',').mapNotNull { it.toFloatOrNull() }
    if (numbers.size != 8) return null
    return CropQuad.of(numbers.chunked(2).map { (x, y) -> Point(x, y) })
}
