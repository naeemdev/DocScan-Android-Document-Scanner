package com.docscan.app.data.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.docscan.app.domain.service.Watermarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.hypot

/** Draws [text] once, diagonally across the centre of a [width] x [height] area, translucent so the page stays readable. */
internal fun drawWatermark(canvas: Canvas, text: String, width: Float, height: Float) {
    val diagonal = hypot(width, height)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(WATERMARK_ALPHA, 120, 120, 120)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = diagonal / 12
    }
    // Shrink long texts so they fit along the diagonal.
    val maxWidth = diagonal * 0.8f
    val measured = paint.measureText(text)
    if (measured > maxWidth) paint.textSize *= maxWidth / measured
    canvas.save()
    canvas.rotate(-Math.toDegrees(atan2(height.toDouble(), width.toDouble())).toFloat(), width / 2, height / 2)
    canvas.drawText(text, width / 2, height / 2 - (paint.ascent() + paint.descent()) / 2, paint)
    canvas.restore()
}

private const val WATERMARK_ALPHA = 70

class AndroidWatermarker : Watermarker {
    override suspend fun apply(sourcePath: String, text: String, outputPath: String) = withContext(Dispatchers.Default) {
        val source = decodeSampled(sourcePath, MAX_SIDE)
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        drawWatermark(Canvas(output), text, output.width.toFloat(), output.height.toFloat())
        FileOutputStream(outputPath).use { output.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        source.recycle()
        output.recycle()
    }

    private companion object {
        const val MAX_SIDE = 2400
    }
}
