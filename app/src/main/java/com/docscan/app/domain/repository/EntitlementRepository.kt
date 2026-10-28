package com.docscan.app.domain.repository

import com.docscan.app.domain.model.Plan
import com.docscan.app.domain.model.PurchaseResult
import kotlinx.coroutines.flow.StateFlow

/** Who has Pro, and how to buy it. */
interface EntitlementRepository {
    val isPro: StateFlow<Boolean>
    /** Null while loading; empty when the store has nothing to sell (offline, not configured). */
    val plans: StateFlow<List<Plan>?>
    /** Reloads plans and owned purchases; also serves as "restore purchases". */
    suspend fun refresh()
    suspend fun purchase(planId: String): PurchaseResult
    /** Debug builds only: pretend to own Pro so gated features can be tested. Ignored in release. */
    fun setDebugPro(enabled: Boolean)
}
