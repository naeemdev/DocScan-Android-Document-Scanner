package com.docscan.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.docscan.app.R
import com.docscan.app.ui.crop.CropRoute
import com.docscan.app.ui.document.DocumentRoute
import com.docscan.app.ui.home.HomeRoute
import com.docscan.app.ui.ocr.OcrRoute
import com.docscan.app.ui.page.PageRoute
import com.docscan.app.ui.paywall.PaywallRoute
import com.docscan.app.ui.settings.SettingsRoute
import com.docscan.app.ui.signature.SignaturePadRoute
import com.docscan.app.ui.tools.ToolsRoute
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey
@Serializable
data object ToolsKey : NavKey
@Serializable
data object SettingsKey : NavKey
@Serializable
data class DocumentKey(val documentId: Long) : NavKey
@Serializable
data class PageKey(val documentId: Long, val pageId: Long) : NavKey
@Serializable
data class OcrKey(val documentId: Long) : NavKey
@Serializable
data class CropKey(val documentId: Long, val pageId: Long) : NavKey
@Serializable
data object SignaturePadKey : NavKey
@Serializable
data object PaywallKey : NavKey

private enum class TopLevel(val key: NavKey, @StringRes val label: Int, val icon: ImageVector) {
    Documents(HomeKey, R.string.nav_documents, Icons.Outlined.Description),
    Tools(ToolsKey, R.string.nav_tools, Icons.Outlined.GridView),
    Settings(SettingsKey, R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun DocScanApp() {
    val backStack = rememberNavBackStack(HomeKey)
    val current = backStack.lastOrNull()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (TopLevel.entries.any { it.key == current }) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.key,
                            onClick = { backStack.switchTab(item.key) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // Each entry gets its own ViewModelStore, so two documents never share a ViewModel.
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> {
                    HomeRoute(onOpenDocument = { backStack.add(DocumentKey(it)) })
                }
                entry<ToolsKey> { ToolsRoute(onOpenDocument = { backStack.add(DocumentKey(it)) }) }
                entry<SettingsKey> { SettingsRoute(onUpgrade = { backStack.add(PaywallKey) }) }
                entry<DocumentKey> { key ->
                    DocumentRoute(
                        documentId = key.documentId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenPage = { backStack.add(PageKey(key.documentId, it)) },
                        onExtractText = { backStack.add(OcrKey(key.documentId)) },
                    )
                }
                entry<PageKey> { key ->
                    PageRoute(
                        documentId = key.documentId,
                        pageId = key.pageId,
                        onBack = { backStack.removeLastOrNull() },
                        onCrop = { backStack.add(CropKey(key.documentId, key.pageId)) },
                        onNewSignature = { backStack.add(SignaturePadKey) },
                        onUpgrade = { backStack.add(PaywallKey) },
                    )
                }
                entry<OcrKey> { key ->
                    OcrRoute(key.documentId, onBack = { backStack.removeLastOrNull() })
                }
                entry<CropKey> { key ->
                    CropRoute(key.documentId, key.pageId, onBack = { backStack.removeLastOrNull() })
                }
                entry<SignaturePadKey> { SignaturePadRoute(onBack = { backStack.removeLastOrNull() }) }
                entry<PaywallKey> { PaywallRoute(onClose = { backStack.removeLastOrNull() }) }
            },
        )
    }
}

/** Tabs sit on top of Home, so Back from Tools or Settings returns to Documents. */
private fun NavBackStack<NavKey>.switchTab(tab: NavKey) {
    removeAll { it != HomeKey }
    if (tab != HomeKey) add(tab)
}
