package com.docscan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.AppSettings
import com.docscan.app.domain.model.MAX_PREFIX_LENGTH
import com.docscan.app.domain.model.MAX_WATERMARK_LENGTH
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality
import com.docscan.app.domain.model.TitleDateStyle
import com.docscan.app.domain.repository.EntitlementRepository
import com.docscan.app.domain.repository.SettingsRepository
import com.docscan.app.domain.usecase.DeleteAllDataUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isPro: Boolean = false,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val entitlements: EntitlementRepository,
    private val deleteAllData: DeleteAllDataUseCase,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(repository.settings, entitlements.isPro) { settings, isPro -> SettingsUiState(settings, isPro) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(repository.settings.value))

    private fun update(change: (AppSettings) -> AppSettings) = repository.update(change(repository.settings.value))

    fun onPageSize(size: PageSize) = update { it.copy(pageSize = size) }

    fun onPdfQuality(quality: PdfQuality) = update { it.copy(pdfQuality = quality) }

    fun onDefaultFilter(filter: PageFilter) = update { it.copy(defaultFilter = filter) }

    fun onTitlePrefix(prefix: String) = update { it.copy(titlePrefix = prefix.take(MAX_PREFIX_LENGTH)) }

    fun onTitleDateStyle(style: TitleDateStyle) = update { it.copy(titleDateStyle = style) }

    fun onWatermark(text: String) {
        if (entitlements.isPro.value) update { it.copy(watermarkText = text.take(MAX_WATERMARK_LENGTH)) }
    }

    fun onRestore() = viewModelScope.launch { entitlements.refresh() }

    fun onDebugPro(enabled: Boolean) = entitlements.setDebugPro(enabled)

    fun onDeleteAll() = viewModelScope.launch { deleteAllData() }
}
