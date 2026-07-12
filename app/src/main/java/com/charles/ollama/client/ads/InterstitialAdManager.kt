package com.charles.ollama.client.ads

import android.app.Activity
import android.util.Log
import com.charles.ollama.client.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.charles.ollama.client.util.PerformanceMonitor
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManager @Inject constructor(
    private val adGate: AdGate
) {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    // Probability of showing an ad (30% chance)
    private val showAdProbability = 0.3f

    // Hard frequency cap: never show two interstitials within this window, and
    // suppress them entirely for a short grace period after process start. This
    // keeps ads at natural transition points and avoids the "unexpected /
    // disruptive interstitial" category in Google Play's Ads policy. The 30%
    // probability above is an additional softener on top of this hard guard.
    private var lastShownAtMs = 0L
    private val processStartMs = System.currentTimeMillis()

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val MIN_INTERVAL_MS = 120_000L
        private const val STARTUP_GRACE_MS = 60_000L
    }
    
    /**
     * Load an interstitial ad
     */
    fun loadAd(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        if (adGate.adsCurrentlyDisabled()) return
        if (isLoading || interstitialAd != null) {
            return
        }
        
        val trace = PerformanceMonitor.startAdTrace("load_interstitial")
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            activity,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    PerformanceMonitor.addAttribute(trace, "status", "loaded")
                    interstitialAd = ad
                    isLoading = false
                    PerformanceMonitor.stopTrace(trace)
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad dismissed")
                            interstitialAd = null
                            // Load next ad
                            loadAd(activity)
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            Log.e(TAG, "Interstitial ad failed to show: ${error.message}")
                            interstitialAd = null
                            // Load next ad
                            loadAd(activity)
                        }
                        
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad showed")
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message}")
                    PerformanceMonitor.addAttribute(trace, "status", "failed")
                    PerformanceMonitor.addAttribute(trace, "error_code", error.code.toString())
                    isLoading = false
                    interstitialAd = null
                    PerformanceMonitor.stopTrace(trace)
                }
            }
        )
    }
    
    /**
     * Show interstitial ad if available and random chance allows
     * Returns true if ad was shown, false otherwise
     */
    fun showAdIfAvailable(activity: Activity): Boolean {
        if (!BuildConfig.ADS_ENABLED) return false
        if (adGate.adsCurrentlyDisabled()) return false
        val trace = PerformanceMonitor.startAdTrace("show_interstitial")
        val ad = interstitialAd

        val now = System.currentTimeMillis()
        val withinStartupGrace = now - processStartMs < STARTUP_GRACE_MS
        val withinInterval = now - lastShownAtMs < MIN_INTERVAL_MS
        if (withinStartupGrace || withinInterval) {
            PerformanceMonitor.addAttribute(trace, "shown", "false")
            PerformanceMonitor.addAttribute(
                trace, "reason", if (withinStartupGrace) "startup_grace" else "interval_filtered"
            )
            PerformanceMonitor.stopTrace(trace)
            return false
        }

        if (ad != null && Random.nextFloat() < showAdProbability) {
            lastShownAtMs = now
            val shown = safeShow(ad, activity)
            PerformanceMonitor.addAttribute(trace, "shown", shown.toString())
            PerformanceMonitor.stopTrace(trace)
            return shown
        }

        PerformanceMonitor.addAttribute(trace, "shown", "false")
        PerformanceMonitor.addAttribute(trace, "reason", if (ad == null) "no_ad_loaded" else "probability_filtered")

        // If ad wasn't shown but we don't have one loaded, try to load one
        if (ad == null && !isLoading) {
            loadAd(activity)
        }

        PerformanceMonitor.stopTrace(trace)
        return false
    }
    
    /**
     * Force show ad if available (ignores probability)
     */
    fun showAdIfAvailable(activity: Activity, force: Boolean): Boolean {
        if (adGate.adsCurrentlyDisabled()) return false
        if (force) {
            val ad = interstitialAd
            if (ad != null) {
                lastShownAtMs = System.currentTimeMillis()
                return safeShow(ad, activity)
            }
        } else {
            return showAdIfAvailable(activity)
        }
        return false
    }

    /**
     * Shows [ad], swallowing the ActivityNotFoundException AdMob can throw on
     * some devices when Play Services is stale or mid-update, even though
     * AdActivity is correctly declared in the merged manifest (see GitHub #30).
     * Returns whether the ad was actually shown.
     */
    private fun safeShow(ad: InterstitialAd, activity: Activity): Boolean {
        return try {
            ad.show(activity)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Interstitial ad failed to show: ${e.message}")
            runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
            interstitialAd = null
            false
        }
    }

    /**
     * Check if an ad is loaded
     */
    fun isAdLoaded(): Boolean {
        return interstitialAd != null
    }
}

