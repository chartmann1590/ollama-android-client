package com.charles.ollama.client.data.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.data.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) : PurchaseBackend {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isWebSyncPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false))
    override val isWebSyncPremium: StateFlow<Boolean> = _isWebSyncPremium.asStateFlow()

    override val productDetails: StateFlow<Map<String, PremiumProductInfo>> =
        MutableStateFlow(hardcodedProducts()).asStateFlow()

    // GitHub flavor has no platform billing account, so purchases are linked to
    // a Firebase sign-in to stay restorable. The paywall reads this to explain
    // why sign-in is required. (Play returns the interface default, false.)
    override val requiresSignInToPurchase: Boolean = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Network must never run on the main thread (NetworkOnMainThreadException),
    // so all Supabase calls are dispatched here. SupervisorJob keeps the scope
    // alive across individual call failures for this app-lifetime singleton.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val supabaseUrl: String = BuildConfig.SUPABASE_URL
    private val supabaseKey: String = BuildConfig.SUPABASE_ANON_KEY

    /** The Firebase account the entitlement is tied to, or null when signed out. */
    private fun currentAccountId(): String? = authRepository.currentUser.value?.uid

    init {
        // Entitlements are tied to the signed-in Firebase account (so they
        // restore across reinstalls and devices, matching the Play experience).
        // Re-evaluate whenever the signed-in user changes: fetch on sign-in,
        // lock everything down on sign-out.
        scope.launch {
            authRepository.currentUser.collect { user ->
                val uid = user?.uid
                if (uid == null) {
                    clearAccountCache()
                    setPremium(false)
                    setWebSyncPremium(false)
                } else {
                    fetchStatus(uid)
                }
            }
        }
    }

    override fun initialize() {
        // Restore cached flags only if they belong to the currently signed-in
        // account; otherwise stay locked until a refresh confirms entitlement.
        val uid = currentAccountId()
        if (uid != null && uid == prefs.getString(KEY_CACHED_UID, null)) {
            _isPremium.value = prefs.getBoolean(KEY_IS_PREMIUM, false)
            _isWebSyncPremium.value = prefs.getBoolean(KEY_IS_WEB_SYNC_PREMIUM, false)
        } else {
            _isPremium.value = false
            _isWebSyncPremium.value = false
        }
    }

    override fun refreshPurchases() {
        val uid = currentAccountId()
        if (uid == null) {
            // Not signed in — there is no account to restore entitlements against.
            setPremium(false)
            setWebSyncPremium(false)
            return
        }
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.w(TAG, "Supabase not configured; using cached premium status")
            return
        }
        scope.launch { fetchStatus(uid) }
    }

    /** Query Supabase for the entitlement status of [accountId] (a Firebase UID). */
    private suspend fun fetchStatus(accountId: String) {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) return
        try {
            val url = "$supabaseUrl/functions/v1/get-premium-status?device_id=$accountId"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    setPremium(json.optBoolean("isPremium", false))
                    setWebSyncPremium(json.optBoolean("isWebSyncPremium", false))
                    prefs.edit().putString(KEY_CACHED_UID, accountId).apply()
                }
            } else {
                Log.w(TAG, "fetchStatus failed: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStatus error", e)
        }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
            Log.w(TAG, "Supabase not configured; cannot initiate purchase")
            toast(activity, "Purchases are not available in this build.")
            return false
        }
        // The network round-trip to create the Stripe Checkout session runs off
        // the main thread; the browser intent is then launched back on the main
        // thread. The Boolean return is advisory only (the UI ignores it) since
        // the real result is asynchronous.
        scope.launch {
            // Purchases are tied to a Firebase account so they restore across
            // reinstalls/devices. Require sign-in first; if the user cancels or
            // it fails, abort without opening checkout.
            val accountId = currentAccountId() ?: run {
                val signedIn = try {
                    withContext(Dispatchers.Main) { authRepository.signInWithGoogle(activity).uid }
                } catch (e: Exception) {
                    Log.w(TAG, "sign-in required for purchase was not completed", e)
                    toast(activity, "Sign in with Google to purchase — this links your purchase to your account so it restores after reinstalling.")
                    return@launch
                }
                signedIn
            }
            try {
                val json = JSONObject().apply {
                    put("productId", plan.productId)
                    put("deviceId", accountId)
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
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val checkoutUrl = JSONObject(responseBody).optString("url")
                    if (checkoutUrl.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            activity.startActivity(intent)
                        }
                        return@launch
                    }
                    Log.w(TAG, "create-checkout-session returned no url")
                } else {
                    Log.w(TAG, "create-checkout-session failed: ${response.code} $responseBody")
                }
                toast(activity, "Could not start checkout. Please try again.")
            } catch (e: Exception) {
                Log.e(TAG, "launchPurchase error", e)
                toast(activity, "Could not start checkout. Please try again.")
            }
        }
        return true
    }

    // Safe to call from any thread — always shows on the main looper.
    private fun toast(activity: Activity, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
        }
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

    /** Forget the locally-cached entitlement (used on sign-out). */
    private fun clearAccountCache() {
        prefs.edit()
            .remove(KEY_CACHED_UID)
            .putBoolean(KEY_IS_PREMIUM, false)
            .putBoolean(KEY_IS_WEB_SYNC_PREMIUM, false)
            .apply()
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
        private const val KEY_CACHED_UID = "cached_account_uid"
        private const val CALLBACK_SCHEME = "ollama-github"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
