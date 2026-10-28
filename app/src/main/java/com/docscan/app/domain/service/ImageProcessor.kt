package com.docscan.app.domain.service

import com.docscan.app.domain.model.PageAdjustments

interface ImageProcessor {
    /** Renders [sourcePath] with [adjustments] to a JPEG at [outputPath]. */
    suspend fun render(sourcePath: String, adjustments: PageAdjustments, outputPath: String)
}
