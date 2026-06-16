package com.charles.ollama.client.data.billing

/**
 * Product catalog for Premium upgrades.
 *
 * These IDs must match the products configured in the Google Play Console:
 *  - [MONTHLY], [YEARLY], [WEBSYNC_MONTHLY], [WEBSYNC_YEARLY] are subscriptions (each needs a base plan).
 *  - [LIFETIME] is a one-time (managed) in-app product.
 *
 * Web Sync plans bundle unlimited web messages + ad removal.
 * Ad-Free-Only plans ([MONTHLY], [YEARLY], [LIFETIME]) only remove ads.
 */
object PremiumProducts {
    const val MONTHLY = "premium_monthly"
    const val YEARLY  = "premium_yearly"
    const val LIFETIME = "premium_lifetime"

    const val WEBSYNC_MONTHLY = "websync_monthly"
    const val WEBSYNC_YEARLY  = "websync_yearly"

    /** Plans that grant unlimited web sync (also grant ad-free). */
    val webSyncIds = listOf(WEBSYNC_MONTHLY, WEBSYNC_YEARLY)

    val subscriptionIds = listOf(MONTHLY, YEARLY, WEBSYNC_MONTHLY, WEBSYNC_YEARLY)
    val inAppIds = listOf(LIFETIME)

    /** All IDs that, if owned/active, grant ad-free premium. */
    val allIds = subscriptionIds + inAppIds
}

/** A purchasable plan, used by the paywall UI and the billing flow. */
enum class PremiumPlan(val productId: String, val isSubscription: Boolean) {
    MONTHLY(PremiumProducts.MONTHLY, isSubscription = true),
    YEARLY(PremiumProducts.YEARLY, isSubscription = true),
    LIFETIME(PremiumProducts.LIFETIME, isSubscription = false),
    WEBSYNC_MONTHLY(PremiumProducts.WEBSYNC_MONTHLY, isSubscription = true),
    WEBSYNC_YEARLY(PremiumProducts.WEBSYNC_YEARLY, isSubscription = true);

    companion object {
        fun fromProductId(productId: String): PremiumPlan? =
            entries.firstOrNull { it.productId == productId }
    }
}
