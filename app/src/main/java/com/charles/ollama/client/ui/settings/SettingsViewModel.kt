package com.charles.ollama.client.ui.settings

import androidx.lifecycle.ViewModel
import com.charles.ollama.client.data.litert.LitertPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val litertPreferences: LitertPreferences
) : ViewModel() {

    private val _huggingFaceToken = MutableStateFlow(litertPreferences.getHuggingFaceToken().orEmpty())
    val huggingFaceToken: StateFlow<String> = _huggingFaceToken.asStateFlow()

    fun updateHuggingFaceToken(token: String) {
        _huggingFaceToken.value = token
        litertPreferences.setHuggingFaceToken(token)
    }

    fun clearHuggingFaceToken() {
        _huggingFaceToken.value = ""
        litertPreferences.setHuggingFaceToken(null)
    }
}
