package com.docscan.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.docscan.app.R
import com.docscan.app.domain.model.Document
import com.docscan.app.domain.model.DocumentSummary
import com.docscan.app.domain.model.Folder
import com.docscan.app.domain.model.Page
import com.docscan.app.domain.model.PageAdjustments
import com.docscan.app.domain.model.PageFilter

object PreviewData {
    private const val NOW = 1_761_600_000_000L
    private const val DAY = 86_400_000L

    @Composable
    fun summaries() = listOf(
        DocumentSummary(1, stringResource(R.string.preview_doc_lease), NOW, 6, null),
        DocumentSummary(2, stringResource(R.string.preview_doc_receipts), NOW - DAY, 3, null),
        DocumentSummary(3, stringResource(R.string.preview_doc_long), NOW - 3 * DAY, 1, null),
    )

    val pages = (0 until 5).map { index ->
        Page(
            id = index + 10L,
            documentId = 1,
            position = index,
            originalPath = "",
            processedPath = "",
            adjustments = PageAdjustments(filter = PageFilter.ENHANCE),
            ocrText = null,
        )
    }

    @Composable
    fun folders() = listOf(
        Folder(1, stringResource(R.string.preview_folder_receipts), 3),
        Folder(2, stringResource(R.string.preview_folder_ids), 0),
    )

    @Composable
    fun document() = Document(1, stringResource(R.string.preview_doc_lease), NOW, NOW, pages)
}
