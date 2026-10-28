package com.docscan.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.docscan.app.DocScanApplication
import com.docscan.app.di.AppContainer

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: AppContainer.() -> VM): VM {
    val container = (LocalContext.current.applicationContext as DocScanApplication).container
    return viewModel(factory = viewModelFactory { initializer { container.create() } })
}
