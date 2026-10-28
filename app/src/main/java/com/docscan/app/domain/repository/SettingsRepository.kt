package com.docscan.app.domain.repository

import com.docscan.app.domain.model.AppSettings
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(settings: AppSettings)
}
