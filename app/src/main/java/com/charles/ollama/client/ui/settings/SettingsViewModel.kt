package com.charles.ollama.client.ui.settings

import androidx.lifecycle.ViewModel
import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.data.litert.LitertPreferences
import com.charles.ollama.client.data.preferences.ThemeMode
import com.charles.ollama.client.data.preferences.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val litertPreferences: LitertPreferences,
    private val adGate: AdGate,
    private val uiPreferences: UiPreferences,
) : ViewModel() {

    private val _huggingFaceToken = MutableStateFlow(litertPreferences.getHuggingFaceToken().orEmpty())
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = uiPreferences.themeMode
    val dynamicColor: StateFlow<Boolean> = uiPreferences.dynamicColor
    val isPremium: StateFlow<Boolean> = adGate.isPremium

    fun setThemeMode(mode: ThemeMode) = uiPreferences.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = uiPreferences.setDynamicColor(enabled)

    fun updateHuggingFaceToken(token: String) {
        _huggingFaceToken.value = token
        litertPreferences.setHuggingFaceToken(token)
    }

    fun clearHuggingFaceToken() {
        _huggingFaceToken.value = ""
        litertPreferences.setHuggingFaceToken(null)
    }

    fun requestSetupTutorialAgain() {
        adGate.requestSetupTutorialReplay()
    }
}
