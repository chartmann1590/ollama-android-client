package com.charles.ollama.client.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.charles.ollama.client.util.PerformanceMonitor
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManager @Inject constructor() {
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private val adUnitId = "ca-app-pub-8382831211800454/6664403620"
    
    // Probability of showing an ad (30% chance)
    private val showAdProbability = 0.3f
    
    companion object {
        private const val TAG = "InterstitialAdManager"
    }
    
    /**
     * Load an interstitial ad
     */
    fun loadAd(activity: Activity) {
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
        val trace = PerformanceMonitor.startAdTrace("show_interstitial")
        val ad = interstitialAd
        
        if (ad != null && Random.nextFloat() < showAdProbability) {
            PerformanceMonitor.addAttribute(trace, "shown", "true")
            ad.show(activity)
            PerformanceMonitor.stopTrace(trace)
            return true
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
        if (force) {
            val ad = interstitialAd
            if (ad != null) {
                ad.show(activity)
                return true
            }
        } else {
            return showAdIfAvailable(activity)
        }
        return false
    }
    
    /**
     * Check if an ad is loaded
     */
    fun isAdLoaded(): Boolean {
        return interstitialAd != null
    }
}

