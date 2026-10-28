package com.docscan.app.ui.page

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.usecase.EditPageUseCase
import com.docscan.app.domain.usecase.PlaceSignatureUseCase
import com.docscan.app.ui.components.FileImage
import com.docscan.app.ui.components.LabeledAction
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.label
import com.docscan.app.ui.components.rememberImageAspect
import com.docscan.app.ui.preview.PreviewData
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.FixedColors
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

sealed interface PageMode {
    data object View : PageMode
    data object Adjust : PageMode
    data class Place(val signaturePath: String) : PageMode
}

/** Where a signature sits while being placed; fractions of the page. */
data class Placement(val x: Float = 0.5f, val y: Float = 0.75f, val width: Float = 0.35f)

data class PageActions(
    val onBack: () -> Unit = {},
    val onFilter: (PageFilter) -> Unit = {},
    val onRotate: () -> Unit = {},
    val onMove: (offset: Int) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCrop: () -> Unit = {},
    val onModeChange: (PageMode) -> Unit = {},
    val onAdjust: (brightness: Float, contrast: Float) -> Unit = { _, _ -> },
    val onSign: () -> Unit = {},
    val onDismissSignatures: () -> Unit = {},
    val onNewSignature: () -> Unit = {},
    val onDeleteSavedSignature: (String) -> Unit = {},
    val onRemoveSignature: () -> Unit = {},
    val onPlace: (path: String, placement: Placement) -> Unit = { _, _ -> },
)

@Composable
fun PageRoute(
    documentId: Long,
    pageId: Long,
    onBack: () -> Unit,
    onCrop: () -> Unit,
    onNewSignature: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val viewModel = appViewModel {
        PageViewModel(
            documentId, pageId, observeDocument, observeSignatures, entitlements,
            editPage, rotatePage, movePage, deletePage, placeSignature, removeSignature, deleteSignature,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.error_generic)
    var mode by remember { mutableStateOf<PageMode>(PageMode.View) }
    var showSignatures by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PageEvent.Closed -> onBack()
                PageEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    PageScreen(
        state = state,
        mode = mode,
        showSignatures = showSignatures,
        snackbar = snackbar,
        actions = PageActions(
            onBack = onBack,
            onFilter = { viewModel.onFilter(it) },
            onRotate = { viewModel.onRotate() },
            onMove = { viewModel.onMove(it) },
            onDelete = { viewModel.onDelete() },
            onCrop = onCrop,
            onModeChange = { mode = it },
            onAdjust = { b, c -> viewModel.onAdjust(b, c) },
            onSign = { if (state.isPro) showSignatures = true else onUpgrade() },
            onDismissSignatures = { showSignatures = false },
            onNewSignature = {
                showSignatures = false
                onNewSignature()
            },
            onDeleteSavedSignature = { viewModel.onDeleteSavedSignature(it) },
            onRemoveSignature = {
                showSignatures = false
                viewModel.onRemoveSignature()
            },
            onPlace = { path, placement ->
                viewModel.onPlaceSignature(path, placement.x, placement.y, placement.width)
                mode = PageMode.View
            },
        ),
        // Picking a saved signature closes the sheet and starts placing it.
        onPickSignature = {
            showSignatures = false
            mode = PageMode.Place(it)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    state: PageUiState,
    mode: PageMode = PageMode.View,
    showSignatures: Boolean = false,
    actions: PageActions = PageActions(),
    onPickSignature: (String) -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var placement by remember(mode) { mutableStateOf(Placement()) }
    val page = state.page
    val enabled = page != null && !state.isProcessing

    Scaffold(
        topBar = {
            if (mode is PageMode.Place) {
                TopAppBar(
                    title = { Text(stringResource(R.string.sign_place_title)) },
                    navigationIcon = {
                        IconButton(onClick = { actions.onModeChange(PageMode.View) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { if (page != null) Text(stringResource(R.string.page_of, state.index + 1, state.pageCount)) },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { confirmDelete = true }, enabled = enabled) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete_page))
                        }
                    },
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = Spacing.xs) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.s)) {
                    when (mode) {
                        PageMode.View -> ViewTools(state, enabled, actions)
                        PageMode.Adjust -> page?.let { AdjustPanel(it.id, it.adjustments, enabled, actions) }
                        is PageMode.Place -> PlacePanel(placement, enabled, onWidth = { placement = placement.copy(width = it) }) {
                            actions.onPlace(mode.signaturePath, placement)
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(Spacing.m), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else {
                PageCanvas(
                    path = page?.processedPath,
                    label = stringResource(R.string.page_number, state.index + 1),
                    placingPath = (mode as? PageMode.Place)?.signaturePath,
                    placement = placement,
                    onPlacementChange = { placement = it },
                )
                if (state.isProcessing) CircularProgressIndicator()
            }
        }
    }

    if (showSignatures) {
        ModalBottomSheet(onDismissRequest = actions.onDismissSignatures) {
            SignaturePicker(
                signatures = state.signatures,
                pageHasSignature = page?.adjustments?.signature != null,
                onPick = onPickSignature,
                onDelete = actions.onDeleteSavedSignature,
                onNew = actions.onNewSignature,
                onRemoveFromPage = actions.onRemoveSignature,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_page_title)) },
            text = {
                Text(stringResource(if (state.pageCount <= 1) R.string.delete_last_page_body else R.string.delete_page_body))
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ViewTools(state: PageUiState, enabled: Boolean, actions: PageActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(PageFilter.entries) { filter ->
            FilterChip(
                selected = state.page?.adjustments?.filter == filter,
                onClick = { actions.onFilter(filter) },
                enabled = enabled,
                label = { Text(stringResource(filter.label)) },
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs)) {
        LabeledAction(Icons.Outlined.ChevronLeft, stringResource(R.string.action_earlier), enabled && state.index > 0) { actions.onMove(-1) }
        LabeledAction(Icons.Outlined.RotateRight, stringResource(R.string.action_rotate), enabled, onClick = actions.onRotate)
        LabeledAction(Icons.Outlined.Crop, stringResource(R.string.action_crop), enabled, onClick = actions.onCrop)
        LabeledAction(Icons.Outlined.Tune, stringResource(R.string.action_adjust), enabled) { actions.onModeChange(PageMode.Adjust) }
        LabeledAction(Icons.Outlined.Draw, stringResource(R.string.action_sign), enabled, onClick = actions.onSign)
        LabeledAction(Icons.Outlined.ChevronRight, stringResource(R.string.action_later), enabled && state.index < state.pageCount - 1) {
            actions.onMove(1)
        }
    }
}

@Composable
private fun AdjustPanel(pageId: Long, adjustments: PageAdjustments, enabled: Boolean, actions: PageActions) {
    // Local while dragging; applied on release because each change re-renders the full page.
    var brightness by remember(pageId, adjustments.brightness) { mutableFloatStateOf(adjustments.brightness) }
    var contrast by remember(pageId, adjustments.contrast) { mutableFloatStateOf(adjustments.contrast) }
    Column(Modifier.padding(horizontal = Spacing.m)) {
        Text(stringResource(R.string.adjust_brightness), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            onValueChangeFinished = { actions.onAdjust(brightness, contrast) },
            valueRange = -1f..1f,
            enabled = enabled,
        )
        Text(stringResource(R.string.adjust_contrast), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = contrast,
            onValueChange = { contrast = it },
            onValueChangeFinished = { actions.onAdjust(brightness, contrast) },
            valueRange = EditPageUseCase.MIN_CONTRAST..EditPageUseCase.MAX_CONTRAST,
            enabled = enabled,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { actions.onAdjust(0f, 1f) }, enabled = enabled) { Text(stringResource(R.string.action_reset)) }
            Button(onClick = { actions.onModeChange(PageMode.View) }) { Text(stringResource(R.string.action_done)) }
        }
    }
}

@Composable
private fun PlacePanel(placement: Placement, enabled: Boolean, onWidth: (Float) -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.m)) {
        Text(stringResource(R.string.sign_place_hint), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sign_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.s))
        Slider(
            value = placement.width,
            onValueChange = onWidth,
            valueRange = PlaceSignatureUseCase.MIN_SIGNATURE_WIDTH..1f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onConfirm, enabled = enabled) { Text(stringResource(R.string.action_place_signature)) }
        }
    }
}

/** The page fitted to the available space; while placing, a draggable signature sits on top. */
@Composable
private fun PageCanvas(
    path: String?,
    label: String,
    placingPath: String?,
    placement: Placement,
    onPlacementChange: (Placement) -> Unit,
) {
    val aspect = rememberImageAspect(path)
    if (aspect == null) {
        FileImage(path, label, Modifier.fillMaxSize(), maxSide = 2000)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitsWidth = aspect > maxWidth / maxHeight
        val width = if (fitsWidth) maxWidth else maxHeight * aspect
        val height = if (fitsWidth) maxWidth / aspect else maxHeight
        Box(Modifier.size(width, height)) {
            FileImage(path, label, Modifier.fillMaxSize(), maxSide = 2000)
            if (placingPath != null) {
                SignatureOverlay(placingPath, placement, width.value, height.value, onPlacementChange)
            }
        }
    }
}

@Composable
private fun SignatureOverlay(
    path: String,
    placement: Placement,
    pageWidthDp: Float,
    pageHeightDp: Float,
    onChange: (Placement) -> Unit,
) {
    val signatureAspect = rememberImageAspect(path) ?: DEFAULT_SIGNATURE_ASPECT
    val density = LocalDensity.current
    val current by rememberUpdatedState(placement)
    val heightFraction = placement.width * pageWidthDp / signatureAspect / pageHeightDp
    val description = stringResource(R.string.sign_overlay_description)
    Box(
        Modifier
            .offset((placement.x * pageWidthDp).dp, (placement.y * pageHeightDp).dp)
            .size((placement.width * pageWidthDp).dp, (placement.width * pageWidthDp / signatureAspect).dp)
            .border(BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.primary))
            .semantics { contentDescription = description }
            .pointerInput(pageWidthDp, pageHeightDp) {
                // The gesture tracks its own position; several drag events can arrive between recompositions.
                var p = current
                detectDragGestures(onDragStart = { p = current }) { change, drag ->
                    change.consume()
                    val dx = with(density) { drag.x.toDp().value } / pageWidthDp
                    val dy = with(density) { drag.y.toDp().value } / pageHeightDp
                    p = p.copy(
                        x = (p.x + dx).coerceIn(0f, 1f - p.width),
                        y = (p.y + dy).coerceIn(0f, (1f - heightFraction).coerceAtLeast(0f)),
                    )
                    onChange(p)
                }
            },
    ) {
        FileImage(path, null, Modifier.fillMaxSize(), maxSide = 800, showBackground = false)
    }
}

@Composable
fun SignaturePicker(
    signatures: List<String>,
    pageHasSignature: Boolean,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onRemoveFromPage: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.sign_saved_title), style = MaterialTheme.typography.titleMedium)
        if (signatures.isEmpty()) {
            Text(
                stringResource(R.string.sign_saved_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(signatures, key = { it }) { path ->
                    val description = stringResource(R.string.sign_use_saved)
                    OutlinedCard(onClick = { onPick(path) }) {
                        Box {
                            FileImage(
                                path = path,
                                contentDescription = description,
                                modifier = Modifier
                                    .size(Sizes.signatureThumbWidth, Sizes.signatureThumbHeight)
                                    .background(FixedColors.paper, RoundedCornerShape(Spacing.s))
                                    .padding(Spacing.s),
                                maxSide = 400,
                                showBackground = false,
                            )
                            IconButton(onClick = { onDelete(path) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.sign_delete_saved))
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_new_signature)) }
        if (pageHasSignature) {
            OutlinedButton(onClick = onRemoveFromPage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_remove_signature))
            }
        }
    }
}

private const val DEFAULT_SIGNATURE_ASPECT = 3f

@Preview(name = "Page – viewer", showBackground = true)
@Composable
private fun PagePreview() = DocScanTheme {
    PageScreen(PageUiState(PreviewData.pages[1], index = 1, pageCount = 5, isLoading = false))
}

@Preview(name = "Page – applying change", showBackground = true)
@Composable
private fun PageProcessingPreview() = DocScanTheme {
    PageScreen(PageUiState(PreviewData.pages[0], index = 0, pageCount = 5, isLoading = false, isProcessing = true))
}

@Preview(name = "Page – brightness and contrast", showBackground = true)
@Composable
private fun PageAdjustPreview() = DocScanTheme {
    PageScreen(PageUiState(PreviewData.pages[0], index = 0, pageCount = 5, isLoading = false), mode = PageMode.Adjust)
}

@Preview(name = "Page – placing a signature", showBackground = true)
@Composable
private fun PagePlacePreview() = DocScanTheme {
    PageScreen(PageUiState(PreviewData.pages[0], index = 0, pageCount = 5, isLoading = false, isPro = true), mode = PageMode.Place(""))
}

@Preview(name = "Page – loading", showBackground = true)
@Composable
private fun PageLoadingPreview() = DocScanTheme {
    PageScreen(PageUiState())
}

@Preview(name = "Signature picker – saved", showBackground = true)
@Composable
private fun SignaturePickerPreview() = DocScanTheme {
    SignaturePicker(listOf("a", "b"), pageHasSignature = true, onPick = {}, onDelete = {}, onNew = {}, onRemoveFromPage = {})
}

@Preview(name = "Signature picker – none yet", showBackground = true)
@Composable
private fun SignaturePickerEmptyPreview() = DocScanTheme {
    SignaturePicker(emptyList(), pageHasSignature = false, onPick = {}, onDelete = {}, onNew = {}, onRemoveFromPage = {})
}
