package com.docscan.app.data.repository

import android.content.SharedPreferences
import com.docscan.app.domain.model.AppSettings
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPrefsSettingsRepository(private val prefs: SharedPreferences) : SettingsRepository {
    private val state = MutableStateFlow(read())
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PAGE_SIZE, settings.pageSize.name)
            .putString(KEY_FILTER, settings.defaultFilter.name)
            .putString(KEY_QUALITY, settings.pdfQuality.name)
            .putString(KEY_PREFIX, settings.titlePrefix)
            .putString(KEY_DATE_STYLE, settings.titleDateStyle.name)
            .putString(KEY_WATERMARK, settings.watermarkText)
            .apply()
        state.value = settings
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            pageSize = enumOr(prefs.getString(KEY_PAGE_SIZE, null), defaults.pageSize),
            defaultFilter = enumOr(prefs.getString(KEY_FILTER, null), defaults.defaultFilter),
            pdfQuality = enumOr(prefs.getString(KEY_QUALITY, null), defaults.pdfQuality),
            titlePrefix = prefs.getString(KEY_PREFIX, null) ?: defaults.titlePrefix,
            titleDateStyle = enumOr(prefs.getString(KEY_DATE_STYLE, null), defaults.titleDateStyle),
            watermarkText = prefs.getString(KEY_WATERMARK, null) ?: defaults.watermarkText,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

    private companion object {
        const val KEY_PAGE_SIZE = "page_size"
        const val KEY_FILTER = "default_filter"
        const val KEY_QUALITY = "pdf_quality"
        const val KEY_PREFIX = "title_prefix"
        const val KEY_DATE_STYLE = "title_date_style"
        const val KEY_WATERMARK = "watermark_text"
    }
}
