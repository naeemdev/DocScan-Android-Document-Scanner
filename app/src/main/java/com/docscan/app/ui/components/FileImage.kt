package com.docscan.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FileImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxSide: Int = 600,
    contentScale: ContentScale = ContentScale.Fit,
    showBackground: Boolean = true,
) {
    val bitmap by produceState<ImageBitmap?>(null, path, maxSide) {
        value = path?.let { withContext(Dispatchers.IO) { decodeOrNull(it, maxSide) } }
    }
    Box(
        if (showBackground) modifier.background(MaterialTheme.colorScheme.surfaceVariant) else modifier,
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Icon(
                Icons.Outlined.Description,
                contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun decodeOrNull(path: String, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}
