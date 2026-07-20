package com.charles.ollama.client.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks progress toward the in-app review prompt so it's shown at most once,
 * only after the user has had a few successful replies (a real satisfaction signal).
 */
@Singleton
class ReviewPromptPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun incrementSuccessfulReplies(): Int {
        val count = prefs.getInt(KEY_SUCCESS_COUNT, 0) + 1
        prefs.edit().putInt(KEY_SUCCESS_COUNT, count).apply()
        return count
    }

    fun hasBeenPrompted(): Boolean = prefs.getBoolean(KEY_PROMPTED, false)

    fun markPrompted() {
        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "review_prompt_prefs"
        private const val KEY_SUCCESS_COUNT = "successful_replies"
        private const val KEY_PROMPTED = "has_prompted"

        /** Number of successful replies before we consider asking for a review. */
        const val SUCCESS_THRESHOLD = 5
    }
}
