package com.charles.ollama.client.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.ProductDetails
import com.charles.ollama.client.data.billing.PremiumManager
import com.charles.ollama.client.data.billing.PremiumPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** A single paywall option with a display price resolved from Play. */
data class PlanOption(
    val plan: PremiumPlan,
    val title: String,
    val tagline: String,
    val price: String?,
    val highlight: Boolean
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val premiumManager: PremiumManager
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium
    val productDetails: StateFlow<Map<String, ProductDetails>> = premiumManager.productDetails

    init {
        // Make sure prices and ownership are fresh when the paywall opens.
        premiumManager.refreshPurchases()
    }

    fun planOptions(details: Map<String, ProductDetails>): List<PlanOption> = listOf(
        PlanOption(
            plan = PremiumPlan.YEARLY,
            title = "Yearly",
            tagline = "Best value — save vs monthly",
            price = details[PremiumPlan.YEARLY.productId]?.let { displayPrice(it, subscription = true) },
            highlight = true
        ),
        PlanOption(
            plan = PremiumPlan.MONTHLY,
            title = "Monthly",
            tagline = "Cancel anytime",
            price = details[PremiumPlan.MONTHLY.productId]?.let { displayPrice(it, subscription = true) },
            highlight = false
        ),
        PlanOption(
            plan = PremiumPlan.LIFETIME,
            title = "Lifetime",
            tagline = "One payment, ad-free forever",
            price = details[PremiumPlan.LIFETIME.productId]?.let { displayPrice(it, subscription = false) },
            highlight = false
        )
    )

    fun purchase(activity: Activity, plan: PremiumPlan): Boolean =
        premiumManager.launchPurchase(activity, plan)

    fun restore() = premiumManager.refreshPurchases()

    private fun displayPrice(details: ProductDetails, subscription: Boolean): String? =
        if (subscription) {
            details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice
        } else {
            details.oneTimePurchaseOfferDetails?.formattedPrice
        }
}
