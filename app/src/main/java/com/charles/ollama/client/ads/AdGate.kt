package com.charles.ollama.client.ads

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the user's reward credits and the active ad-free expiry timestamp.
 * Backed by SharedPreferences so state survives process death; exposes
 * StateFlows so the rewards screen can react in real time.
 *
 * The redemption tiers are defined here so the UI and the gate stay in sync.
 */
@Singleton
class AdGate @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _setupTutorialReplayRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val setupTutorialReplayRequests: SharedFlow<Unit> = _setupTutorialReplayRequests.asSharedFlow()

    private val _credits = MutableStateFlow(prefs.getInt(KEY_CREDITS, 0))
    val credits: StateFlow<Int> = _credits.asStateFlow()

    private val _adFreeUntilMs = MutableStateFlow(prefs.getLong(KEY_AD_FREE_UNTIL, 0L))
    val adFreeUntilMs: StateFlow<Long> = _adFreeUntilMs.asStateFlow()

    private val _creditsSpentToday = MutableStateFlow(loadCreditsSpentToday())
    val creditsSpentToday: StateFlow<Int> = _creditsSpentToday.asStateFlow()

    private val _creditsEarnedToday = MutableStateFlow(loadCreditsEarnedToday())
    val creditsEarnedToday: StateFlow<Int> = _creditsEarnedToday.asStateFlow()

    /** Refresh from prefs — handles the case where the local day rolled over while the app was running. */
    fun refreshDailyCounters() {
        val spent = loadCreditsSpentToday()
        val earned = loadCreditsEarnedToday()
        if (_creditsSpentToday.value != spent) _creditsSpentToday.value = spent
        if (_creditsEarnedToday.value != earned) _creditsEarnedToday.value = earned
    }

    fun redeemableToday(): Int {
        refreshDailyCounters()
        return (MAX_CREDITS_PER_DAY - _creditsSpentToday.value).coerceAtLeast(0)
    }

    fun earnableToday(): Int {
        refreshDailyCounters()
        return (MAX_CREDITS_PER_DAY - _creditsEarnedToday.value).coerceAtLeast(0)
    }

    private fun loadCreditsSpentToday(): Int {
        val storedDay = prefs.getLong(KEY_CREDIT_DAY, -1L)
        return if (storedDay == currentLocalDayKey()) prefs.getInt(KEY_SPENT_TODAY, 0) else 0
    }

    private fun loadCreditsEarnedToday(): Int {
        val storedDay = prefs.getLong(KEY_CREDIT_DAY, -1L)
        return if (storedDay == currentLocalDayKey()) prefs.getInt(KEY_EARNED_TODAY, 0) else 0
    }

    private fun currentLocalDayKey(): Long {
        val now = System.currentTimeMillis()
        val offset = java.util.TimeZone.getDefault().getOffset(now).toLong()
        return (now + offset) / 86_400_000L
    }

    fun adsCurrentlyDisabled(): Boolean =
        _adFreeUntilMs.value > System.currentTimeMillis()

    fun adFreeRemainingMs(): Long {
        val left = _adFreeUntilMs.value - System.currentTimeMillis()
        return if (left > 0) left else 0L
    }

    /**
     * Grant up to [amount] credits, but never exceed [MAX_CREDITS_PER_DAY] total
     * earned in the current local day. Returns the number actually granted so
     * callers can surface "you hit the daily cap" feedback if they want.
     */
    fun addCredits(amount: Int): Int {
        if (amount <= 0) return 0
        val today = currentLocalDayKey()
        val storedDay = prefs.getLong(KEY_CREDIT_DAY, -1L)
        val isNewDay = storedDay != today
        val currentEarned = if (isNewDay) 0 else prefs.getInt(KEY_EARNED_TODAY, 0)
        val capacity = (MAX_CREDITS_PER_DAY - currentEarned).coerceAtLeast(0)
        val granted = amount.coerceAtMost(capacity)
        if (granted == 0) {
            _creditsEarnedToday.value = currentEarned
            if (isNewDay) _creditsSpentToday.value = 0
            return 0
        }

        val updated = _credits.value + granted
        val newEarned = currentEarned + granted

        prefs.edit().apply {
            putInt(KEY_CREDITS, updated)
            putLong(KEY_CREDIT_DAY, today)
            putInt(KEY_EARNED_TODAY, newEarned)
            if (isNewDay) putInt(KEY_SPENT_TODAY, 0)
        }.apply()

        _credits.value = updated
        _creditsEarnedToday.value = newEarned
        if (isNewDay) _creditsSpentToday.value = 0
        return granted
    }

    /**
     * Spend [tier].cost credits and extend the ad-free expiry by tier.durationMs.
     * Subject to a hard cap of [MAX_CREDITS_PER_DAY] credits redeemed per local
     * calendar day so the rewards system can't be drained in one sitting.
     */
    fun redeem(tier: RewardTier): RedemptionResult {
        val today = currentLocalDayKey()
        val storedDay = prefs.getLong(KEY_CREDIT_DAY, -1L)
        val isNewDay = storedDay != today
        val currentSpent = if (isNewDay) 0 else prefs.getInt(KEY_SPENT_TODAY, 0)

        if (currentSpent + tier.cost > MAX_CREDITS_PER_DAY) {
            _creditsSpentToday.value = currentSpent
            if (isNewDay) _creditsEarnedToday.value = 0
            return RedemptionResult.DailyLimit(remainingToday = (MAX_CREDITS_PER_DAY - currentSpent).coerceAtLeast(0))
        }
        if (_credits.value < tier.cost) {
            return RedemptionResult.InsufficientCredits(missing = tier.cost - _credits.value)
        }

        val now = System.currentTimeMillis()
        val baseline = if (_adFreeUntilMs.value > now) _adFreeUntilMs.value else now
        val newExpiry = baseline + tier.durationMs
        val newCredits = _credits.value - tier.cost
        val newSpent = currentSpent + tier.cost

        prefs.edit().apply {
            putInt(KEY_CREDITS, newCredits)
            putLong(KEY_AD_FREE_UNTIL, newExpiry)
            putLong(KEY_CREDIT_DAY, today)
            putInt(KEY_SPENT_TODAY, newSpent)
            if (isNewDay) putInt(KEY_EARNED_TODAY, 0)
        }.apply()

        _credits.value = newCredits
        _adFreeUntilMs.value = newExpiry
        _creditsSpentToday.value = newSpent
        if (isNewDay) _creditsEarnedToday.value = 0
        return RedemptionResult.Success
    }

    fun hasSeenWhatsNew(): Boolean = prefs.getBoolean(KEY_WHATS_NEW_SEEN, false)

    fun markWhatsNewSeen() {
        prefs.edit().putBoolean(KEY_WHATS_NEW_SEEN, true).apply()
    }

    fun hasSeenSetupTutorial(): Boolean =
        prefs.getBoolean(KEY_SETUP_TUTORIAL_SEEN, false)

    fun markSetupTutorialSeen() {
        prefs.edit().putBoolean(KEY_SETUP_TUTORIAL_SEEN, true).apply()
    }

    /** Show the getting-started sheet again (e.g. from Settings). */
    fun requestSetupTutorialReplay() {
        _setupTutorialReplayRequests.tryEmit(Unit)
    }

    companion object {
        private const val PREFS_NAME = "ad_gate_prefs"
        private const val KEY_CREDITS = "credits"
        private const val KEY_AD_FREE_UNTIL = "ad_free_until_ms"
        private const val KEY_WHATS_NEW_SEEN = "whats_new_rewards_v1_seen"
        private const val KEY_SETUP_TUTORIAL_SEEN = "setup_tutorial_v1_seen"
        private const val KEY_CREDIT_DAY = "credit_day_local"
        private const val KEY_SPENT_TODAY = "credits_spent_today"
        private const val KEY_EARNED_TODAY = "credits_earned_today"

        const val CREDITS_PER_AD = 1
        const val MAX_CREDITS_PER_DAY = 3

        val TIERS: List<RewardTier> = listOf(
            RewardTier(id = "30m", cost = 1, durationMs = 30L * 60 * 1000, label = "30 minutes"),
            RewardTier(id = "2h", cost = 3, durationMs = 2L * 60 * 60 * 1000, label = "2 hours")
        )
    }
}

data class RewardTier(
    val id: String,
    val cost: Int,
    val durationMs: Long,
    val label: String
)

sealed class RedemptionResult {
    object Success : RedemptionResult()
    data class InsufficientCredits(val missing: Int) : RedemptionResult()
    data class DailyLimit(val remainingToday: Int) : RedemptionResult()
}
