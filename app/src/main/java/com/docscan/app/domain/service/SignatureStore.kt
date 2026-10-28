package com.docscan.app.domain.service

import com.docscan.app.domain.model.Point
import kotlinx.coroutines.flow.StateFlow

/** The user's saved signatures, as transparent images. */
interface SignatureStore {
    val signatures: StateFlow<List<String>>
    /** [strokes] are in pad coordinates normalised to 0..1; [aspectRatio] is pad width / height. Returns the image path. */
    suspend fun save(strokes: List<List<Point>>, aspectRatio: Float): String
    suspend fun delete(path: String)
    suspend fun clearAll()
}
