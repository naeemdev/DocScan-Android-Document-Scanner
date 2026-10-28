package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.AppSettings
import com.docscan.app.domain.model.ExportFormat
import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.PageAdjustments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProFeaturesTest {
    private val files = FakeFiles()
    private val repo = FakeRepo()

    private fun export(pro: Boolean, pdf: RecordingPdf, marks: RecordingWatermarker) = ExportDocumentUseCase(
        pdf, marks, files, FakeSettings(AppSettings(watermarkText = "  CONFIDENTIAL ")), FakeEntitlements(pro),
    )

    private suspend fun oneDocument() = repo.getDocument(
        repo.createDocument("t", listOf(NewPage(files.importImage("a"), files.importImage("b"), PageAdjustments()))),
    )!!

    @Test
    fun watermarkIsStampedOnlyForPro() = runBlocking {
        val doc = oneDocument()
        val freePdf = RecordingPdf()
        export(pro = false, freePdf, RecordingWatermarker())(doc, ExportFormat.PDF)
        assertNull(freePdf.watermark)

        val proPdf = RecordingPdf()
        val marks = RecordingWatermarker()
        export(pro = true, proPdf, marks)(doc, ExportFormat.PDF)
        assertEquals("CONFIDENTIAL", proPdf.watermark)
        export(pro = true, proPdf, marks)(doc, ExportFormat.JPG)
        assertEquals(listOf("CONFIDENTIAL"), marks.stamped)
    }

    @Test
    fun signatureNeedsPro() = runBlocking {
        val page = oneDocument().pages.first()
        val edit = EditPageUseCase(repo, files, recordingProcessor(files))
        val place = PlaceSignatureUseCase(files, edit, RequireProUseCase(FakeEntitlements(pro = false)))
        assertTrue(runCatching { place(page, "saved.png", 0.5f, 0.5f, 0.3f) }.exceptionOrNull() is ProRequiredException)
    }

    @Test
    fun replacingASignatureDeletesTheOldCopy() = runBlocking {
        files.live += "saved.png"
        val doc = oneDocument()
        val edit = EditPageUseCase(repo, files, recordingProcessor(files))
        val place = PlaceSignatureUseCase(files, edit, RequireProUseCase(FakeEntitlements(pro = true)))

        place(doc.pages.first(), "saved.png", 0.9f, 0.5f, 0.3f)
        val firstCopy = repo.getDocument(doc.id)!!.pages.first().adjustments.signature!!
        // Clamped so the signature stays on the page.
        assertEquals(0.7f, firstCopy.x, 0.0001f)

        place(repo.getDocument(doc.id)!!.pages.first(), "saved.png", 0.1f, 0.1f, 0.3f)
        assertFalse("old copy must be deleted", firstCopy.imagePath in files.live)
        assertTrue("saved signature is untouched", "saved.png" in files.live)
    }

    @Test
    fun folderNamesAreTrimmedAndBlankRejected() {
        assertEquals("Taxes", cleanFolderName("  Taxes "))
        assertNull(cleanFolderName("   "))
        assertEquals(60, cleanFolderName("x".repeat(200))!!.length)
    }
}
