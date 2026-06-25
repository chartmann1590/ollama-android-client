package com.charles.ollama.client.data.billing

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.charles.ollama.client.ads.AdGate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubPaymentReturnHandler @Inject constructor(
    private val adGate: AdGate,
    private val premiumManager: PremiumManager,
) : PaymentReturnHandler {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != CALLBACK_SCHEME) return
        if (uri.host == CALLBACK_SUCCESS_HOST) {
            adGate.grantTemporaryAdFree(CHECKOUT_AD_FREE_GRACE_MS)
            premiumManager.refreshPurchases()
            mainHandler.postDelayed({ premiumManager.refreshPurchases() }, 2_000L)
            mainHandler.postDelayed({ premiumManager.refreshPurchases() }, 8_000L)
        }
    }

    companion object {
        private const val CALLBACK_SCHEME = "ollama-github"
        private const val CALLBACK_SUCCESS_HOST = "payment-success"
        private const val CHECKOUT_AD_FREE_GRACE_MS = 10L * 60 * 1000
    }
}
