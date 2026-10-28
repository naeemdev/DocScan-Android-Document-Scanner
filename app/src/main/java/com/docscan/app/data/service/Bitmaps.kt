package com.docscan.app.data.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Decodes with a power-of-two sample so the long side is at most [maxSide]; scans are 8-12 MP. */
internal fun decodeSampled(path: String, maxSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Cannot decode image $path")
}
