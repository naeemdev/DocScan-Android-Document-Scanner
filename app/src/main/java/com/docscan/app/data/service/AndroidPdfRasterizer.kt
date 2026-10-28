package com.docscan.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.docscan.app.domain.service.PdfRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class AndroidPdfRasterizer(private val context: Context) : PdfRasterizer {
    override suspend fun renderPages(sourceUri: String, newPath: () -> String): List<String> =
        withContext(Dispatchers.IO) {
            val written = mutableListOf<String>()
            try {
                val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(sourceUri), "r")
                    ?: error("Cannot open $sourceUri")
                // PdfRenderer throws SecurityException for password-protected files; the caller reports it.
                PdfRenderer(descriptor).use { renderer ->
                    // The file is untrusted input: cap the work it can ask for.
                    require(renderer.pageCount in 1..MAX_PAGES) { "Unsupported page count ${renderer.pageCount}" }
                    repeat(renderer.pageCount) { index ->
                        renderer.openPage(index).use { page ->
                            val scale = LONG_SIDE.toFloat() / maxOf(page.width, page.height)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).roundToInt(),
                                (page.height * scale).roundToInt(),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val path = newPath().also(written::add)
                            FileOutputStream(path).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                            bitmap.recycle()
                        }
                    }
                }
                written
            } catch (e: Exception) {
                written.forEach { File(it).delete() }
                throw e
            }
        }

    private companion object {
        const val MAX_PAGES = 300
        const val LONG_SIDE = 2000
        const val JPEG_QUALITY = 90
    }
}
