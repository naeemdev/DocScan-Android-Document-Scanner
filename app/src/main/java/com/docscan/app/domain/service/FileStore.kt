package com.docscan.app.domain.service

interface FileStore {
    /** Copies an external image (content:// or file:// URI) into app storage and returns its path. */
    suspend fun importImage(sourceUri: String): String
    fun newImagePath(): String
    /** Copies a stored file and returns the copy's path. */
    suspend fun copy(path: String): String
    fun newExportPath(fileName: String): String
    /** Writes [text] to a shareable export file and returns its path. */
    suspend fun writeText(fileName: String, text: String): String
    suspend fun delete(paths: List<String>)
    /** Removes previously shared export files; they hold copies of documents. */
    suspend fun clearExports()
    /** Removes every stored scan and export. */
    suspend fun clearAll()
}
