package com.charles.ollama.client.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** User-selectable app appearance. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Persistent UI preferences (theme mode, dynamic color, app language, request timeout).
 * Mirrors the lightweight SharedPreferences pattern used by LitertPreferences
 * and AdGate, and exposes StateFlows so Compose can react instantly.
 */
@Singleton
class UiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _requestTimeoutSeconds = MutableStateFlow(prefs.getInt(KEY_REQUEST_TIMEOUT, DEFAULT_TIMEOUT_SECONDS))
    val requestTimeoutSeconds: StateFlow<Int> = _requestTimeoutSeconds.asStateFlow()

    private val _languageTag = MutableStateFlow(prefs.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG)
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    private val _languageOnboardingComplete = MutableStateFlow(prefs.getBoolean(KEY_LANGUAGE_ONBOARDING_COMPLETE, false))
    val languageOnboardingComplete: StateFlow<Boolean> = _languageOnboardingComplete.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        if (_dynamicColor.value == enabled) return
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setRequestTimeoutSeconds(seconds: Int) {
        if (_requestTimeoutSeconds.value == seconds) return
        prefs.edit().putInt(KEY_REQUEST_TIMEOUT, seconds).apply()
        _requestTimeoutSeconds.value = seconds
    }

    fun setLanguageTag(languageTag: String) {
        if (_languageTag.value == languageTag) return
        prefs.edit().putString(KEY_LANGUAGE_TAG, languageTag).apply()
        _languageTag.value = languageTag
    }

    fun setLanguageOnboardingComplete(complete: Boolean) {
        if (_languageOnboardingComplete.value == complete) return
        prefs.edit().putBoolean(KEY_LANGUAGE_ONBOARDING_COMPLETE, complete).apply()
        _languageOnboardingComplete.value = complete
    }

    companion object {
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_REQUEST_TIMEOUT = "request_timeout_seconds"
        private const val KEY_LANGUAGE_TAG = "language_tag"
        private const val KEY_LANGUAGE_ONBOARDING_COMPLETE = "language_onboarding_complete"
        const val DEFAULT_TIMEOUT_SECONDS = 90
        const val DEFAULT_LANGUAGE_TAG = "en"
        val TIMEOUT_OPTIONS = listOf(60, 90, 120, 180, 300, 600)
    }
}
