package com.charles.ollama.client.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    private val backend: PurchaseBackend
) {
    val isPremium: StateFlow<Boolean> = backend.isPremium
    val isWebSyncPremium: StateFlow<Boolean> = backend.isWebSyncPremium
    val productDetails: StateFlow<Map<String, PremiumProductInfo>> = backend.productDetails

    /** True when the active backend requires sign-in before purchase (GitHub/Stripe). */
    val requiresSignInToPurchase: Boolean = backend.requiresSignInToPurchase

    fun initialize() = backend.initialize()
    fun refreshPurchases() = backend.refreshPurchases()
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean =
        backend.launchPurchase(activity, plan)
    fun restorePurchases(activity: Activity) = backend.restorePurchases(activity)
}
