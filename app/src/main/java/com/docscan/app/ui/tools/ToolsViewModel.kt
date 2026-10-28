package com.docscan.app.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.usecase.CreateIdCardUseCase
import com.docscan.app.domain.usecase.ImportPdfUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolsUiState(
    val isWorking: Boolean = false,
    val lastCode: String? = null,
    /** Front of an ID card waiting for its back side. */
    val idCardFront: String? = null,
)

sealed interface ToolsEvent {
    data class OpenDocument(val id: Long) : ToolsEvent
    data object PdfFailed : ToolsEvent
    data object Failed : ToolsEvent
}

class ToolsViewModel(
    private val importPdf: ImportPdfUseCase,
    private val createIdCard: CreateIdCardUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()
    private val _events = Channel<ToolsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onCodeScanned(code: String?) = _state.update { it.copy(lastCode = code) }

    fun onPdfPicked(uri: String) = work(ToolsEvent.PdfFailed) { importPdf(uri) }

    fun onIdCardSideScanned(uri: String) {
        val front = _state.value.idCardFront
        if (front == null) {
            _state.update { it.copy(idCardFront = uri) }
        } else {
            _state.update { it.copy(idCardFront = null) }
            work(ToolsEvent.Failed) { createIdCard(front, uri) }
        }
    }

    fun onIdCardCancelled() = _state.update { it.copy(idCardFront = null) }

    private fun work(failure: ToolsEvent, block: suspend () -> Long) = viewModelScope.launch {
        if (_state.value.isWorking) return@launch
        _state.update { it.copy(isWorking = true) }
        attempt { block() }
            .onSuccess { _events.send(ToolsEvent.OpenDocument(it)) }
            .onFailure { _events.send(failure) }
        _state.update { it.copy(isWorking = false) }
    }
}
