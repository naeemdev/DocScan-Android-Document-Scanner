package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.AppSettings
import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality
import com.docscan.app.domain.model.Plan
import com.docscan.app.domain.model.PurchaseResult
import com.docscan.app.domain.repository.DocumentRepository
import com.docscan.app.domain.repository.EntitlementRepository
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.service.DocumentNamer
import com.docscan.app.domain.service.FileStore
import com.docscan.app.domain.service.ImageProcessor
import com.docscan.app.domain.service.PdfExporter
import com.docscan.app.domain.service.Watermarker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** In-memory file store that tracks which files exist, so tests can assert nothing leaks. */
class FakeFiles : FileStore {
    val live = mutableSetOf<String>()
    private var next = 0
    override suspend fun importImage(sourceUri: String) = newImagePath().also { live += it }
    override fun newImagePath() = "f${next++}"
    override suspend fun copy(path: String) = newImagePath().also { live += it }
    override fun newExportPath(fileName: String) = "export/$fileName"
    override suspend fun writeText(fileName: String, text: String) = newExportPath(fileName).also { live += it }
    override suspend fun delete(paths: List<String>) {
        live -= paths.toSet()
    }
    override suspend fun clearExports() {
        live.removeAll { it.startsWith("export/") }
    }
    override suspend fun clearAll() = live.clear()
}

class FakeRepo(var failCreate: Boolean = false) : DocumentRepository {
    val docs = mutableMapOf<Long, Document>()
    override fun observeSummaries(query: String, folderId: Long?): Flow<List<DocumentSummary>> = emptyFlow()
    override fun observeDocument(id: Long): Flow<Document?> = emptyFlow()
    override suspend fun getDocument(id: Long) = docs[id]
    override suspend fun createDocument(title: String, pages: List<NewPage>): Long {
        check(!failCreate) { "disk full" }
        val id = docs.size + 1L
        docs[id] = Document(id, title, 0, 0, pages.mapIndexed { i, p ->
            Page(id * 100 + i, id, i, p.originalPath, p.processedPath, p.adjustments, p.ocrText)
        })
        return id
    }
    override suspend fun addPages(documentId: Long, pages: List<NewPage>) = Unit
    override suspend fun renameDocument(id: Long, title: String) = Unit
    override suspend fun deleteDocument(id: Long) {
        docs.remove(id)
    }
    override suspend fun deleteAllDocuments() = docs.clear()
    override suspend fun moveToFolder(documentIds: List<Long>, folderId: Long?) = Unit
    override suspend fun updatePage(page: Page) {
        val doc = docs.getValue(page.documentId)
        docs[doc.id] = doc.copy(pages = doc.pages.map { if (it.id == page.id) page else it })
    }
    override suspend fun deletePage(id: Long) = Unit
    override suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = Unit
}

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override fun update(settings: AppSettings) {
        this.settings.value = settings
    }
}

class FakeEntitlements(pro: Boolean) : EntitlementRepository {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(pro)
    override val plans: StateFlow<List<Plan>?> = MutableStateFlow(emptyList())
    override suspend fun refresh() = Unit
    override suspend fun purchase(planId: String) = PurchaseResult.FAILED
    override fun setDebugPro(enabled: Boolean) = Unit
}

object FakeNamer : DocumentNamer {
    override fun newScanTitle(epochMillis: Long) = "new"
    override fun mergedTitle(firstTitle: String, otherCount: Int) = "$firstTitle+$otherCount"
}

fun recordingProcessor(files: FakeFiles) = object : ImageProcessor {
    override suspend fun render(sourcePath: String, adjustments: PageAdjustments, outputPath: String) {
        files.live += outputPath
    }
}

/** Records what the exporter was asked to do. */
class RecordingPdf : PdfExporter {
    var watermark: String? = "unset"
    override suspend fun export(pages: List<Page>, pageSize: PageSize, quality: PdfQuality, watermark: String?, outputPath: String) {
        this.watermark = watermark
    }
}

class RecordingWatermarker : Watermarker {
    val stamped = mutableListOf<String>()
    override suspend fun apply(sourcePath: String, text: String, outputPath: String) {
        stamped += text
    }
}
