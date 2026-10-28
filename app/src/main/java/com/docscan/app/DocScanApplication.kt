package com.docscan.app

import android.app.Application
import com.docscan.app.data.billing.CurrentActivity
import com.docscan.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DocScanApplication : Application() {
    private val currentActivity = CurrentActivity()
    val container: AppContainer by lazy { AppContainer(this, currentActivity) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(currentActivity)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { container.entitlements.refresh() }
    }
}
