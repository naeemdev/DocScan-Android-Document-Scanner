package com.docscan.app.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.ExportFormat
import com.docscan.app.domain.usecase.DeleteDocumentUseCase
import com.docscan.app.domain.usecase.ExportDocumentUseCase
import com.docscan.app.domain.usecase.ImportScanUseCase
import com.docscan.app.domain.usecase.MovePageUseCase
import com.docscan.app.domain.usecase.ObserveDocumentUseCase
import com.docscan.app.domain.usecase.RenameDocumentUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentUiState(
    val document: Document? = null,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
)

sealed interface DocumentEvent {
    data class Share(val paths: List<String>, val mimeType: String) : DocumentEvent
    data object Closed : DocumentEvent
    data object Failed : DocumentEvent
}

class DocumentViewModel(
    private val documentId: Long,
    observeDocument: ObserveDocumentUseCase,
    private val importScan: ImportScanUseCase,
    private val renameDocument: RenameDocumentUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val exportDocument: ExportDocumentUseCase,
    private val movePage: MovePageUseCase,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val _events = Channel<DocumentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<DocumentUiState> =
        combine(observeDocument(documentId), busy) { document, isBusy ->
            DocumentUiState(document = document, isLoading = false, isBusy = isBusy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    fun onRename(title: String) = viewModelScope.launch { renameDocument(documentId, title) }

    fun onDelete() = viewModelScope.launch {
        deleteDocument(documentId)
        _events.send(DocumentEvent.Closed)
    }

    fun onMovePage(from: Int, to: Int) = runBusy {
        val document = state.value.document ?: return@runBusy
        movePage(document, from, to)
    }

    fun onPagesScanned(uris: List<String>) =
        runBusy { importScan(uris, intoDocumentId = documentId) }

    fun onExport(format: ExportFormat) = runBusy {
        val document = state.value.document ?: return@runBusy
        val paths = exportDocument(document, format)
        val mime = if (format == ExportFormat.PDF) "application/pdf" else "image/jpeg"
        _events.send(DocumentEvent.Share(paths, mime))
    }

    private fun runBusy(block: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        attempt { block() }.onFailure { _events.send(DocumentEvent.Failed) }
        busy.value = false
    }
}
