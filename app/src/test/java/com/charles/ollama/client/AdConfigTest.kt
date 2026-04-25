package com.charles.ollama.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the AdMob configuration plumbing in `app/build.gradle.kts`. These
 * tests fail fast if a future refactor accidentally drops one of the
 * `BuildConfig.ADMOB_*` fields, the `ADS_ENABLED` flag, or substitutes a
 * value that doesn't look like a real AdMob ID.
 *
 * The `ca-app-pub-` prefix and the `~` (app id) / `/` (ad unit id) separators
 * are part of AdMob's public format, so checking for them is a cheap sanity
 * check that catches things like leaving a placeholder string in.
 */
class AdConfigTest {

    @Test
    fun `ads enabled flag is exposed`() {
        // We don't assert true/false — both states are valid configurations —
        // but BuildConfig.ADS_ENABLED must exist and be a boolean.
        val value: Boolean = BuildConfig.ADS_ENABLED
        // touch the value so the compiler can't elide it
        assertTrue(value || !value)
    }

    @Test
    fun `admob app id is present and well-formed`() {
        val id = BuildConfig.ADMOB_APP_ID
        assertNotNull(id)
        assertTrue("App ID must use the AdMob format: $id", id.startsWith("ca-app-pub-"))
        assertTrue("App ID must contain '~' separator: $id", id.contains("~"))
    }

    @Test
    fun `every ad unit id is well-formed`() {
        val ids = listOf(
            BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            BuildConfig.ADMOB_NATIVE_AD_UNIT_ID,
            BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
        )
        ids.forEach { id ->
            assertTrue("Ad unit ID must start with ca-app-pub-: $id", id.startsWith("ca-app-pub-"))
            assertTrue("Ad unit ID must contain '/' separator: $id", id.contains("/"))
        }
    }

    @Test
    fun `ad unit ids are not blank`() {
        val ids = listOf(
            BuildConfig.ADMOB_APP_ID,
            BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            BuildConfig.ADMOB_NATIVE_AD_UNIT_ID,
            BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
        )
        ids.forEach { id -> assertFalse("Ad ID must not be blank", id.isBlank()) }
    }
}
