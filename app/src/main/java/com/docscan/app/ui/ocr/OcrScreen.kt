package com.docscan.app.ui.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.ui.components.EmptyState
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.shareFiles
import com.docscan.app.ui.components.shareText
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun OcrRoute(documentId: Long, onBack: () -> Unit) {
    val viewModel = appViewModel { OcrViewModel(documentId, recognizeText, exportText) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.text_copied)
    val shareTitle = stringResource(R.string.share_chooser_title)
    val failed = stringResource(R.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.textFiles.collect { result ->
            result
                .onSuccess { context.shareFiles(listOf(it), "text/plain", shareTitle) }
                .onFailure { snackbar.showSnackbar(failed) }
        }
    }

    OcrScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onCopy = { text ->
            clipboard.setText(AnnotatedString(text))
            scope.launch { snackbar.showSnackbar(copied) }
        },
        onShare = { context.shareText(it, shareTitle) },
        onRetry = viewModel::retry,
        onSaveTxt = { viewModel.onSaveTxt(it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    state: OcrUiState,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onSaveTxt: (String) -> Unit = {},
) {
    val text = (state as? OcrUiState.Done)?.text?.takeIf { it.isNotBlank() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { text?.let(onCopy) }, enabled = text != null) {
                        Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy))
                    }
                    IconButton(onClick = { text?.let(onShare) }, enabled = text != null) {
                        Icon(Icons.Outlined.Share, stringResource(R.string.action_share_text))
                    }
                    IconButton(onClick = { text?.let(onSaveTxt) }, enabled = text != null) {
                        Icon(Icons.Outlined.Description, stringResource(R.string.action_save_txt))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier
            .padding(padding)
            .fillMaxSize()) {
            when {
                state is OcrUiState.Loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(
                        Spacing.m,
                        Alignment.CenterVertically
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.ocr_reading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                state is OcrUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        Icons.Outlined.ErrorOutline,
                        stringResource(R.string.ocr_error_title),
                        stringResource(R.string.ocr_error_body),
                        Modifier.weight(1f),
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(bottom = Spacing.xl)
                    ) { Text(stringResource(R.string.action_retry)) }
                }

                text == null -> EmptyState(
                    Icons.Outlined.TextFields,
                    stringResource(R.string.ocr_empty_title),
                    stringResource(R.string.ocr_empty_body),
                )

                else -> SelectionContainer {
                    Text(
                        text,
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.m),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Preview(name = "Text – result", showBackground = true)
@Composable
private fun OcrDonePreview() = DocScanTheme {
    OcrScreen(
        OcrUiState.Done(stringResource(R.string.preview_ocr_text)),
        {},
        {},
        {},
        {})
}

@Preview(name = "Text – reading", showBackground = true)
@Composable
private fun OcrLoadingPreview() = DocScanTheme { OcrScreen(OcrUiState.Loading, {}, {}, {}, {}) }

@Preview(name = "Text – nothing found", showBackground = true)
@Composable
private fun OcrEmptyPreview() = DocScanTheme { OcrScreen(OcrUiState.Done(""), {}, {}, {}, {}) }

@Preview(name = "Text – error", showBackground = true)
@Composable
private fun OcrErrorPreview() = DocScanTheme { OcrScreen(OcrUiState.Error, {}, {}, {}, {}) }
