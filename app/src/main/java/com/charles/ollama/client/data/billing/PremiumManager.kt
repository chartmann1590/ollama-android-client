package com.charles.ollama.client.data.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.charles.ollama.client.BuildConfig
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Google Play [BillingClient] and the single source of truth for the
 * user's premium (ad-free) status. Premium is granted when any of the products
 * in [PremiumProducts.allIds] is owned (subscriptions active, or the lifetime
 * one-time product purchased).
 *
 * The last-known premium flag is cached in SharedPreferences so ads stay hidden
 * on a cold start before Play responds. Purchases are acknowledged client-side;
 * there is no server-side receipt verification (acceptable for this app's risk
 * profile).
 */
@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isWebSyncPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false))
    val isWebSyncPremium: StateFlow<Boolean> = _isWebSyncPremium.asStateFlow()

    /** Loaded product details keyed by product id, used by the paywall UI. */
    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var connecting = false

    /** Begin (or re-establish) the Play connection. Safe to call repeatedly. */
    fun initialize() {
        if (billingClient.isReady || connecting) return
        connecting = true
        runCatching { billingClient.startConnection(this) }
            .onFailure {
                connecting = false
                Log.w(TAG, "startConnection failed", it)
            }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        connecting = false
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryProductDetails()
            refreshPurchases()
        } else {
            Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    override fun onBillingServiceDisconnected() {
        connecting = false
        // Reconnect lazily on the next initialize()/refresh call.
    }

    /** Re-query owned purchases — call on app foreground and after the paywall opens. */
    fun refreshPurchases() {
        if (!billingClient.isReady) {
            initialize()
            return
        }
        var anyOwned = false
        var anyWebSync = false
        var pending = 2
        val finish = {
            pending--
            if (pending == 0) {
                setPremium(anyOwned)
                setWebSyncPremium(anyWebSync)
            }
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        ) { _, purchases ->
            if (purchases.any { it.grantsPremium() }) anyOwned = true
            if (purchases.any { it.grantsWebSync() }) anyWebSync = true
            purchases.forEach { handlePurchase(it) }
            finish()
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { _, purchases ->
            if (purchases.any { it.grantsPremium() }) anyOwned = true
            purchases.forEach { handlePurchase(it) }
            finish()
        }
    }

    private fun queryProductDetails() {
        queryProductDetailsForType(ProductType.SUBS, PremiumProducts.subscriptionIds)
        queryProductDetailsForType(ProductType.INAPP, PremiumProducts.inAppIds)
    }

    private fun queryProductDetailsForType(type: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val products = ids.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(type)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = _productDetails.value.toMutableMap().apply {
                    details.forEach { put(it.productId, it) }
                }
            } else {
                Log.w(TAG, "queryProductDetails($type) failed: ${result.debugMessage}")
            }
        }
    }

    /** Launch the Play purchase dialog for [plan]. Requires a foreground Activity. */
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        val details = _productDetails.value[plan.productId]
        if (details == null) {
            // Not loaded yet — kick a refresh so a retry can succeed.
            queryProductDetails()
            return false
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (plan.isSubscription) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: return false
            productParamsBuilder.setOfferToken(offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            var granted = false
            var grantedWebSync = false
            purchases.forEach {
                if (it.grantsPremium()) granted = true
                if (it.grantsWebSync()) grantedWebSync = true
                handlePurchase(it)
            }
            if (granted) setPremium(true)
            if (grantedWebSync) setWebSyncPremium(true)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.grantsPremium()) return
        setPremium(true)
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { ack ->
                if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${ack.debugMessage}")
                }
            }
        }
    }

    private fun Purchase.grantsPremium(): Boolean =
        products.any { it in PremiumProducts.allIds } &&
            BillingSecurity.verifyPurchase(BuildConfig.PLAY_LICENSE_KEY, originalJson, signature)

    private fun Purchase.grantsWebSync(): Boolean =
        products.any { it in PremiumProducts.webSyncIds } &&
            BillingSecurity.verifyPurchase(BuildConfig.PLAY_LICENSE_KEY, originalJson, signature)

    private fun setPremium(premium: Boolean) {
        if (_isPremium.value == premium) return
        prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
        _isPremium.value = premium
    }

    private fun setWebSyncPremium(webSync: Boolean) {
        if (_isWebSyncPremium.value == webSync) return
        prefs.edit().putBoolean(KEY_IS_WEB_SYNC_PREMIUM, webSync).apply()
        _isWebSyncPremium.value = webSync
    }

    companion object {
        private const val TAG = "PremiumManager"
        private const val PREFS_NAME = "premium_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_IS_WEB_SYNC_PREMIUM = "is_web_sync_premium"
    }
}
