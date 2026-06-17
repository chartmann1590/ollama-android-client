package com.charles.ollama.client.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class PremiumProductInfo(
    val productId: String,
    val title: String?,
    val description: String?,
    val price: String?,
    val currencyCode: String?,
    val period: String?,
    val isSubscription: Boolean
)

interface PurchaseBackend {
    val isPremium: StateFlow<Boolean>
    val isWebSyncPremium: StateFlow<Boolean>
    val productDetails: StateFlow<Map<String, PremiumProductInfo>>

    /**
     * Whether the user must sign in before a purchase can be made. Platform
     * billing (Play) ties purchases to the store account automatically, so it
     * stays false. The GitHub/Stripe backend overrides this to true because it
     * links entitlements to a Firebase account for cross-device restore.
     * Defaulted here so non-Stripe backends need no change.
     */
    val requiresSignInToPurchase: Boolean get() = false

    fun initialize()
    fun refreshPurchases()
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean
}
