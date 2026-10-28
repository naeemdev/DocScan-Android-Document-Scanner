package com.docscan.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Width / height of the image file at [path], read from its header only; null until known or if unreadable. */
@Composable
fun rememberImageAspect(path: String?): Float? {
    val aspect by produceState<Float?>(null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(it, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else null
            }
        }
    }
    return aspect
}
