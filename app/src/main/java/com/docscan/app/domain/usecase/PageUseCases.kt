package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.SignaturePlacement
import com.docscan.app.domain.model.isUsable
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.service.FileStore
import com.docscan.app.domain.service.ImageProcessor

class EditPageUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
    private val imageProcessor: ImageProcessor,
) {
    suspend operator fun invoke(page: Page, adjustments: PageAdjustments) {
        val clean = adjustments.copy(
            brightness = adjustments.brightness.coerceIn(-1f, 1f),
            contrast = adjustments.contrast.coerceIn(MIN_CONTRAST, MAX_CONTRAST),
        )
        // A new path (not overwrite) so anything showing the old image reloads.
        val output = fileStore.newImagePath()
        try {
            imageProcessor.render(page.originalPath, clean, output)
        } catch (e: Exception) {
            fileStore.delete(listOf(output))
            throw e
        }
        repository.updatePage(page.copy(processedPath = output, adjustments = clean))
        val oldSignature = page.adjustments.signature?.imagePath
        fileStore.delete(listOfNotNull(page.processedPath, oldSignature.takeIf { it != clean.signature?.imagePath }))
    }

    companion object {
        const val MIN_CONTRAST = 0.5f
        const val MAX_CONTRAST = 2f
    }
}

class RotatePageUseCase(private val editPage: EditPageUseCase) {
    suspend operator fun invoke(page: Page) =
        editPage(page, page.adjustments.copy(rotation = (page.adjustments.rotation + 90) % 360))
}

class CropPageUseCase(private val editPage: EditPageUseCase) {
    /** [crop] null resets to the full image. */
    suspend operator fun invoke(page: Page, crop: CropQuad?) {
        require(crop == null || crop.isUsable()) { "Crop area is too small or twisted" }
        editPage(page, page.adjustments.copy(crop = crop?.takeUnless { it == CropQuad.FULL }))
    }
}

class MovePageUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(document: Document, from: Int, to: Int) {
        if (from == to || from !in document.pages.indices || to !in document.pages.indices) return
        repository.reorderPages(document.id, document.pages.map { it.id }.moved(from, to))
    }
}

class DeletePageUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
    private val deleteDocument: DeleteDocumentUseCase,
) {
    /** Deleting the only page deletes the document. Returns true when the document is gone. */
    suspend operator fun invoke(page: Page): Boolean {
        val document = repository.getDocument(page.documentId) ?: return true
        if (document.pages.size <= 1) {
            deleteDocument(document.id)
            return true
        }
        repository.deletePage(page.id)
        fileStore.delete(page.ownedFiles)
        return false
    }
}

class PlaceSignatureUseCase(
    private val fileStore: FileStore,
    private val editPage: EditPageUseCase,
    private val requirePro: RequireProUseCase,
) {
    /** Stamps a copy of the saved signature at [savedSignaturePath] on the page; the page owns the copy. */
    suspend operator fun invoke(page: Page, savedSignaturePath: String, x: Float, y: Float, width: Float) {
        requirePro()
        val clampedWidth = width.coerceIn(MIN_SIGNATURE_WIDTH, 1f)
        val copy = fileStore.copy(savedSignaturePath)
        val placement = SignaturePlacement(
            imagePath = copy,
            x = x.coerceIn(0f, 1f - clampedWidth),
            y = y.coerceIn(0f, 1f),
            width = clampedWidth,
        )
        try {
            editPage(page, page.adjustments.copy(signature = placement))
        } catch (e: Exception) {
            fileStore.delete(listOf(copy))
            throw e
        }
    }

    companion object {
        const val MIN_SIGNATURE_WIDTH = 0.08f
    }
}

class RemoveSignatureUseCase(private val editPage: EditPageUseCase) {
    suspend operator fun invoke(page: Page) {
        if (page.adjustments.signature != null) editPage(page, page.adjustments.copy(signature = null))
    }
}

internal fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }
