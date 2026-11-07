package com.charles.ollama.client.data.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.amazon.device.iap.model.ProductDataResponse
import com.amazon.device.iap.model.ProductType
import com.amazon.device.iap.model.PurchaseResponse
import com.amazon.device.iap.model.PurchaseUpdatesResponse
import com.amazon.device.iap.model.Receipt
import com.amazon.device.iap.model.UserDataResponse
import com.charles.ollama.client.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Amazon Appstore flavor: Amazon In-App Purchasing (Appstore SDK 3.x) behind the same
 * [PurchaseBackend] the play (Play Billing) and github (Stripe) flavors implement. Fire devices
 * have no Google Play services and Amazon requires its own IAP for digital unlocks, so no
 * sign-in is needed: purchases belong to the Amazon account, like Play.
 *
 * Same products and prices as Play (term SKUs = the Play product ids, see AMAZON-IAP-SKUS.md):
 *  - subscriptions, parent [PARENT_AD_FREE]: premium_monthly $0.99, premium_yearly $9.99
 *  - subscriptions, parent [PARENT_WEB_SYNC]: websync_monthly $1.99, websync_yearly $14.99
 *  - entitlement: premium_lifetime $19.99
 */
@Singleton
class AmazonPurchaseBackend @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchaseBackend, PurchasingListener {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(readFlag(KEY_IS_PREMIUM, KEY_DEBUG_FORCE_PREMIUM))
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isWebSyncPremium = MutableStateFlow(readFlag(KEY_IS_WEB_SYNC_PREMIUM, KEY_DEBUG_FORCE_WEB_SYNC_PREMIUM))
    override val isWebSyncPremium: StateFlow<Boolean> = _isWebSyncPremium.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, PremiumProductInfo>>(emptyMap())
    override val productDetails: StateFlow<Map<String, PremiumProductInfo>> = _productDetails.asStateFlow()

    @Volatile private var registered = false
    private var pagePremium = false
    private var pageWebSync = false

    override fun initialize() {
        if (registered) return
        registered = try {
            PurchasingService.registerListener(context.applicationContext, this)
            PurchasingService.getUserData()
            PurchasingService.getProductData(PremiumProducts.allIds.toSet())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Amazon IAP unavailable: ${t.message}")
            false
        }
        if (registered) refreshPurchases()
    }

    override fun refreshPurchases() {
        if (!registered) { initialize(); return }
        pagePremium = false
        pageWebSync = false
        try { PurchasingService.getPurchaseUpdates(true) } catch (t: Throwable) { Log.w(TAG, "getPurchaseUpdates: ${t.message}") }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        if (!registered) initialize()
        if (!registered) return false
        Log.i(TAG, "purchase(${plan.productId})")
        return try {
            PurchasingService.purchase(plan.productId)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "purchase failed to start: ${t.message}")
            false
        }
    }

    private fun Receipt.matches(): String? {
        if (isCanceled) return null
        val ids = listOfNotNull(sku, termSku)
        return ids.firstOrNull { it in PremiumProducts.allIds }
    }

    override fun onUserDataResponse(response: UserDataResponse) {
        Log.i(TAG, "onUserDataResponse: ${response.requestStatus} marketplace=${response.userData?.marketplace}")
    }

    override fun onProductDataResponse(response: ProductDataResponse) {
        Log.i(TAG, "onProductDataResponse: ${response.requestStatus} products=${response.productData?.keys} unavailable=${response.unavailableSkus}")
        if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) return
        _productDetails.value = response.productData.orEmpty().mapValues { (sku, p) ->
            val sub = p.productType == ProductType.SUBSCRIPTION
            PremiumProductInfo(
                productId = sku,
                title = p.title,
                description = p.description,
                price = p.price,
                currencyCode = null,
                period = when (sku) {
                    PremiumProducts.MONTHLY, PremiumProducts.WEBSYNC_MONTHLY -> "P1M"
                    PremiumProducts.YEARLY, PremiumProducts.WEBSYNC_YEARLY -> "P1Y"
                    else -> null
                },
                isSubscription = sub
            )
        }
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        Log.i(TAG, "onPurchaseResponse: ${response.requestStatus}")
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL -> {
                val r = response.receipt ?: return
                val id = r.matches()
                if (id != null) {
                    setFlag(KEY_IS_PREMIUM, true, _isPremium)
                    if (id in PremiumProducts.webSyncIds) setFlag(KEY_IS_WEB_SYNC_PREMIUM, true, _isWebSyncPremium)
                }
                PurchasingService.notifyFulfillment(r.receiptId, FulfillmentResult.FULFILLED)
            }
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> refreshPurchases()
            else -> Unit // FAILED (incl. user cancel), INVALID_SKU, NOT_SUPPORTED, PENDING
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            Log.w(TAG, "onPurchaseUpdatesResponse: ${response.requestStatus}")
            return
        }
        for (r in response.receipts) {
            val id = r.matches() ?: continue
            pagePremium = true
            if (id in PremiumProducts.webSyncIds) pageWebSync = true
        }
        if (response.hasMore()) {
            PurchasingService.getPurchaseUpdates(false)
            return
        }
        if (debugForced()) return
        setFlag(KEY_IS_PREMIUM, pagePremium, _isPremium)
        setFlag(KEY_IS_WEB_SYNC_PREMIUM, pageWebSync, _isWebSyncPremium)
    }

    private fun setFlag(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, value).apply()
        flow.value = value
    }

    private fun readFlag(key: String, debugKey: String): Boolean =
        prefs.getBoolean(key, false) || (BuildConfig.DEBUG && prefs.getBoolean(debugKey, false))

    private fun debugForced(): Boolean = BuildConfig.DEBUG &&
        (prefs.getBoolean(KEY_DEBUG_FORCE_PREMIUM, false) || prefs.getBoolean(KEY_DEBUG_FORCE_WEB_SYNC_PREMIUM, false))

    companion object {
        private const val TAG = "AmazonPurchaseBackend"
        const val PARENT_AD_FREE = "premium_adfree"
        const val PARENT_WEB_SYNC = "premium_websync"
        private const val PREFS_NAME = "premium_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_IS_WEB_SYNC_PREMIUM = "is_web_sync_premium"
        private const val KEY_DEBUG_FORCE_PREMIUM = "debug_force_premium"
        private const val KEY_DEBUG_FORCE_WEB_SYNC_PREMIUM = "debug_force_web_sync_premium"
    }
}
