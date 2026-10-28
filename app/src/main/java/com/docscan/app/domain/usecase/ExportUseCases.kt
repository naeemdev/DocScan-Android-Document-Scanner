package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.ExportFormat
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.repository.EntitlementRepository
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.service.FileStore
import com.docscan.app.domain.service.PdfExporter
import com.docscan.app.domain.service.Watermarker

class ExportDocumentUseCase(
    private val pdfExporter: PdfExporter,
    private val watermarker: Watermarker,
    private val fileStore: FileStore,
    private val settings: SettingsRepository,
    private val entitlements: EntitlementRepository,
) {
    /** Returns paths of files ready to share. */
    suspend operator fun invoke(document: Document, format: ExportFormat): List<String> {
        val current = settings.settings.value
        // The watermark is a Pro feature; a lapsed subscription simply stops stamping it.
        val watermark = current.watermarkText.trim().takeIf { it.isNotEmpty() && entitlements.isPro.value }
        val name = safeFileName(document.title)
        fileStore.clearExports()
        return when (format) {
            ExportFormat.PDF -> {
                val output = fileStore.newExportPath("$name.pdf")
                pdfExporter.export(document.pages, current.pageSize, current.pdfQuality, watermark, output)
                listOf(output)
            }
            ExportFormat.JPG -> if (watermark == null) {
                document.pages.map { it.processedPath }
            } else {
                document.pages.mapIndexed { index, page ->
                    fileStore.newExportPath("$name-${index + 1}.jpg").also { watermarker.apply(page.processedPath, watermark, it) }
                }
            }
        }
    }
}

class ExportTextUseCase(
    private val repository: DocumentRepository,
    private val fileStore: FileStore,
) {
    /** Writes [text] to "<document title>.txt" and returns its path. */
    suspend operator fun invoke(documentId: Long, text: String): String {
        val title = repository.getDocument(documentId)?.title.orEmpty()
        return fileStore.writeText(safeFileName(title) + ".txt", text)
    }
}
