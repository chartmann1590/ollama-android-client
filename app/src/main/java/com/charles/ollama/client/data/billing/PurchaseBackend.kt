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

    fun initialize()
    fun refreshPurchases()
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean
}
