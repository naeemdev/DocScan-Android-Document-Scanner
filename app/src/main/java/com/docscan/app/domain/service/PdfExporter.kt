package com.docscan.app.domain.service

import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality

interface PdfExporter {
    suspend fun export(pages: List<Page>, pageSize: PageSize, quality: PdfQuality, watermark: String?, outputPath: String)
}
