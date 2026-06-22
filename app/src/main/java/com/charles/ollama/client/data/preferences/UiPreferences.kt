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
 * Persistent UI preferences (theme mode, dynamic color, voice auto-read).
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

    companion object {
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_REQUEST_TIMEOUT = "request_timeout_seconds"
        const val DEFAULT_TIMEOUT_SECONDS = 90
        val TIMEOUT_OPTIONS = listOf(60, 90, 120, 180, 300, 600)
    }
}
