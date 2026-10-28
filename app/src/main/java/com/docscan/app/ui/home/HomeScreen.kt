package com.docscan.app.ui.home

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.Folder
import com.docscan.app.domain.model.MAX_FOLDER_NAME_LENGTH
import com.docscan.app.ui.components.EmptyState
import com.docscan.app.ui.components.FileImage
import com.docscan.app.ui.components.TextInputDialog
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.rememberDocumentScanner
import com.docscan.app.ui.preview.PreviewData
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

@Composable
fun HomeRoute(onOpenDocument: (Long) -> Unit) {
    val viewModel = appViewModel {
        HomeViewModel(
            observeDocuments, observeFolders, importScan, deleteDocument, mergeDocuments,
            createFolder, renameFolder, deleteFolder, moveDocuments,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importFailed = stringResource(R.string.error_import)
    val scannerFailed = stringResource(R.string.error_scanner)
    var scannerError by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenDocument -> onOpenDocument(event.id)
                HomeEvent.ImportFailed -> snackbar.showSnackbar(importFailed)
                is HomeEvent.Moved -> snackbar.showSnackbar(
                    context.resources.getQuantityString(R.plurals.moved_count, event.count, event.count),
                )
            }
        }
    }
    LaunchedEffect(scannerError) {
        if (scannerError) snackbar.showSnackbar(scannerFailed)
        scannerError = false
    }
    val scan = rememberDocumentScanner(onScanned = viewModel::onScanned, onError = { scannerError = true })

    HomeScreen(
        state = state,
        snackbar = snackbar,
        actions = HomeActions(
            onQueryChange = viewModel::onQueryChange,
            onScan = scan,
            onOpen = onOpenDocument,
            onDelete = { viewModel.onDelete(it) },
            onToggleSelection = { viewModel.onToggleSelection(it) },
            onClearSelection = viewModel::onClearSelection,
            onMerge = { viewModel.onMergeSelected() },
            onFolderSelected = viewModel::onFolderSelected,
            onCreateFolder = { viewModel.onCreateFolder(it) },
            onRenameFolder = { id, name -> viewModel.onRenameFolder(id, name) },
            onDeleteFolder = { viewModel.onDeleteFolder(it) },
            onMoveSelected = { viewModel.onMoveSelected(it) },
        ),
    )
}

/** Everything the Home screen can ask for; grouped so the screen signature stays readable. */
data class HomeActions(
    val onQueryChange: (String) -> Unit = {},
    val onScan: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onDelete: (List<Long>) -> Unit = {},
    val onToggleSelection: (Long) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onMerge: () -> Unit = {},
    val onFolderSelected: (Long?) -> Unit = {},
    val onCreateFolder: (String) -> Unit = {},
    val onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    val onDeleteFolder: (Long) -> Unit = {},
    val onMoveSelected: (Long?) -> Unit = {},
)

private sealed interface HomeDialog {
    data class DeleteDocuments(val ids: List<Long>) : HomeDialog
    data object NewFolder : HomeDialog
    data class FolderOptions(val folder: Folder) : HomeDialog
    data class RenameFolder(val folder: Folder) : HomeDialog
    data class DeleteFolder(val folder: Folder) : HomeDialog
    data object MoveTo : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions = HomeActions(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val selecting = state.selectedIds.isNotEmpty()
    BackHandler(enabled = selecting, onBack = actions.onClearSelection)

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, state.selectedIds.size, state.selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = actions.onClearSelection) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { dialog = HomeDialog.MoveTo }) {
                            Icon(Icons.Outlined.DriveFileMove, stringResource(R.string.action_move_to_folder))
                        }
                        IconButton(onClick = actions.onMerge, enabled = state.selectedIds.size >= 2 && !state.isImporting) {
                            Icon(Icons.Outlined.CallMerge, stringResource(R.string.action_merge))
                        }
                        IconButton(onClick = { dialog = HomeDialog.DeleteDocuments(state.selectedIds) }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text(state.currentFolder?.name ?: stringResource(R.string.app_name)) })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.isImporting) actions.onScan() },
                icon = { Icon(Icons.Outlined.PhotoCamera, null) },
                text = { Text(stringResource(R.string.action_scan)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_clear_search))
                        }
                    }
                },
                singleLine = true,
            )
            FolderRow(
                state = state,
                onSelect = actions.onFolderSelected,
                onOptions = { dialog = HomeDialog.FolderOptions(it) },
                onNewFolder = { dialog = HomeDialog.NewFolder },
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.documents.isEmpty() && state.query.isNotBlank() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.search_empty_title, state.query),
                    body = stringResource(R.string.search_empty_body),
                )
                state.documents.isEmpty() && state.currentFolder != null -> EmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.folder_empty_title),
                    body = stringResource(R.string.folder_empty_body),
                )
                state.documents.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.DocumentScanner,
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = Spacing.xl * 3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(state.documents, key = { it.id }) { document ->
                        val selected = document.id in state.selectedIds
                        DocumentRow(
                            document = document,
                            selecting = selecting,
                            selected = selected,
                            onClick = { if (selecting) actions.onToggleSelection(document.id) else actions.onOpen(document.id) },
                            onLongClick = { actions.onToggleSelection(document.id) },
                            onDelete = { dialog = HomeDialog.DeleteDocuments(listOf(document.id)) },
                        )
                    }
                }
            }
        }
    }

    HomeDialogs(dialog, state, actions, onChange = { dialog = it })
}

@Composable
private fun FolderRow(state: HomeUiState, onSelect: (Long?) -> Unit, onOptions: (Folder) -> Unit, onNewFolder: () -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.padding(bottom = Spacing.s),
    ) {
        item(key = "all") {
            FilterChip(
                selected = state.folderId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.folder_all)) },
            )
        }
        items(state.folders, key = { it.id }) { folder ->
            val selected = folder.id == state.folderId
            FilterChip(
                selected = selected,
                onClick = { if (selected) onOptions(folder) else onSelect(folder.id) },
                label = { Text(stringResource(R.string.folder_chip, folder.name, folder.documentCount), maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                trailingIcon = if (selected) {
                    { Icon(Icons.Outlined.MoreVert, stringResource(R.string.folder_options, folder.name)) }
                } else {
                    null
                },
            )
        }
        item(key = "new") {
            AssistChip(
                onClick = onNewFolder,
                label = { Text(stringResource(R.string.action_new_folder)) },
                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
            )
        }
    }
}

@Composable
private fun HomeDialogs(dialog: HomeDialog?, state: HomeUiState, actions: HomeActions, onChange: (HomeDialog?) -> Unit) {
    val dismiss = { onChange(null) }
    when (dialog) {
        null -> Unit
        is HomeDialog.DeleteDocuments -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(pluralStringResource(R.plurals.delete_documents_title, dialog.ids.size, dialog.ids.size)) },
            text = { Text(stringResource(R.string.delete_document_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDelete(dialog.ids); dismiss() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        HomeDialog.NewFolder -> TextInputDialog(
            title = stringResource(R.string.action_new_folder),
            label = stringResource(R.string.folder_name_label),
            confirmLabel = stringResource(R.string.action_create),
            maxLength = MAX_FOLDER_NAME_LENGTH,
            onDismiss = dismiss,
            onConfirm = { actions.onCreateFolder(it); dismiss() },
        )
        is HomeDialog.FolderOptions -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(dialog.folder.name) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_rename)) },
                        modifier = Modifier.clickable(onClick = { onChange(HomeDialog.RenameFolder(dialog.folder)) }),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.action_delete_folder)) },
                        modifier = Modifier.clickable(onClick = { onChange(HomeDialog.DeleteFolder(dialog.folder)) }),
                    )
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } },
        )
        is HomeDialog.RenameFolder -> TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.folder_name_label),
            confirmLabel = stringResource(R.string.action_save),
            maxLength = MAX_FOLDER_NAME_LENGTH,
            initial = dialog.folder.name,
            onDismiss = dismiss,
            onConfirm = { actions.onRenameFolder(dialog.folder.id, it); dismiss() },
        )
        is HomeDialog.DeleteFolder -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.delete_folder_title, dialog.folder.name)) },
            text = { Text(stringResource(R.string.delete_folder_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDeleteFolder(dialog.folder.id); dismiss() }) {
                    Text(stringResource(R.string.action_delete_folder))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
        HomeDialog.MoveTo -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.action_move_to_folder)) },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.folder_none)) },
                            leadingContent = { Icon(Icons.Outlined.FolderOpen, null) },
                            modifier = Modifier.clickable(onClick = { actions.onMoveSelected(null); dismiss() }),
                        )
                    }
                    items(state.folders, key = { it.id }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Outlined.Folder, null) },
                            modifier = Modifier.clickable(onClick = { actions.onMoveSelected(folder.id); dismiss() }),
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.action_new_folder)) },
                            leadingContent = { Icon(Icons.Outlined.CreateNewFolder, null) },
                            modifier = Modifier.clickable(onClick = { onChange(HomeDialog.NewFolder) }),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DocumentRow(
    document: DocumentSummary,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.s))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.action_select),
            )
            .semantics { this.selected = selected }
            .padding(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        FileImage(
            path = document.coverPath,
            contentDescription = null,
            modifier = Modifier.size(Sizes.listThumbWidth, Sizes.listThumbHeight).clip(RoundedCornerShape(Spacing.xs)),
            maxSide = 200,
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.document_meta,
                    pluralStringResource(R.plurals.page_count, document.pageCount, document.pageCount),
                    DateUtils.getRelativeTimeSpanString(document.updatedAt).toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = null)
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete_named, document.title))
            }
        }
    }
}

@Preview(name = "Home – filled", showBackground = true)
@Composable
private fun HomeFilledPreview() = DocScanTheme {
    HomeScreen(HomeUiState(documents = PreviewData.summaries(), folders = PreviewData.folders(), isLoading = false))
}

@Preview(name = "Home – inside a folder", showBackground = true)
@Composable
private fun HomeFolderPreview() = DocScanTheme {
    val folders = PreviewData.folders()
    HomeScreen(HomeUiState(documents = PreviewData.summaries().take(1), folders = folders, folderId = folders.first().id, isLoading = false))
}

@Preview(name = "Home – empty folder", showBackground = true)
@Composable
private fun HomeEmptyFolderPreview() = DocScanTheme {
    val folders = PreviewData.folders()
    HomeScreen(HomeUiState(folders = folders, folderId = folders.last().id, isLoading = false))
}

@Preview(name = "Home – empty", showBackground = true)
@Composable
private fun HomeEmptyPreview() = DocScanTheme {
    HomeScreen(HomeUiState(isLoading = false))
}

@Preview(name = "Home – no search results", showBackground = true)
@Composable
private fun HomeNoResultsPreview() = DocScanTheme {
    HomeScreen(HomeUiState(query = stringResource(R.string.preview_search_query), isLoading = false))
}

@Preview(name = "Home – selecting", showBackground = true)
@Composable
private fun HomeSelectingPreview() = DocScanTheme {
    HomeScreen(HomeUiState(documents = PreviewData.summaries(), isLoading = false, selectedIds = listOf(2L, 1L)))
}

@Preview(name = "Home – importing (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeImportingPreview() = DocScanTheme(darkTheme = true) {
    HomeScreen(HomeUiState(documents = PreviewData.summaries(), isLoading = false, isImporting = true))
}
