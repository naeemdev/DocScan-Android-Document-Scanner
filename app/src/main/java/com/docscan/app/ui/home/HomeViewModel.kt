package com.docscan.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.Folder
import com.docscan.app.domain.usecase.CreateFolderUseCase
import com.docscan.app.domain.usecase.DeleteDocumentUseCase
import com.docscan.app.domain.usecase.DeleteFolderUseCase
import com.docscan.app.domain.usecase.ImportScanUseCase
import com.docscan.app.domain.usecase.MergeDocumentsUseCase
import com.docscan.app.domain.usecase.MoveDocumentsUseCase
import com.docscan.app.domain.usecase.ObserveDocumentsUseCase
import com.docscan.app.domain.usecase.ObserveFoldersUseCase
import com.docscan.app.domain.usecase.RenameFolderUseCase
import com.docscan.app.ui.components.attempt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val documents: List<DocumentSummary> = emptyList(),
    val folders: List<Folder> = emptyList(),
    /** Folder being shown; null = all documents. */
    val folderId: Long? = null,
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    /** Selected document ids, in the order they were picked (that is the merge order). */
    val selectedIds: List<Long> = emptyList(),
) {
    val currentFolder: Folder? get() = folders.firstOrNull { it.id == folderId }
}

sealed interface HomeEvent {
    data class OpenDocument(val id: Long) : HomeEvent
    data object ImportFailed : HomeEvent
    data class Moved(val count: Int) : HomeEvent
}

private data class Filters(val query: String, val folderId: Long?)

class HomeViewModel(
    observeDocuments: ObserveDocumentsUseCase,
    observeFolders: ObserveFoldersUseCase,
    private val importScan: ImportScanUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val mergeDocuments: MergeDocumentsUseCase,
    private val createFolder: CreateFolderUseCase,
    private val renameFolder: RenameFolderUseCase,
    private val deleteFolder: DeleteFolderUseCase,
    private val moveDocuments: MoveDocumentsUseCase,
) : ViewModel() {
    private val filters = MutableStateFlow(Filters("", null))
    private val importing = MutableStateFlow(false)
    private val selected = MutableStateFlow<List<Long>>(emptyList())
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val documents = filters.flatMapLatest { observeDocuments(it.query, it.folderId) }

    val state: StateFlow<HomeUiState> = combine(
        filters,
        documents,
        observeFolders(),
        importing,
        selected,
    ) { f, docs, folders, isImporting, selectedIds ->
        val visible = docs.map { it.id }.toSet()
        HomeUiState(
            query = f.query,
            documents = docs,
            folders = folders,
            // A deleted folder falls back to "all".
            folderId = f.folderId?.takeIf { id -> folders.any { it.id == id } },
            isLoading = false,
            isImporting = isImporting,
            selectedIds = selectedIds.filter { it in visible },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) = filters.update { it.copy(query = value) }

    fun onFolderSelected(id: Long?) {
        filters.update { it.copy(folderId = id) }
        selected.value = emptyList()
    }

    fun onScanned(uris: List<String>) = viewModelScope.launch {
        importing.value = true
        attempt { importScan(uris) }
            .onSuccess { id ->
                // A scan made while a folder is open belongs in that folder.
                state.value.folderId?.let { moveDocuments(listOf(id), it) }
                _events.send(HomeEvent.OpenDocument(id))
            }
            .onFailure { _events.send(HomeEvent.ImportFailed) }
        importing.value = false
    }

    fun onDelete(ids: List<Long>) = viewModelScope.launch {
        ids.forEach { deleteDocument(it) }
        selected.value = emptyList()
    }

    fun onToggleSelection(id: Long) = selected.update { if (id in it) it - id else it + id }

    fun onClearSelection() {
        selected.value = emptyList()
    }

    fun onMergeSelected() = viewModelScope.launch {
        val ids = state.value.selectedIds
        if (ids.size < 2 || importing.value) return@launch
        importing.value = true
        attempt { mergeDocuments(ids) }
            .onSuccess {
                selected.value = emptyList()
                _events.send(HomeEvent.OpenDocument(it))
            }
            .onFailure { _events.send(HomeEvent.ImportFailed) }
        importing.value = false
    }

    /** [folderId] null takes the documents out of their folder. */
    fun onMoveSelected(folderId: Long?) = viewModelScope.launch {
        val ids = state.value.selectedIds
        moveDocuments(ids, folderId)
        selected.value = emptyList()
        _events.send(HomeEvent.Moved(ids.size))
    }

    /** Creates a folder; with documents selected they are moved into it. */
    fun onCreateFolder(name: String) = viewModelScope.launch {
        val id = createFolder(name) ?: return@launch
        val ids = state.value.selectedIds
        if (ids.isNotEmpty()) {
            moveDocuments(ids, id)
            selected.value = emptyList()
            _events.send(HomeEvent.Moved(ids.size))
        }
    }

    fun onRenameFolder(id: Long, name: String) = viewModelScope.launch { renameFolder(id, name) }

    fun onDeleteFolder(id: Long) = viewModelScope.launch {
        deleteFolder(id)
        if (filters.value.folderId == id) filters.update { it.copy(folderId = null) }
    }
}
