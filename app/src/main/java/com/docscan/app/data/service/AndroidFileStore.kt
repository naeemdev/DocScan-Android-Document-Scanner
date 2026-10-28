package com.docscan.app.data.service

import android.content.Context
import android.net.Uri
import com.docscan.app.domain.service.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AndroidFileStore(private val context: Context) : FileStore {
    private val scansDir = File(context.filesDir, "scans").apply { mkdirs() }
    private val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }

    override suspend fun importImage(sourceUri: String): String = withContext(Dispatchers.IO) {
        val target = File(newImagePath())
        val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?: error("Cannot open $sourceUri")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target.path
    }

    override fun newImagePath(): String = File(scansDir, "${UUID.randomUUID()}.jpg").path

    override suspend fun copy(path: String): String = withContext(Dispatchers.IO) {
        File(path).copyTo(File(newImagePath())).path
    }

    override fun newExportPath(fileName: String): String = File(exportsDir, fileName).path

    override suspend fun writeText(fileName: String, text: String): String = withContext(Dispatchers.IO) {
        File(newExportPath(fileName)).apply { writeText(text) }.path
    }

    override suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }

    override suspend fun clearExports() = withContext(Dispatchers.IO) {
        exportsDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        scansDir.listFiles()?.forEach { it.delete() }
        exportsDir.listFiles()?.forEach { it.delete() }
        Unit
    }
}
