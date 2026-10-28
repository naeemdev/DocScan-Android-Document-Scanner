package com.docscan.app.data.service

import android.content.Context
import com.docscan.app.R
import com.docscan.app.domain.model.TitleDateStyle
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.service.DocumentNamer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AndroidDocumentNamer(
    private val context: Context,
    private val settings: SettingsRepository,
) : DocumentNamer {
    private val dateTimeFormat = formatter(R.string.default_title_date_pattern)
    private val dateFormat = formatter(R.string.default_title_date_only_pattern)

    override fun newScanTitle(epochMillis: Long): String {
        val current = settings.settings.value
        val format = if (current.titleDateStyle == TitleDateStyle.DATE) dateFormat else dateTimeFormat
        val prefix = current.titlePrefix.trim().ifEmpty { context.getString(R.string.default_title_prefix) }
        return context.getString(R.string.document_title_pattern, prefix, format.format(Instant.ofEpochMilli(epochMillis)))
    }

    private fun formatter(pattern: Int) =
        DateTimeFormatter.ofPattern(context.getString(pattern)).withZone(ZoneId.systemDefault())

    override fun mergedTitle(firstTitle: String, otherCount: Int): String =
        context.resources.getQuantityString(R.plurals.merged_document_title, otherCount, firstTitle, otherCount)
}
