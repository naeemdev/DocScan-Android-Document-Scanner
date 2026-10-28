package com.docscan.app.domain.service

interface PdfRasterizer {
    /** Renders every page of the PDF at [sourceUri] to a JPEG at a path from [newPath]. Returns the paths in page order. */
    suspend fun renderPages(sourceUri: String, newPath: () -> String): List<String>
}
