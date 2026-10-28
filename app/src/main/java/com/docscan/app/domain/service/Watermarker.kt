package com.docscan.app.domain.service

interface Watermarker {
    /** Writes a copy of the image at [sourcePath] with [text] stamped across it. */
    suspend fun apply(sourcePath: String, text: String, outputPath: String)
}
