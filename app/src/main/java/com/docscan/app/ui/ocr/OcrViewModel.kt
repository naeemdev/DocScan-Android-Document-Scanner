package com.docscan.app.ui.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.usecase.ExportTextUseCase
import com.docscan.app.domain.usecase.RecognizeDocumentTextUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface OcrUiState {
    data object Loading : OcrUiState
    data class Done(val text: String) : OcrUiState
    data object Error : OcrUiState
}

class OcrViewModel(
    private val documentId: Long,
    private val recognizeText: RecognizeDocumentTextUseCase,
    private val exportText: ExportTextUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<OcrUiState>(OcrUiState.Loading)
    val state: StateFlow<OcrUiState> = _state.asStateFlow()
    private val _textFiles = Channel<Result<String>>(Channel.BUFFERED)
    /** Paths of .txt files ready to share, or a failure to report. */
    val textFiles = _textFiles.receiveAsFlow()

    init {
        retry()
    }

    fun retry() {
        _state.value = OcrUiState.Loading
        viewModelScope.launch {
            _state.value = attempt { recognizeText(documentId) }.fold(
                onSuccess = { pages ->
                    OcrUiState.Done(pages.filter { it.isNotBlank() }.joinToString("\n\n"))
                },
                onFailure = { OcrUiState.Error },
            )
        }
    }

    fun onSaveTxt(text: String) = viewModelScope.launch {
        _textFiles.send(attempt { exportText(documentId, text) })
    }
}
