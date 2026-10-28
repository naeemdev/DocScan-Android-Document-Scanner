package com.docscan.app.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.BuildConfig
import com.docscan.app.R
import com.docscan.app.domain.model.AppSettings
import com.docscan.app.domain.model.PageFilter
import com.docscan.app.domain.model.PageSize
import com.docscan.app.domain.model.PdfQuality
import com.docscan.app.domain.model.TitleDateStyle
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.components.label
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Spacing

@Composable
fun SettingsRoute(onUpgrade: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(settings, entitlements, deleteAllData) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onPageSize = viewModel::onPageSize,
            onPdfQuality = viewModel::onPdfQuality,
            onDefaultFilter = viewModel::onDefaultFilter,
            onTitlePrefix = viewModel::onTitlePrefix,
            onTitleDateStyle = viewModel::onTitleDateStyle,
            onWatermark = viewModel::onWatermark,
            onUpgrade = onUpgrade,
            onRestore = { viewModel.onRestore() },
            onDebugPro = viewModel::onDebugPro,
            onDeleteAll = { viewModel.onDeleteAll() },
        ),
        showDebug = BuildConfig.DEBUG,
    )
}

data class SettingsActions(
    val onPageSize: (PageSize) -> Unit = {},
    val onPdfQuality: (PdfQuality) -> Unit = {},
    val onDefaultFilter: (PageFilter) -> Unit = {},
    val onTitlePrefix: (String) -> Unit = {},
    val onTitleDateStyle: (TitleDateStyle) -> Unit = {},
    val onWatermark: (String) -> Unit = {},
    val onUpgrade: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDebugPro: (Boolean) -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    showDebug: Boolean = false
) {
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())) {
            ProSection(state.isPro, actions)

            OptionGroup(
                stringResource(R.string.settings_page_size),
                PageSize.entries,
                settings.pageSize,
                { stringResource(it.label) },
                actions.onPageSize
            )
            OptionGroup(
                stringResource(R.string.settings_pdf_quality),
                PdfQuality.entries,
                settings.pdfQuality,
                { stringResource(it.label) },
                actions.onPdfQuality
            )
            OptionGroup(
                stringResource(R.string.settings_default_filter),
                PageFilter.entries,
                settings.defaultFilter,
                { stringResource(it.label) },
                actions.onDefaultFilter,
            )

            SectionTitle(stringResource(R.string.settings_naming))
            OutlinedTextField(
                value = settings.titlePrefix,
                onValueChange = actions.onTitlePrefix,
                label = { Text(stringResource(R.string.settings_name_prefix)) },
                placeholder = { Text(stringResource(R.string.default_title_prefix)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m),
            )
            OptionGroup(
                null,
                TitleDateStyle.entries,
                settings.titleDateStyle,
                { stringResource(it.label) },
                actions.onTitleDateStyle
            )

            SectionTitle(stringResource(R.string.settings_watermark))
            if (state.isPro) {
                OutlinedTextField(
                    value = settings.watermarkText,
                    onValueChange = actions.onWatermark,
                    label = { Text(stringResource(R.string.settings_watermark_label)) },
                    supportingText = { Text(stringResource(R.string.settings_watermark_help)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.settings_watermark_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onUpgrade) { Text(stringResource(R.string.action_unlock)) }
                }
            }

            if (showDebug) {
                SectionTitle(stringResource(R.string.settings_debug))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.isPro,
                            role = Role.Switch,
                            onValueChange = actions.onDebugPro
                        )
                        .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_debug_pro),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(checked = state.isPro, onCheckedChange = null)
                }
            }

            SectionTitle(stringResource(R.string.settings_your_data))
            Text(
                stringResource(R.string.settings_data_local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
            OutlinedButton(
                onClick = { confirmDeleteAll = true },
                modifier = Modifier.padding(Spacing.m),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete_all)) }
        }
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; actions.onDeleteAll() }) {
                    Text(
                        stringResource(R.string.action_delete_all)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(
                        stringResource(R.string.action_cancel)
                    )
                }
            },
        )
    }
}

@Composable
private fun ProSection(isPro: Boolean, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.settings_pro))
    Text(
        stringResource(if (isPro) R.string.settings_pro_active else R.string.settings_pro_inactive),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Spacing.m),
    )
    Row(
        Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        if (!isPro) Button(onClick = actions.onUpgrade) { Text(stringResource(R.string.action_get_pro)) }
        TextButton(onClick = actions.onRestore) { Text(stringResource(R.string.paywall_restore)) }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.m, top = Spacing.l, bottom = Spacing.s),
    )
}

@Composable
private fun <T> OptionGroup(
    title: String?,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    title?.let { SectionTitle(it) }
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) })
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(
                    label(option),
                    Modifier.padding(start = Spacing.m),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Preview(name = "Settings – free", showBackground = true, heightDp = 1600)
@Composable
private fun SettingsPreview() = DocScanTheme { SettingsScreen(SettingsUiState()) }

@Preview(name = "Settings – Pro with watermark", showBackground = true, heightDp = 1600)
@Composable
private fun SettingsProPreview() = DocScanTheme {
    SettingsScreen(
        SettingsUiState(
            AppSettings(watermarkText = stringResource(R.string.preview_watermark)),
            isPro = true
        ), showDebug = true
    )
}

@Preview(
    name = "Settings – dark",
    showBackground = true,
    heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SettingsDarkPreview() = DocScanTheme(darkTheme = true) {
    SettingsScreen(
        SettingsUiState(
            AppSettings(
                PageSize.LETTER,
                PageFilter.BLACK_WHITE,
                PdfQuality.HIGH,
                titlePrefix = stringResource(R.string.preview_prefix)
            )
        )
    )
}
