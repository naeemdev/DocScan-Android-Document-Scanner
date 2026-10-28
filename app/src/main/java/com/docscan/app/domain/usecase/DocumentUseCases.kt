package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.MAX_TITLE_LENGTH
import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.repository.FolderRepository
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.service.DocumentNamer
import com.docscan.app.domain.service.FileStore
import com.docscan.app.domain.service.IdCardComposer
import com.docscan.app.domain.service.ImageProcessor
import com.docscan.app.domain.service.PdfRasterizer
import com.docscan.app.domain.service.SignatureStore
import com.docscan.app.domain.service.TextRecognizer

class ObserveDocumentsUseCase(private val repository: DocumentRepository) {
    operator fun invoke(query: String, folderId: Long?) = repository.observeSummaries(query.trim(), folderId)
}

class ObserveDocumentUseCase(private val repository: DocumentRepository) {
    operator fun invoke(id: Long) = repository.observeDocument(id)
}

/** Turns stored original images into pages and saves them. Every import path ends here. */
class SavePagesUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
    private val imageProcessor: ImageProcessor,
    private val settings: SettingsRepository,
    private val namer: DocumentNamer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Takes ownership of [originals]: on failure they are deleted. Returns the document id. */
    suspend operator fun invoke(
        originals: List<String>,
        intoDocumentId: Long? = null,
        filter: PageFilter = settings.settings.value.defaultFilter,
    ): Long {
        require(originals.isNotEmpty()) { "Nothing to import" }
        val created = mutableListOf<String>()
        try {
            val adjustments = PageAdjustments(filter = filter)
            val pages = originals.map { original ->
                val processed = fileStore.newImagePath().also(created::add)
                imageProcessor.render(original, adjustments, processed)
                NewPage(original, processed, adjustments)
            }
            if (intoDocumentId != null) {
                repository.addPages(intoDocumentId, pages)
                return intoDocumentId
            }
            return repository.createDocument(namer.newScanTitle(clock()), pages)
        } catch (e: Exception) {
            // No half-imported files left behind on failure or cancellation.
            fileStore.delete(originals + created)
            throw e
        }
    }
}

class ImportScanUseCase(
    private val fileStore: FileStore,
    private val savePages: SavePagesUseCase,
) {
    /** Adds scanned images to [intoDocumentId], or to a new document when null. Returns the document id. */
    suspend operator fun invoke(sourceUris: List<String>, intoDocumentId: Long? = null): Long =
        savePages(fileStore.importAll(sourceUris), intoDocumentId)
}

class ImportPdfUseCase(
    private val rasterizer: PdfRasterizer,
    private val fileStore: FileStore,
    private val savePages: SavePagesUseCase,
) {
    /** A PDF is already digital, so its pages keep their look (no filter). */
    suspend operator fun invoke(sourceUri: String): Long =
        savePages(rasterizer.renderPages(sourceUri, fileStore::newImagePath), filter = PageFilter.ORIGINAL)
}

class CreateIdCardUseCase(
    private val fileStore: FileStore,
    private val composer: IdCardComposer,
    private val savePages: SavePagesUseCase,
) {
    suspend operator fun invoke(frontUri: String, backUri: String): Long {
        val sides = fileStore.importAll(listOf(frontUri, backUri))
        val output = fileStore.newImagePath()
        try {
            composer.compose(sides[0], sides[1], output)
        } catch (e: Exception) {
            fileStore.delete(listOf(output))
            throw e
        } finally {
            fileStore.delete(sides)
        }
        return savePages(listOf(output))
    }
}

class MergeDocumentsUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
    private val namer: DocumentNamer,
) {
    /** Creates a new document with the pages of [ids] in that order; the sources are kept. Returns the new id. */
    suspend operator fun invoke(ids: List<Long>): Long {
        val documents = ids.mapNotNull { repository.getDocument(it) }
        require(documents.size >= 2) { "Need at least two documents" }
        val created = mutableListOf<String>()
        suspend fun copy(path: String) = fileStore.copy(path).also(created::add)
        try {
            val pages = documents.flatMap { it.pages }.map { page ->
                val signature = page.adjustments.signature
                NewPage(
                    originalPath = copy(page.originalPath),
                    processedPath = copy(page.processedPath),
                    adjustments = page.adjustments.copy(signature = signature?.copy(imagePath = copy(signature.imagePath))),
                    ocrText = page.ocrText,
                )
            }
            return repository.createDocument(namer.mergedTitle(documents.first().title, documents.size - 1), pages)
        } catch (e: Exception) {
            fileStore.delete(created)
            throw e
        }
    }
}

class RenameDocumentUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(id: Long, title: String) {
        val clean = title.trim().take(MAX_TITLE_LENGTH)
        if (clean.isNotEmpty()) repository.renameDocument(id, clean)
    }
}

class DeleteDocumentUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
) {
    suspend operator fun invoke(id: Long) {
        val document = repository.getDocument(id) ?: return
        repository.deleteDocument(id)
        fileStore.delete(document.pages.flatMap { it.ownedFiles })
    }
}

class DeleteAllDataUseCase(
    private val documents: DocumentRepository,
    private val folders: FolderRepository,
    private val fileStore: FileStore,
    private val signatures: SignatureStore,
) {
    suspend operator fun invoke() {
        documents.deleteAllDocuments()
        folders.deleteAllFolders()
        fileStore.clearAll()
        signatures.clearAll()
    }
}

class RecognizeDocumentTextUseCase(
    private val repository: DocumentRepository,
    private val recognizer: TextRecognizer,
) {
    /** Text per page, in page order. Results are cached on the page so search can find them. */
    suspend operator fun invoke(documentId: Long): List<String> {
        val document = repository.getDocument(documentId) ?: return emptyList()
        return document.pages.map { page ->
            page.ocrText ?: recognizer.recognize(page.processedPath).also {
                repository.updatePage(page.copy(ocrText = it))
            }
        }
    }
}

/** Copies all [uris] into storage, or none of them. */
private suspend fun FileStore.importAll(uris: List<String>): List<String> {
    val imported = mutableListOf<String>()
    try {
        uris.forEach { imported += importImage(it) }
    } catch (e: Exception) {
        delete(imported)
        throw e
    }
    return imported
}

internal fun safeFileName(title: String): String =
    title.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(80).ifEmpty { "document" }
