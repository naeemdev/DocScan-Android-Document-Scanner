package com.docscan.app.ui.document

import androidx.compose.foundation.border
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.ExportFormat
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.MAX_TITLE_LENGTH
import com.docscan.app.ui.components.EmptyState
import com.docscan.app.ui.components.FileImage
import com.docscan.app.ui.components.LabeledAction
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.rememberDocumentScanner
import com.docscan.app.ui.components.shareFiles
import com.docscan.app.ui.preview.PreviewData
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

@Composable
fun DocumentRoute(
    documentId: Long,
    onBack: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onExtractText: () -> Unit,
) {
    val viewModel = appViewModel {
        DocumentViewModel(
            documentId,
            observeDocument,
            importScan,
            renameDocument,
            deleteDocument,
            exportDocument,
            movePage,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.error_generic)
    val shareTitle = stringResource(R.string.share_chooser_title)
    var scannerError by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DocumentEvent.Share -> context.shareFiles(
                    event.paths,
                    event.mimeType,
                    shareTitle
                )

                DocumentEvent.Closed -> onBack()
                DocumentEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }
    LaunchedEffect(scannerError) {
        if (scannerError) snackbar.showSnackbar(failed)
        scannerError = false
    }
    val scan = rememberDocumentScanner(
        onScanned = viewModel::onPagesScanned,
        onError = { scannerError = true })

    DocumentScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onRename = { viewModel.onRename(it) },
        onDelete = { viewModel.onDelete() },
        onAddPages = scan,
        onOpenPage = onOpenPage,
        onMovePage = { from, to -> viewModel.onMovePage(from, to) },
        onExport = { viewModel.onExport(it) },
        onExtractText = onExtractText,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    state: DocumentUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAddPages: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onMovePage: (from: Int, to: Int) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onExtractText: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val document = state.document
    val canAct = document != null && !state.isBusy

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        document?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            enabled = document != null,
                            role = Role.Button
                        ) { renaming = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }, enabled = document != null) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.action_rename))
                    }
                    IconButton(onClick = { confirmDelete = true }, enabled = document != null) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                LabeledAction(
                    Icons.Outlined.PictureAsPdf,
                    stringResource(R.string.action_share_pdf),
                    canAct
                ) { onExport(ExportFormat.PDF) }
                LabeledAction(
                    Icons.Outlined.Image,
                    stringResource(R.string.action_share_images),
                    canAct
                ) { onExport(ExportFormat.JPG) }
                LabeledAction(
                    Icons.Outlined.TextSnippet,
                    stringResource(R.string.action_extract_text),
                    canAct,
                    onClick = onExtractText,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                document == null -> EmptyState(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.document_missing_title),
                    body = stringResource(R.string.document_missing_body),
                )

                else -> PageGrid(
                    pages = document.pages,
                    busy = state.isBusy,
                    onOpenPage = onOpenPage,
                    onMovePage = onMovePage,
                    onAddPages = onAddPages,
                )
            }
        }
    }

    if (renaming && document != null) {
        RenameDialog(
            initial = document.title,
            onDismiss = { renaming = false },
            onConfirm = { onRename(it); renaming = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false; onDelete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(
                        stringResource(
                            R.string.action_cancel
                        )
                    )
                }
            },
        )
    }
}

/** Page thumbnails; long-press and drag a page to move it. */
@Composable
private fun PageGrid(
    pages: List<Page>,
    busy: Boolean,
    onOpenPage: (Long) -> Unit,
    onMovePage: (from: Int, to: Int) -> Unit,
    onAddPages: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Local order while dragging; reset whenever the stored order changes.
    var order by remember(pages) { mutableStateOf(pages) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun itemAt(position: Offset) = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
        item.key is Long && position.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                position.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(Sizes.pageGridCell),
        state = gridState,
        contentPadding = PaddingValues(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.pointerInput(pages, busy) {
            if (busy) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { start -> draggedId = itemAt(start)?.key as Long? },
                onDrag = { change, amount ->
                    val id = draggedId ?: return@detectDragGesturesAfterLongPress
                    change.consume()
                    dragOffset += amount
                    val dragged = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                        ?: return@detectDragGesturesAfterLongPress
                    val center = dragged.offset.toOffset() + dragOffset +
                            Offset(dragged.size.width / 2f, dragged.size.height / 2f)
                    val target = itemAt(center)?.takeIf { it.key != id }
                        ?: return@detectDragGesturesAfterLongPress
                    order =
                        order.toMutableList().apply { add(target.index, removeAt(dragged.index)) }
                    // The dragged cell now lays out at the target slot; keep it under the finger.
                    dragOffset += (dragged.offset - target.offset).toOffset()
                },
                onDragEnd = {
                    val id = draggedId
                    val from = pages.indexOfFirst { it.id == id }
                    val to = order.indexOfFirst { it.id == id }
                    if (id != null && from != to) onMovePage(from, to)
                    draggedId = null
                    dragOffset = Offset.Zero
                },
                onDragCancel = {
                    order = pages
                    draggedId = null
                    dragOffset = Offset.Zero
                },
            )
        },
    ) {
        itemsIndexed(order, key = { _, page -> page.id }) { index, page ->
            val dragging = page.id == draggedId
            PageCell(
                page = page,
                number = index + 1,
                onClick = { onOpenPage(page.id) },
                modifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = DRAG_SCALE
                            scaleY = DRAG_SCALE
                        }
                } else {
                    Modifier.animateItem()
                },
            )
        }
        item(key = "add") { AddPageCell(enabled = !busy, onClick = onAddPages) }
    }
}

@Composable
private fun PageCell(page: Page, number: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.page_number, number)
    Box(
        modifier
            .aspectRatio(PAGE_ASPECT)
            .clip(RoundedCornerShape(Spacing.s))
            .clickable(onClickLabel = label, onClick = onClick),
    ) {
        FileImage(page.processedPath, label, Modifier.fillMaxSize(), maxSide = 500)
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = RoundedCornerShape(topEnd = Spacing.s),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                number.toString(),
                Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun AddPageCell(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(PAGE_ASPECT)
            .clip(RoundedCornerShape(Spacing.s))
            .border(
                Sizes.hairline,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(Spacing.s)
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.action_add_pages),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MAX_TITLE_LENGTH) },
                label = { Text(stringResource(R.string.rename_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) {
                Text(
                    stringResource(R.string.action_save)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val PAGE_ASPECT = 0.72f
private const val DISABLED_ALPHA = 0.38f
private const val DRAG_SCALE = 1.05f

@Preview(name = "Document – pages", showBackground = true)
@Composable
private fun DocumentFilledPreview() = DocScanTheme {
    DocumentScreen(
        DocumentUiState(PreviewData.document(), isLoading = false),
        {},
        {},
        {},
        {},
        {},
        { _, _ -> },
        {},
        {})
}

@Preview(name = "Document – exporting", showBackground = true)
@Composable
private fun DocumentBusyPreview() = DocScanTheme {
    DocumentScreen(
        DocumentUiState(PreviewData.document(), isLoading = false, isBusy = true),
        {},
        {},
        {},
        {},
        {},
        { _, _ -> },
        {},
        {})
}

@Preview(name = "Document – deleted / missing", showBackground = true)
@Composable
private fun DocumentMissingPreview() = DocScanTheme {
    DocumentScreen(
        DocumentUiState(null, isLoading = false),
        {},
        {},
        {},
        {},
        {},
        { _, _ -> },
        {},
        {})
}

@Preview(name = "Document – loading", showBackground = true)
@Composable
private fun DocumentLoadingPreview() = DocScanTheme {
    DocumentScreen(DocumentUiState(), {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}
