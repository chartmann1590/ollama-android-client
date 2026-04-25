package com.charles.ollama.client.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.util.PerformanceMonitor
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Loads and shows AdMob App Open ads when the app returns to the foreground.
 *
 * Lifecycle:
 *   * Cached ad expires after 4 hours per Google guidance — anything older is reloaded.
 *   * The first foreground transition (cold start) is skipped: showing an
 *     unrelated ad before the user sees the app harms the first impression.
 *   * If a full-screen ad is already showing (interstitial or app-open), we
 *     skip rather than stack overlays.
 */
class AppOpenAdManager : Application.ActivityLifecycleCallbacks, LifecycleEventObserver {

    private val adUnitId = BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var loadTimeMs: Long = 0L
    private var currentActivity: Activity? = null
    private var skippedFirstForeground = false

    fun register(application: Application) {
        if (!BuildConfig.ADS_ENABLED) return
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun isAdAvailable(): Boolean {
        if (appOpenAd == null) return false
        val ageMs = System.currentTimeMillis() - loadTimeMs
        return ageMs < FOUR_HOURS_MS
    }

    private fun loadAd(context: Context) {
        if (isLoading || isAdAvailable()) return
        val trace = PerformanceMonitor.startAdTrace("load_app_open")
        isLoading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    PerformanceMonitor.addAttribute(trace, "status", "loaded")
                    appOpenAd = ad
                    loadTimeMs = System.currentTimeMillis()
                    isLoading = false
                    PerformanceMonitor.stopTrace(trace)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "App open ad failed to load: ${error.message}")
                    PerformanceMonitor.addAttribute(trace, "status", "failed")
                    PerformanceMonitor.addAttribute(trace, "error_code", error.code.toString())
                    isLoading = false
                    PerformanceMonitor.stopTrace(trace)
                }
            }
        )
    }

    private fun showAdIfAvailable() {
        if (isShowing) return
        val activity = currentActivity ?: return
        if (!isAdAvailable()) {
            loadAd(activity)
            return
        }
        val ad = appOpenAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowing = false
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowing = false
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                isShowing = true
            }
        }
        ad.show(activity)
    }

    // --- ActivityLifecycleCallbacks: track the foreground activity ---
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (!isShowing) currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) { currentActivity = activity }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    // --- ProcessLifecycleOwner: fire on background -> foreground ---
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) return
        if (!skippedFirstForeground) {
            skippedFirstForeground = true
            currentActivity?.let { loadAd(it) }
            return
        }
        showAdIfAvailable()
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val FOUR_HOURS_MS = 4L * 60 * 60 * 1000
    }
}
