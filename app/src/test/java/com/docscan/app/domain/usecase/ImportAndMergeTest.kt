package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.NewPage
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.SignaturePlacement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportAndMergeTest {
    @Test
    fun failedSaveLeavesNoFiles() = runBlocking {
        val files = FakeFiles()
        val repo = FakeRepo(failCreate = true)
        val import = ImportScanUseCase(files, SavePagesUseCase(repo, files, recordingProcessor(files), FakeSettings(), FakeNamer))
        runCatching { import(listOf("a", "b")) }
        assertTrue("orphans: ${files.live}", files.live.isEmpty())
    }

    @Test
    fun mergeKeepsSelectionOrderAndCopiesEveryFile() = runBlocking {
        val files = FakeFiles()
        val repo = FakeRepo()
        // Rotation marks which source a page came from.
        val first = repo.createDocument(
            "a",
            listOf(NewPage(files.importImage("x"), files.importImage("x2"), PageAdjustments(rotation = 0))),
        )
        val signed = PageAdjustments(rotation = 90, signature = SignaturePlacement(files.importImage("sig"), 0.1f, 0.1f, 0.3f))
        val second = repo.createDocument(
            "b",
            listOf("y", "z").map { NewPage(files.importImage(it), files.importImage(it + "2"), signed) },
        )
        val merged = MergeDocumentsUseCase(repo, files, FakeNamer)(listOf(second, first))

        val doc = repo.docs.getValue(merged)
        assertEquals("b+1", doc.title)
        assertEquals(listOf(90, 90, 0), doc.pages.map { it.adjustments.rotation })
        val sourceFiles = (repo.docs.getValue(first).pages + repo.docs.getValue(second).pages).flatMap { it.ownedFiles }
        assertTrue("merged pages must own copies", doc.pages.flatMap { it.ownedFiles }.none { it in sourceFiles })
        assertNotEquals(signed.signature?.imagePath, doc.pages.first().adjustments.signature?.imagePath)
    }
}
