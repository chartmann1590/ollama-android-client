package com.charles.ollama.client.data.billing

/**
 * Product catalog for the Remove-Ads / Premium upgrade.
 *
 * These IDs must match the products configured in the Google Play Console:
 *  - [MONTHLY] and [YEARLY] are subscriptions (each needs a base plan).
 *  - [LIFETIME] is a one-time (managed) in-app product.
 */
object PremiumProducts {
    const val MONTHLY = "premium_monthly"
    const val YEARLY = "premium_yearly"
    const val LIFETIME = "premium_lifetime"

    val subscriptionIds = listOf(MONTHLY, YEARLY)
    val inAppIds = listOf(LIFETIME)

    /** All IDs that, if owned/active, grant ad-free premium. */
    val allIds = subscriptionIds + inAppIds
}

/** A purchasable plan, used by the paywall UI and the billing flow. */
enum class PremiumPlan(val productId: String, val isSubscription: Boolean) {
    MONTHLY(PremiumProducts.MONTHLY, isSubscription = true),
    YEARLY(PremiumProducts.YEARLY, isSubscription = true),
    LIFETIME(PremiumProducts.LIFETIME, isSubscription = false);

    companion object {
        fun fromProductId(productId: String): PremiumPlan? =
            entries.firstOrNull { it.productId == productId }
    }
}
