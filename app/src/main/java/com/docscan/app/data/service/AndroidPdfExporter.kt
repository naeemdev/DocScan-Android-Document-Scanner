package com.docscan.app.data.service

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality
import com.docscan.app.domain.service.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

class AndroidPdfExporter : PdfExporter {
    override suspend fun export(
        pages: List<Page>,
        pageSize: PageSize,
        quality: PdfQuality,
        watermark: String?,
        outputPath: String,
    ) =
        withContext(Dispatchers.Default) {
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { index, page ->
                    val bitmap = decodeSampled(page.processedPath, quality.maxSide)
                    val (width, height) = pageSize.pointsFor(bitmap.width, bitmap.height)
                    val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                    val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val left = (width - drawWidth) / 2
                    val top = (height - drawHeight) / 2
                    pdfPage.canvas.drawBitmap(
                        bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    watermark?.let { drawWatermark(pdfPage.canvas, it, width.toFloat(), height.toFloat()) }
                    pdf.finishPage(pdfPage)
                    bitmap.recycle()
                }
                FileOutputStream(outputPath).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
        }

}

/** Long side of each embedded page image. PdfDocument stores bitmaps with little compression, so this sets file size. */
private val PdfQuality.maxSide: Int
    get() = when (this) {
        PdfQuality.SMALL -> 1200
        PdfQuality.BALANCED -> 1800
        PdfQuality.HIGH -> 2600
    }

/** PDF page size in points (1/72 inch), turned landscape when the image is. */
internal fun PageSize.pointsFor(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
    val landscape = imageWidth > imageHeight
    val (short, long) = when (this) {
        PageSize.A4 -> 595 to 842
        PageSize.LETTER -> 612 to 792
        PageSize.FIT_IMAGE -> {
            val scale = 842f / maxOf(imageWidth, imageHeight)
            return (imageWidth * scale).roundToInt() to (imageHeight * scale).roundToInt()
        }
    }
    return if (landscape) long to short else short to long
}
