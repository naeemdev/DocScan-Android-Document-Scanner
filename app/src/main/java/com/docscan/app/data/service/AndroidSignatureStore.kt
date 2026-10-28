package com.docscan.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.docscan.app.domain.model.Point
import com.docscan.app.domain.service.SignatureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

class AndroidSignatureStore(context: Context) : SignatureStore {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    private val state = MutableStateFlow(list())
    override val signatures: StateFlow<List<String>> = state.asStateFlow()

    override suspend fun save(strokes: List<List<Point>>, aspectRatio: Float): String = withContext(Dispatchers.Default) {
        val padWidth = CANVAS_WIDTH.toFloat()
        val padHeight = padWidth / aspectRatio
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            style = Paint.Style.STROKE
            strokeWidth = padWidth * STROKE_FRACTION
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Crop to the ink so placing it on a page is predictable.
        val xs = strokes.flatten().map { it.x * padWidth }
        val ys = strokes.flatten().map { it.y * padHeight }
        val margin = paint.strokeWidth
        val left = (xs.min() - margin).coerceAtLeast(0f)
        val top = (ys.min() - margin).coerceAtLeast(0f)
        val width = ((xs.max() + margin).coerceAtMost(padWidth) - left).roundToInt().coerceAtLeast(1)
        val height = ((ys.max() + margin).coerceAtMost(padHeight) - top).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-left, -top)
        strokes.forEach { stroke ->
            val path = Path()
            stroke.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * padWidth, p.y * padHeight) else path.lineTo(p.x * padWidth, p.y * padHeight)
            }
            canvas.drawPath(path, paint)
        }
        val file = File(dir, "${UUID.randomUUID()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        state.value = list()
        file.path
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        // Only files inside our own folder can be deleted through here.
        File(path).takeIf { it.parentFile == dir }?.delete()
        state.value = list()
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        state.value = list()
    }

    private fun list(): List<String> =
        dir.listFiles { file -> file.extension == "png" }?.sortedByDescending { it.lastModified() }?.map { it.path }.orEmpty()

    private companion object {
        const val CANVAS_WIDTH = 1200
        const val STROKE_FRACTION = 0.008f
        // Dark blue ink reads as signed by hand; it is stamped after the colour filter, so filters never change it.
        val INK = Color.rgb(16, 32, 96)
    }
}
