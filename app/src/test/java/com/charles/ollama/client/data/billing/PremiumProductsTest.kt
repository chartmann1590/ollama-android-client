package com.charles.ollama.client.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PremiumProductsTest {

    @Test
    fun `lifetime purchase is an in-app product that grants ad-free`() {
        assertEquals("premium_lifetime", PremiumProducts.LIFETIME)
        assertTrue(PremiumProducts.inAppIds.contains(PremiumProducts.LIFETIME))
        assertTrue(PremiumProducts.allIds.contains(PremiumProducts.LIFETIME))
        assertFalse(PremiumProducts.webSyncIds.contains(PremiumProducts.LIFETIME))
    }

    @Test
    fun `web sync products also grant ad-free`() {
        PremiumProducts.webSyncIds.forEach { productId ->
            assertTrue("Web Sync product must grant ad-free: $productId", PremiumProducts.allIds.contains(productId))
        }
    }

    @Test
    fun `GitHub Supabase entitlement functions know every Android premium product id`() {
        val checkoutSource = repoFile("supabase/functions/create-checkout-session/index.ts").readText()
        val statusSource = repoFile("supabase/functions/get-premium-status/index.ts").readText()
        val webhookSource = repoFile("supabase/functions/stripe-webhook/index.ts").readText()

        PremiumProducts.allIds.forEach { productId ->
            assertTrue("Checkout function is missing product id $productId", checkoutSource.contains(productId))
            assertTrue("Status function is missing product id $productId", statusSource.contains(productId))
        }

        assertTrue("Webhook must special-case lifetime purchases", webhookSource.contains(PremiumProducts.LIFETIME))
    }

    private fun repoFile(relativePath: String): File {
        var dir = File(checkNotNull(System.getProperty("user.dir"))).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = checkNotNull(dir.parentFile)
        }
        return File(dir, relativePath)
    }
}
