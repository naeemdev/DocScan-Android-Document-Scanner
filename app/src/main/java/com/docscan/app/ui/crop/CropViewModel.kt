package com.docscan.app.ui.crop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.CropQuad
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.usecase.CropPageUseCase
import com.docscan.app.domain.usecase.ObserveDocumentUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CropUiState(
    val page: Page? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
)

sealed interface CropEvent {
    data object Done : CropEvent
    data object Failed : CropEvent
}

class CropViewModel(
    documentId: Long,
    private val pageId: Long,
    observeDocument: ObserveDocumentUseCase,
    private val cropPage: CropPageUseCase,
) : ViewModel() {
    private val saving = MutableStateFlow(false)
    private val _events = Channel<CropEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<CropUiState> = combine(observeDocument(documentId), saving) { doc, isSaving ->
        CropUiState(page = doc?.pages?.firstOrNull { it.id == pageId }, isLoading = false, isSaving = isSaving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CropUiState())

    /** [crop] null resets to the whole image. */
    fun onApply(crop: CropQuad?) = viewModelScope.launch {
        val page = state.value.page ?: return@launch
        if (saving.value) return@launch
        saving.value = true
        attempt { cropPage(page, crop) }
            .onSuccess { _events.send(CropEvent.Done) }
            .onFailure { _events.send(CropEvent.Failed) }
        saving.value = false
    }
}
