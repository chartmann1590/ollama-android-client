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

@Singleton
class PlayPurchaseBackend @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchaseBackend, PurchasesUpdatedListener, BillingClientStateListener {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(readPremiumFlag())
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isWebSyncPremium = MutableStateFlow(readWebSyncPremiumFlag())
    override val isWebSyncPremium: StateFlow<Boolean> = _isWebSyncPremium.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, PremiumProductInfo>>(emptyMap())
    override val productDetails: StateFlow<Map<String, PremiumProductInfo>> = _productDetails.asStateFlow()

    private val rawProductDetails = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var connecting = false

    override fun initialize() {
        if (applyDebugForcedEntitlements()) return
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
    }

    override fun refreshPurchases() {
        if (applyDebugForcedEntitlements()) return
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
                details.forEach { rawProductDetails[it.productId] = it }
                _productDetails.value = rawProductDetails.mapValues { it.value.toPremiumProductInfo() }
            } else {
                Log.w(TAG, "queryProductDetails($type) failed: ${result.debugMessage}")
            }
        }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        val details = rawProductDetails[plan.productId]
        if (details == null) {
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

    private fun readPremiumFlag(): Boolean =
        prefs.getBoolean(KEY_IS_PREMIUM, false) || debugForcePremium()

    private fun readWebSyncPremiumFlag(): Boolean =
        prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false) || debugForceWebSyncPremium()

    private fun applyDebugForcedEntitlements(): Boolean {
        if (!BuildConfig.DEBUG) return false
        val premium = debugForcePremium()
        val webSync = debugForceWebSyncPremium()
        if (!premium && !webSync) return false
        setPremium(premium || webSync)
        setWebSyncPremium(webSync)
        return true
    }

    private fun debugForcePremium(): Boolean =
        BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_FORCE_PREMIUM, false)

    private fun debugForceWebSyncPremium(): Boolean =
        BuildConfig.DEBUG && prefs.getBoolean(KEY_DEBUG_FORCE_WEB_SYNC_PREMIUM, false)

    private fun ProductDetails.toPremiumProductInfo(): PremiumProductInfo {
        val subscriptionPrice = subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        val oneTimePrice = oneTimePurchaseOfferDetails
        return PremiumProductInfo(
            productId = productId,
            title = title,
            description = description,
            price = subscriptionPrice?.formattedPrice ?: oneTimePrice?.formattedPrice,
            currencyCode = null,
            period = subscriptionPrice?.billingPeriod?.takeIf { it.isNotBlank() },
            isSubscription = productType == ProductType.SUBS
        )
    }

    companion object {
        private const val TAG = "PlayPurchaseBackend"
        private const val PREFS_NAME = "premium_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_IS_WEB_SYNC_PREMIUM = "is_web_sync_premium"
        private const val KEY_DEBUG_FORCE_PREMIUM = "debug_force_premium"
        private const val KEY_DEBUG_FORCE_WEB_SYNC_PREMIUM = "debug_force_web_sync_premium"
    }
}
