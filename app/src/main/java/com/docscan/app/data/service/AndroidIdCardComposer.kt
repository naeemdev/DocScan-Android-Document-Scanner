package com.docscan.app.data.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.docscan.app.domain.service.IdCardComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.min

class AndroidIdCardComposer : IdCardComposer {
    override suspend fun compose(frontPath: String, backPath: String, outputPath: String) =
        withContext(Dispatchers.Default) {
            val page = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            page.eraseColor(Color.WHITE)
            val canvas = Canvas(page)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            listOf(frontPath, backPath).forEachIndexed { slot, path ->
                val card = decodeSampled(path, PAGE_WIDTH)
                // Each side gets one half of the page, card centred with a margin.
                val boxWidth = PAGE_WIDTH * CARD_WIDTH_FRACTION
                val boxHeight = PAGE_HEIGHT / 2f * CARD_HEIGHT_FRACTION
                val scale = min(boxWidth / card.width, boxHeight / card.height)
                val width = card.width * scale
                val height = card.height * scale
                val left = (PAGE_WIDTH - width) / 2
                val top = slot * PAGE_HEIGHT / 2f + (PAGE_HEIGHT / 2f - height) / 2
                canvas.drawBitmap(card, null, RectF(left, top, left + width, top + height), paint)
                card.recycle()
            }
            FileOutputStream(outputPath).use { page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            page.recycle()
        }

    private companion object {
        // A4 at 200 dpi.
        const val PAGE_WIDTH = 1654
        const val PAGE_HEIGHT = 2339
        const val CARD_WIDTH_FRACTION = 0.7f
        const val CARD_HEIGHT_FRACTION = 0.8f
        const val JPEG_QUALITY = 90
    }
}
