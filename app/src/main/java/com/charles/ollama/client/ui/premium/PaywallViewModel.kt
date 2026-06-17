package com.charles.ollama.client.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.charles.ollama.client.data.billing.PremiumManager
import com.charles.ollama.client.data.billing.PremiumPlan
import com.charles.ollama.client.data.billing.PremiumProductInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class PlanOption(
    val plan: PremiumPlan,
    val title: String,
    val tagline: String,
    val price: String?,
    val highlight: Boolean,
    val fallbackPrice: String? = null
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val premiumManager: PremiumManager
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium
    val isWebSyncPremium: StateFlow<Boolean> = premiumManager.isWebSyncPremium
    val productDetails: StateFlow<Map<String, PremiumProductInfo>> = premiumManager.productDetails

    /** GitHub/Stripe backend requires Google sign-in before checkout; Play does not. */
    val requiresSignInToPurchase: Boolean = premiumManager.requiresSignInToPurchase

    init {
        premiumManager.refreshPurchases()
    }

    fun planOptions(details: Map<String, PremiumProductInfo>): List<PlanOption> = listOf(
        PlanOption(
            plan = PremiumPlan.WEBSYNC_YEARLY,
            title = "Web Sync Yearly",
            tagline = "Unlimited web messages + no ads — best value",
            price = details[PremiumPlan.WEBSYNC_YEARLY.productId]?.price,
            highlight = true,
            fallbackPrice = "$14.99/yr"
        ),
        PlanOption(
            plan = PremiumPlan.WEBSYNC_MONTHLY,
            title = "Web Sync Monthly",
            tagline = "Unlimited web messages + no ads",
            price = details[PremiumPlan.WEBSYNC_MONTHLY.productId]?.price,
            highlight = false,
            fallbackPrice = "$1.99/mo"
        ),
        PlanOption(
            plan = PremiumPlan.YEARLY,
            title = "Ad-Free Yearly",
            tagline = "Remove all ads — best value",
            price = details[PremiumPlan.YEARLY.productId]?.price,
            highlight = false
        ),
        PlanOption(
            plan = PremiumPlan.MONTHLY,
            title = "Ad-Free Monthly",
            tagline = "Remove all ads, cancel anytime",
            price = details[PremiumPlan.MONTHLY.productId]?.price,
            highlight = false
        ),
        PlanOption(
            plan = PremiumPlan.LIFETIME,
            title = "Lifetime Ad-Free",
            tagline = "One payment, ad-free forever",
            price = details[PremiumPlan.LIFETIME.productId]?.price,
            highlight = false
        )
    )

    fun purchase(activity: Activity, plan: PremiumPlan): Boolean =
        premiumManager.launchPurchase(activity, plan)

    fun restore() = premiumManager.refreshPurchases()
}
