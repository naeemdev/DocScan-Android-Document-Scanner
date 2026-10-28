package com.docscan.app.data.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.SignaturePlacement
import com.docscan.app.domain.service.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt

class AndroidImageProcessor : ImageProcessor {
    override suspend fun render(sourcePath: String, adjustments: PageAdjustments, outputPath: String) =
        withContext(Dispatchers.Default) {
            val source = decodeSampled(sourcePath, MAX_SIDE)
            val cropped = adjustments.crop?.let { perspectiveCrop(source, it) } ?: source
            val rotated = Bitmap.createBitmap(
                cropped, 0, 0, cropped.width, cropped.height,
                Matrix().apply { postRotate(adjustments.rotation.toFloat()) }, true,
            )
            val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val colour = adjustments.filter.toColorMatrix().apply {
                postConcat(contrast(adjustments.contrast, adjustments.brightness * BRIGHTNESS_RANGE))
            }
            canvas.drawBitmap(rotated, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colour) })
            adjustments.signature?.let { drawSignature(canvas, it, output.width, output.height) }
            FileOutputStream(outputPath).use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            setOf(source, cropped, rotated, output).forEach { it.recycle() }
        }

    /** Maps the four corners onto a rectangle sized by the quad's average edge lengths. */
    private fun perspectiveCrop(source: Bitmap, crop: CropQuad): Bitmap {
        val px = crop.corners.map { it.x * source.width to it.y * source.height }
        fun edge(a: Int, b: Int) = hypot(px[a].first - px[b].first, px[a].second - px[b].second)
        val width = ((edge(0, 1) + edge(3, 2)) / 2).roundToInt().coerceAtLeast(1)
        val height = ((edge(0, 3) + edge(1, 2)) / 2).roundToInt().coerceAtLeast(1)
        val matrix = Matrix().apply {
            setPolyToPoly(
                px.flatMap { listOf(it.first, it.second) }.toFloatArray(), 0,
                floatArrayOf(0f, 0f, width.toFloat(), 0f, width.toFloat(), height.toFloat(), 0f, height.toFloat()), 0,
                4,
            )
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    private fun drawSignature(canvas: Canvas, placement: SignaturePlacement, width: Int, height: Int) {
        // A missing file (e.g. storage cleared) drops the stamp instead of failing the whole page.
        val signature = BitmapFactory.decodeFile(placement.imagePath) ?: return
        val left = placement.x * width
        val top = placement.y * height
        val drawWidth = placement.width * width
        val drawHeight = drawWidth * signature.height / signature.width
        canvas.drawBitmap(signature, null, RectF(left, top, left + drawWidth, top + drawHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        signature.recycle()
    }

    private companion object {
        const val MAX_SIDE = 2400
        const val JPEG_QUALITY = 90
        const val BRIGHTNESS_RANGE = 80f
    }
}

// shortcut: global colour matrices, not adaptive thresholding; uneven lighting stays uneven. Upgrade to OpenCV if users complain.
internal fun PageFilter.toColorMatrix(): ColorMatrix = when (this) {
    PageFilter.ORIGINAL -> ColorMatrix()
    PageFilter.ENHANCE -> contrast(1.35f, 12f).apply { postConcat(ColorMatrix().apply { setSaturation(1.15f) }) }
    PageFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
    PageFilter.BLACK_WHITE -> ColorMatrix().apply { setSaturation(0f); postConcat(contrast(3f, 10f)) }
    PageFilter.LIGHTEN -> contrast(1.1f, 45f)
}

private fun contrast(scale: Float, brightness: Float): ColorMatrix {
    val offset = (1 - scale) * 128f + brightness
    return ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, offset,
            0f, scale, 0f, 0f, offset,
            0f, 0f, scale, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}
