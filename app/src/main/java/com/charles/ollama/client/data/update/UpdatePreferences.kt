package com.charles.ollama.client.data.update

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks state for the in-app GitHub update prompt:
 * - `lastCheckedMillis` throttles API calls so we only hit GitHub every few
 *   hours.
 * - `dismissedTag` lets the user say "not interested in this release" so the
 *   dialog stays hidden until a newer tag appears.
 */
@Singleton
class UpdatePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastCheckedMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECKED, value).apply()

    var dismissedTag: String?
        get() = prefs.getString(KEY_DISMISSED_TAG, null)
        set(value) = prefs.edit().putString(KEY_DISMISSED_TAG, value).apply()

    companion object {
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECKED = "last_checked_millis"
        private const val KEY_DISMISSED_TAG = "dismissed_tag"

        /** Minimum gap between GitHub API calls. */
        const val CHECK_INTERVAL_MILLIS: Long = 6L * 60L * 60L * 1000L
    }
}
