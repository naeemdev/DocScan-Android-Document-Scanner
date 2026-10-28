package com.docscan.app.ui.tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.rememberDocumentScanner
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Spacing
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@Composable
fun ToolsRoute(onOpenDocument: (Long) -> Unit) {
    val viewModel = appViewModel { ToolsViewModel(importPdf, createIdCard) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scannerFailed = stringResource(R.string.error_scanner)
    val pdfFailed = stringResource(R.string.error_pdf)
    val failed = stringResource(R.string.error_generic)
    val copied = stringResource(R.string.text_copied)
    val noApp = stringResource(R.string.error_no_app)
    val codeScanner = remember { GmsBarcodeScanning.getClient(context) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPdfPicked(it.toString()) }
    }
    val scanIdSide = rememberDocumentScanner(
        onScanned = { viewModel.onIdCardSideScanned(it.first()) },
        onError = { scope.launch { snackbar.showSnackbar(scannerFailed) } },
        pageLimit = 1,
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ToolsEvent.OpenDocument -> onOpenDocument(event.id)
                ToolsEvent.PdfFailed -> snackbar.showSnackbar(pdfFailed)
                ToolsEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    ToolsScreen(
        state = state,
        snackbar = snackbar,
        onScanQr = {
            codeScanner.startScan()
                .addOnSuccessListener { viewModel.onCodeScanned(it.rawValue) }
                .addOnFailureListener { scope.launch { snackbar.showSnackbar(scannerFailed) } }
        },
        onImportPdf = { pickPdf.launch(arrayOf("application/pdf")) },
        onScanIdCard = scanIdSide,
        onCancelIdCard = viewModel::onIdCardCancelled,
        onCopy = {
            clipboard.setText(AnnotatedString(it))
            scope.launch { snackbar.showSnackbar(copied) }
        },
        onOpenLink = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
            } catch (e: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(noApp) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    state: ToolsUiState,
    onScanQr: () -> Unit,
    onImportPdf: () -> Unit,
    onScanIdCard: () -> Unit,
    onCancelIdCard: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_tools)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ToolCard(
                    Icons.Outlined.Badge,
                    R.string.tool_id_title,
                    R.string.tool_id_body,
                    !state.isWorking,
                    onScanIdCard
                )
                ToolCard(
                    Icons.Outlined.PictureAsPdf,
                    R.string.tool_pdf_title,
                    R.string.tool_pdf_body,
                    !state.isWorking,
                    onImportPdf
                )
                ToolCard(
                    Icons.Outlined.QrCodeScanner,
                    R.string.tool_qr_title,
                    R.string.tool_qr_body,
                    true,
                    onScanQr
                )
                state.lastCode?.let { code -> CodeResult(code, onCopy, onOpenLink) }
            }
        }
    }

    if (state.idCardFront != null) {
        AlertDialog(
            onDismissRequest = onCancelIdCard,
            title = { Text(stringResource(R.string.id_back_title)) },
            text = { Text(stringResource(R.string.id_back_body)) },
            confirmButton = { TextButton(onClick = onScanIdCard) { Text(stringResource(R.string.action_scan_back)) } },
            dismissButton = { TextButton(onClick = onCancelIdCard) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: Int,
    body: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeResult(code: String, onCopy: (String) -> Unit, onOpenLink: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(R.string.tool_qr_result),
                style = MaterialTheme.typography.labelLarge
            )
            Text(code, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = { onCopy(code) }) { Text(stringResource(R.string.action_copy)) }
                if (URLUtil.isNetworkUrl(code)) {
                    TextButton(onClick = { onOpenLink(code) }) { Text(stringResource(R.string.action_open_link)) }
                }
            }
        }
    }
}

@Preview(name = "Tools", showBackground = true)
@Composable
private fun ToolsPreview() = DocScanTheme { ToolsScreen(ToolsUiState(), {}, {}, {}, {}, {}, {}) }

@Preview(name = "Tools – QR result", showBackground = true)
@Composable
private fun ToolsResultPreview() = DocScanTheme {
    ToolsScreen(
        ToolsUiState(lastCode = stringResource(R.string.preview_qr_value)),
        {},
        {},
        {},
        {},
        {},
        {})
}

@Preview(name = "Tools – importing PDF", showBackground = true)
@Composable
private fun ToolsWorkingPreview() =
    DocScanTheme { ToolsScreen(ToolsUiState(isWorking = true), {}, {}, {}, {}, {}, {}) }

@Preview(name = "Tools – ID card, waiting for back", showBackground = true)
@Composable
private fun ToolsIdCardPreview() =
    DocScanTheme { ToolsScreen(ToolsUiState(idCardFront = "front"), {}, {}, {}, {}, {}, {}) }
