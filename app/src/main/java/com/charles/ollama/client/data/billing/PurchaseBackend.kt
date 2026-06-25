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
     * stays false. Account-linked external billing backends override this
     * because they link entitlements to a Firebase account for cross-device
     * restore.
     * Defaulted here so platform billing backends need no change.
     */
    val requiresSignInToPurchase: Boolean get() = false

    fun initialize()
    fun refreshPurchases()
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean

    /**
     * Restore previously-purchased entitlements. Platform billing (Play) just
     * re-queries the store account, so the default simply refreshes. The
     * External billing backends may override this to first prompt sign-in when signed
     * out (entitlements are tied to a Firebase account), using [activity] to
     * launch the credential flow.
     */
    fun restorePurchases(activity: Activity) = refreshPurchases()

    /**
     * Best-effort revoke of server-side entitlements during account deletion.
     * Play purchases are owned by the Google account (nothing to delete here),
     * so the default is a no-op. External billing backends may override this to
     * cancel any live subscription and remove the account's purchase rows.
     */
    suspend fun deleteRemoteEntitlements() {}
}
