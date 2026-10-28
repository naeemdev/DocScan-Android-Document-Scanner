package com.docscan.app.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.app.domain.model.Plan
import com.docscan.app.domain.model.PlanKind
import com.docscan.app.domain.model.PurchaseResult
import com.docscan.app.domain.repository.EntitlementRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaywallUiState(
    /** Null while loading; empty when Play has nothing to sell right now. */
    val plans: List<Plan>? = null,
    val selectedPlanId: String? = null,
    val isPro: Boolean = false,
    val isBusy: Boolean = false,
) {
    val selectedPlan: Plan? get() = plans?.firstOrNull { it.id == selectedPlanId } ?: plans?.defaultPlan()
}

/** Yearly is the plan most people should pick, so it starts selected. */
private fun List<Plan>.defaultPlan(): Plan? = firstOrNull { it.kind == PlanKind.YEARLY } ?: firstOrNull()

sealed interface PaywallEvent {
    data object Purchased : PaywallEvent
    data object Pending : PaywallEvent
    data object Failed : PaywallEvent
    data object Restored : PaywallEvent
    data object NothingToRestore : PaywallEvent
}

class PaywallViewModel(private val entitlements: EntitlementRepository) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<PaywallUiState> =
        combine(entitlements.plans, entitlements.isPro, selected, busy) { plans, isPro, selectedId, isBusy ->
            PaywallUiState(plans, selectedId, isPro, isBusy)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState())

    init {
        viewModelScope.launch { entitlements.refresh() }
    }

    fun onSelect(planId: String) {
        selected.value = planId
    }

    fun onBuy() = viewModelScope.launch {
        val plan = state.value.selectedPlan ?: return@launch
        if (busy.value) return@launch
        busy.value = true
        when (entitlements.purchase(plan.id)) {
            PurchaseResult.SUCCESS -> _events.send(PaywallEvent.Purchased)
            PurchaseResult.PENDING -> _events.send(PaywallEvent.Pending)
            PurchaseResult.FAILED -> _events.send(PaywallEvent.Failed)
            PurchaseResult.CANCELLED -> Unit
        }
        busy.value = false
    }

    fun onRestore() = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        entitlements.refresh()
        _events.send(if (entitlements.isPro.value) PaywallEvent.Restored else PaywallEvent.NothingToRestore)
        busy.value = false
    }
}
