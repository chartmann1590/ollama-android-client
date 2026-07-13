package com.charles.ollama.client.ui.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.ads.RedemptionResult
import com.charles.ollama.client.ads.RewardTier
import com.charles.ollama.client.ads.RewardedAdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val adGate: AdGate,
    private val rewardedAdManager: RewardedAdManager
) : ViewModel() {

    val credits: StateFlow<Int> = adGate.credits
    val adFreeUntilMs: StateFlow<Long> = adGate.adFreeUntilMs
    val creditsSpentToday: StateFlow<Int> = adGate.creditsSpentToday
    val creditsEarnedToday: StateFlow<Int> = adGate.creditsEarnedToday
    val rewardedAdState: StateFlow<com.charles.ollama.client.ads.RewardedAdState> =
        rewardedAdManager.state

    val isPremium: StateFlow<Boolean> = adGate.isPremium

    val tiers: List<RewardTier> = AdGate.TIERS
    val creditsPerAd: Int = AdGate.CREDITS_PER_AD
    val maxCreditsPerDay: Int = AdGate.MAX_CREDITS_PER_DAY

    private val _nowMs = MutableStateFlow(System.currentTimeMillis())
    /** Ticks every second so the countdown re-renders smoothly. */
    val nowMs: StateFlow<Long> = _nowMs.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _nowMs.value = System.currentTimeMillis()
                adGate.refreshDailyCounters()
                delay(1000)
            }
        }
    }

    fun redeem(tier: RewardTier) {
        when (val r = adGate.redeem(tier)) {
            is RedemptionResult.Success ->
                _toast.value = "Ad-free time extended by ${tier.label}"
            is RedemptionResult.InsufficientCredits ->
                _toast.value = "Need ${r.missing} more credit${if (r.missing == 1) "" else "s"}"
            is RedemptionResult.DailyLimit ->
                _toast.value = if (r.remainingToday == 0)
                    "Daily redeem limit reached - try again tomorrow"
                else
                    "Only ${r.remainingToday} more credit${if (r.remainingToday == 1) "" else "s"} can be redeemed today"
        }
    }

    fun canEarnMoreToday(): Boolean = adGate.earnableToday() > 0

    fun clearToast() { _toast.value = null }

    fun isAdLoaded(): Boolean = rewardedAdManager.isAdLoaded()

    fun rewardedManager(): RewardedAdManager = rewardedAdManager

    fun showEarnLimitToast() {
        _toast.value = "Daily earn limit reached - come back tomorrow for more"
    }
}
