package com.docscan.app.ui.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.repository.EntitlementRepository
import com.docscan.app.domain.usecase.DeletePageUseCase
import com.docscan.app.domain.usecase.DeleteSignatureUseCase
import com.docscan.app.domain.usecase.EditPageUseCase
import com.docscan.app.domain.usecase.MovePageUseCase
import com.docscan.app.domain.usecase.ObserveDocumentUseCase
import com.docscan.app.domain.usecase.ObserveSignaturesUseCase
import com.docscan.app.domain.usecase.PlaceSignatureUseCase
import com.docscan.app.domain.usecase.RemoveSignatureUseCase
import com.docscan.app.domain.usecase.RotatePageUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PageUiState(
    val page: Page? = null,
    /** Zero-based position of [page] in its document. */
    val index: Int = 0,
    val pageCount: Int = 0,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val isPro: Boolean = false,
    val signatures: List<String> = emptyList(),
)

sealed interface PageEvent {
    data object Closed : PageEvent
    data object Failed : PageEvent
}

class PageViewModel(
    documentId: Long,
    private val pageId: Long,
    observeDocument: ObserveDocumentUseCase,
    observeSignatures: ObserveSignaturesUseCase,
    entitlements: EntitlementRepository,
    private val editPage: EditPageUseCase,
    private val rotatePage: RotatePageUseCase,
    private val movePage: MovePageUseCase,
    private val deletePage: DeletePageUseCase,
    private val placeSignature: PlaceSignatureUseCase,
    private val removeSignature: RemoveSignatureUseCase,
    private val deleteSignature: DeleteSignatureUseCase,
) : ViewModel() {
    private val processing = MutableStateFlow(false)
    private val _events = Channel<PageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private val document = observeDocument(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<PageUiState> =
        combine(document, processing, entitlements.isPro, observeSignatures()) { doc, isProcessing, isPro, signatures ->
            val index = doc?.pages?.indexOfFirst { it.id == pageId } ?: -1
            PageUiState(
                page = doc?.pages?.getOrNull(index),
                index = index.coerceAtLeast(0),
                pageCount = doc?.pages?.size ?: 0,
                isLoading = false,
                isProcessing = isProcessing,
                isPro = isPro,
                signatures = signatures,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageUiState())

    fun onFilter(filter: PageFilter) = withPage {
        if (it.adjustments.filter != filter) editPage(it, it.adjustments.copy(filter = filter))
    }

    fun onAdjust(brightness: Float, contrast: Float) = withPage {
        editPage(it, it.adjustments.copy(brightness = brightness, contrast = contrast))
    }

    fun onRotate() = withPage { rotatePage(it) }

    fun onMove(offset: Int) = withPage {
        val doc = document.value ?: return@withPage
        val from = state.value.index
        movePage(doc, from, from + offset)
    }

    fun onPlaceSignature(path: String, x: Float, y: Float, width: Float) = withPage { placeSignature(it, path, x, y, width) }

    fun onRemoveSignature() = withPage { removeSignature(it) }

    fun onDeleteSavedSignature(path: String) = viewModelScope.launch { deleteSignature(path) }

    fun onDelete() = withPage {
        deletePage(it)
        _events.send(PageEvent.Closed)
    }

    private fun withPage(block: suspend (Page) -> Unit) = viewModelScope.launch {
        val page = state.value.page ?: return@launch
        if (processing.value) return@launch
        processing.value = true
        attempt { block(page) }.onFailure { _events.send(PageEvent.Failed) }
        processing.value = false
    }
}
