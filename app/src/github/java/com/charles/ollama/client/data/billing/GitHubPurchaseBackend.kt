package com.charles.ollama.client.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.charles.ollama.client.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubPurchaseBackend @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchaseBackend {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isWebSyncPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false))
    override val isWebSyncPremium: StateFlow<Boolean> = _isWebSyncPremium.asStateFlow()

    override val productDetails: StateFlow<Map<String, PremiumProductInfo>> =
        MutableStateFlow(hardcodedProducts()).asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl: String = BuildConfig.SUPABASE_URL
    private val supabaseKey: String = BuildConfig.SUPABASE_ANON_KEY

    private val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: generateDeviceId()
    }

    override fun initialize() {
        val cached = prefs.getBoolean(KEY_IS_PREMIUM, false)
        val cachedWebSync = prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false)
        if (cached || cachedWebSync) {
            _isPremium.value = cached
            _isWebSyncPremium.value = cachedWebSync
        }
    }

    override fun refreshPurchases() {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.w(TAG, "Supabase not configured; using cached premium status")
            return
        }
        try {
            val url = "$supabaseUrl/functions/v1/get-premium-status?device_id=$deviceId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val premium = json.optBoolean("isPremium", false)
                    val webSync = json.optBoolean("isWebSyncPremium", false)
                    setPremium(premium)
                    setWebSyncPremium(webSync)
                }
            } else {
                Log.w(TAG, "refreshPurchases failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshPurchases error", e)
        }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.w(TAG, "Supabase not configured; cannot initiate purchase")
            return false
        }
        try {
            val json = JSONObject().apply {
                put("productId", plan.productId)
                put("deviceId", deviceId)
                put("successUrl", "${CALLBACK_SCHEME}://payment-success")
                put("cancelUrl", "${CALLBACK_SCHEME}://payment-cancelled")
            }
            val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$supabaseUrl/functions/v1/create-checkout-session")
                .addHeader("Authorization", "Bearer $supabaseKey")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val resultJson = JSONObject(responseBody)
                    val checkoutUrl = resultJson.optString("url")
                    if (checkoutUrl.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))
                        activity.startActivity(intent)
                        return true
                    }
                }
            } else {
                Log.w(TAG, "create-checkout-session failed: ${response.code} ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "launchPurchase error", e)
        }
        return false
    }

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

    private fun generateDeviceId(): String {
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun hardcodedProducts(): Map<String, PremiumProductInfo> = mapOf(
        PremiumProducts.WEBSYNC_YEARLY to PremiumProductInfo(
            productId = PremiumProducts.WEBSYNC_YEARLY,
            title = "Web Sync Yearly",
            description = "Unlimited web messages + no ads",
            price = "$14.99/yr",
            currencyCode = "USD",
            period = "year",
            isSubscription = true
        ),
        PremiumProducts.WEBSYNC_MONTHLY to PremiumProductInfo(
            productId = PremiumProducts.WEBSYNC_MONTHLY,
            title = "Web Sync Monthly",
            description = "Unlimited web messages + no ads",
            price = "$1.99/mo",
            currencyCode = "USD",
            period = "month",
            isSubscription = true
        ),
        PremiumProducts.YEARLY to PremiumProductInfo(
            productId = PremiumProducts.YEARLY,
            title = "Ad-Free Yearly",
            description = "Remove all ads — best value",
            price = "$9.99/yr",
            currencyCode = "USD",
            period = "year",
            isSubscription = true
        ),
        PremiumProducts.MONTHLY to PremiumProductInfo(
            productId = PremiumProducts.MONTHLY,
            title = "Ad-Free Monthly",
            description = "Remove all ads, cancel anytime",
            price = "$0.99/mo",
            currencyCode = "USD",
            period = "month",
            isSubscription = true
        ),
        PremiumProducts.LIFETIME to PremiumProductInfo(
            productId = PremiumProducts.LIFETIME,
            title = "Lifetime Ad-Free",
            description = "One payment, ad-free forever",
            price = "$19.99",
            currencyCode = "USD",
            period = null,
            isSubscription = false
        )
    )

    companion object {
        private const val TAG = "GitHubPurchaseBackend"
        private const val PREFS_NAME = "github_premium_prefs"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_IS_WEB_SYNC_PREMIUM = "is_web_sync_premium"
        private const val KEY_DEVICE_ID = "device_id"
        private const val CALLBACK_SCHEME = "ollama-github"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
