package com.charles.ollama.client

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E smoke test guarding both Crashlytics regressions:
 *
 *  1. `UnsatisfiedLinkError: litertlm_jni` thrown from `LiteRtChatService.<init>`
 *     when Hilt instantiates the singleton at app open.
 *  2. `IllegalArgumentException("parameter must be a descendant of this view")`
 *     from `ViewGroup.offsetRectBetweenParentAndChild` during the first frame's
 *     scroll-to-rect pass.
 *
 * Either crash would surface here as a failure to launch [MainActivity].
 *
 * Also asserts that the `${admobAppId}` manifest placeholder was substituted
 * by the build — if the substitution is missing or empty, the AdMob SDK
 * crashes the process at first ad load, which would not necessarily reproduce
 * during a quick smoke launch.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun mainActivity_launchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing) {
                    "MainActivity finished immediately after launch"
                }
            }
        }
    }

    @Test
    fun admobAppId_manifestPlaceholderIsSubstituted() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = ctx.packageManager.getApplicationInfo(
            ctx.packageName,
            PackageManager.GET_META_DATA
        )
        val manifestId = info.metaData?.get("com.google.android.gms.ads.APPLICATION_ID")?.toString()
        assertNotNull("AdMob APPLICATION_ID meta-data is missing", manifestId)
        assertTrue(
            "AdMob APPLICATION_ID was not substituted, got: $manifestId",
            manifestId!!.startsWith("ca-app-pub-") && manifestId.contains("~")
        )
        assertEquals(
            "Manifest AdMob ID must match BuildConfig.ADMOB_APP_ID",
            BuildConfig.ADMOB_APP_ID,
            manifestId
        )
    }
}
