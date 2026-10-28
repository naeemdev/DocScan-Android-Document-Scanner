package com.docscan.app.data.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.docscan.app.domain.model.Plan
import com.docscan.app.domain.model.PlanKind
import com.docscan.app.domain.model.PurchaseResult
import com.docscan.app.domain.repository.EntitlementRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play Billing. Products to create in Play Console:
 * - subscription [SUBSCRIPTION_ID] with base plans [MONTHLY_PLAN] and [YEARLY_PLAN]
 * - one-time product [LIFETIME_ID]
 *
 * shortcut: purchases are trusted on-device (no server verification of purchase tokens).
 * Add Play Developer API verification on a backend before fraud becomes worth the cost.
 */
class PlayBillingRepository(
    context: Context,
    private val currentActivity: CurrentActivity,
    private val debugBuild: Boolean,
) : EntitlementRepository, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val owned = MutableStateFlow(false)
    private val debugPro = MutableStateFlow(false)
    private val _plans = MutableStateFlow<List<Plan>?>(null)
    private val products = mutableMapOf<String, Pair<ProductDetails, String?>>()
    private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    override val isPro: StateFlow<Boolean> =
        combine(owned, debugPro) { bought, debug -> bought || (debugBuild && debug) }
            .stateIn(scope, SharingStarted.Eagerly, false)
    override val plans: StateFlow<List<Plan>?> = _plans.asStateFlow()

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun refresh() {
        if (!connect()) {
            _plans.value = emptyList()
            return
        }
        _plans.value = loadPlans()
        val purchases = listOf(ProductType.SUBS, ProductType.INAPP).flatMap { type ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build()
            ).purchasesList
        }
        owned.value = handle(purchases) == PurchaseResult.SUCCESS
    }

    override suspend fun purchase(planId: String): PurchaseResult {
        val activity = currentActivity.get() ?: return PurchaseResult.FAILED
        val (details, offerToken) = products[planId] ?: return PurchaseResult.FAILED
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { offerToken?.let(::setOfferToken) }
            .build()
        val outcome = CompletableDeferred<PurchaseResult>()
        pendingPurchase = outcome
        val launch = withContext(Dispatchers.Main) {
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams))
                    .build()
            )
        }
        return when (launch.responseCode) {
            BillingResponseCode.OK -> outcome.await()
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refresh()
                PurchaseResult.SUCCESS
            }

            else -> PurchaseResult.FAILED
        }.also { pendingPurchase = null }
    }

    override fun setDebugPro(enabled: Boolean) {
        if (debugBuild) debugPro.value = enabled
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            val outcome = when (result.responseCode) {
                BillingResponseCode.OK -> handle(purchases.orEmpty()).also {
                    if (it == PurchaseResult.SUCCESS) owned.value = true
                }

                BillingResponseCode.USER_CANCELED -> PurchaseResult.CANCELLED
                BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseResult.SUCCESS.also {
                    owned.value = true
                }

                else -> PurchaseResult.FAILED
            }
            pendingPurchase?.complete(outcome)
        }
    }

    /** Acknowledges new purchases (Play refunds unacknowledged ones after 3 days). */
    private suspend fun handle(purchases: List<Purchase>): PurchaseResult {
        val ours = purchases.filter { purchase -> purchase.products.any { it in PRODUCT_IDS } }
        ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken)
                        .build()
                )
            }
        return when {
            ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PurchaseResult.SUCCESS
            ours.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> PurchaseResult.PENDING
            else -> PurchaseResult.FAILED
        }
    }

    private suspend fun loadPlans(): List<Plan> {
        products.clear()
        val plans = mutableListOf<Plan>()
        query(SUBSCRIPTION_ID, ProductType.SUBS)?.let { details ->
            // Base plans only; promotional offers are not shown.
            details.subscriptionOfferDetails.orEmpty().filter { it.offerId == null }
                .forEach { offer ->
                    val kind = when (offer.basePlanId) {
                        MONTHLY_PLAN -> PlanKind.MONTHLY
                        YEARLY_PLAN -> PlanKind.YEARLY
                        else -> return@forEach
                    }
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                        ?: return@forEach
                    products[offer.basePlanId] = details to offer.offerToken
                    plans += Plan(offer.basePlanId, kind, price)
                }
        }
        query(LIFETIME_ID, ProductType.INAPP)?.let { details ->
            val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return@let
            products[LIFETIME_ID] = details to offer.offerToken
            plans += Plan(LIFETIME_ID, PlanKind.LIFETIME, offer.formattedPrice)
        }
        return plans.sortedBy { it.kind.ordinal }
    }

    private suspend fun query(productId: String, type: String): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(productId)
                        .setProductType(type).build()
                )
            )
            .build()
        return client.queryProductDetails(params).productDetailsList?.firstOrNull()
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    companion object {
        const val SUBSCRIPTION_ID = "docscan_pro"
        const val MONTHLY_PLAN = "monthly"
        const val YEARLY_PLAN = "yearly"
        const val LIFETIME_ID = "docscan_pro_lifetime"
        private val PRODUCT_IDS = setOf(SUBSCRIPTION_ID, LIFETIME_ID)
    }
}
