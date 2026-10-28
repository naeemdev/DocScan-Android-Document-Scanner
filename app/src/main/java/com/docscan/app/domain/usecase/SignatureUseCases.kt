package com.docscan.app.domain.usecase

import com.docscan.app.domain.model.Point
import com.docscan.app.domain.service.SignatureStore

class ObserveSignaturesUseCase(private val store: SignatureStore) {
    operator fun invoke() = store.signatures
}

class SaveSignatureUseCase(
    private val store: SignatureStore,
    private val requirePro: RequireProUseCase,
) {
    /** Strokes with fewer than two points are dots from accidental taps and are dropped. */
    suspend operator fun invoke(strokes: List<List<Point>>, aspectRatio: Float): String {
        requirePro()
        val drawn = strokes.filter { it.size >= 2 }
        require(drawn.isNotEmpty()) { "Nothing drawn" }
        require(aspectRatio > 0f) { "Bad pad size" }
        return store.save(drawn, aspectRatio)
    }
}

class DeleteSignatureUseCase(private val store: SignatureStore) {
    suspend operator fun invoke(path: String) = store.delete(path)
}
