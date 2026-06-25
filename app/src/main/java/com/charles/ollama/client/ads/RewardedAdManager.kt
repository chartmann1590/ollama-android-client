package com.charles.ollama.client.ads

import android.app.Activity
import android.util.Log
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.util.PerformanceMonitor
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and shows a rewarded ad. When the user earns the reward, [adGate]
 * receives [AdGate.CREDITS_PER_AD] credit. Mirrors the structure of
 * [InterstitialAdManager] but never gates on the ad-free expiry — the whole
 * point of rewarded ads is that they remain available so users can extend
 * their ad-free window.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    private val adGate: AdGate
) {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID

    private val _state = MutableStateFlow(RewardedAdState.IDLE)
    val state: StateFlow<RewardedAdState> = _state.asStateFlow()

    fun isAdLoaded(): Boolean = rewardedAd != null

    fun loadAd(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        if (adGate.isPremium.value) {
            rewardedAd = null
            _state.value = RewardedAdState.IDLE
            return
        }
        if (isLoading || rewardedAd != null) return

        val trace = PerformanceMonitor.startAdTrace("load_rewarded")
        isLoading = true
        _state.value = RewardedAdState.LOADING

        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    PerformanceMonitor.addAttribute(trace, "status", "loaded")
                    PerformanceMonitor.stopTrace(trace)
                    rewardedAd = ad
                    isLoading = false
                    _state.value = RewardedAdState.READY
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    PerformanceMonitor.addAttribute(trace, "status", "failed")
                    PerformanceMonitor.addAttribute(trace, "error_code", error.code.toString())
                    PerformanceMonitor.stopTrace(trace)
                    rewardedAd = null
                    isLoading = false
                    _state.value = RewardedAdState.FAILED
                }
            }
        )
    }

    /**
     * Show the loaded ad. [onReward] runs on the main thread when the user
     * earns the reward. [onClosed] runs whether or not the user earned it,
     * so callers can refresh their UI / reload the next ad.
     */
    fun showAd(
        activity: Activity,
        onReward: () -> Unit,
        onClosed: () -> Unit
    ): Boolean {
        if (adGate.isPremium.value) {
            rewardedAd = null
            _state.value = RewardedAdState.IDLE
            onClosed()
            return false
        }
        if (!BuildConfig.ADS_ENABLED) {
            // For dev / test builds with ADS_ENABLED=false, still grant the
            // reward so the redemption flow can be exercised end-to-end.
            adGate.addCredits(AdGate.CREDITS_PER_AD)
            onReward()
            onClosed()
            return true
        }

        val ad = rewardedAd
        if (ad == null) {
            // Nothing to show; ensure a load is in flight for next time.
            loadAd(activity)
            return false
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                _state.value = RewardedAdState.IDLE
                loadAd(activity)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                _state.value = RewardedAdState.FAILED
                loadAd(activity)
                onClosed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed")
                _state.value = RewardedAdState.SHOWING
            }
        }

        ad.show(activity, OnUserEarnedRewardListener {
            adGate.addCredits(AdGate.CREDITS_PER_AD)
            onReward()
        })
        return true
    }

    companion object {
        private const val TAG = "RewardedAdManager"
    }
}

enum class RewardedAdState { IDLE, LOADING, READY, SHOWING, FAILED }
