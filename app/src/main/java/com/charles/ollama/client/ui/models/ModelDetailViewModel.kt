package com.charles.ollama.client.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.api.dto.ShowModelResponse
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class ModelDetailViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Nav passes the (URL-encoded) model name; decode it for display and the API call.
    val modelName: String = URLDecoder.decode(
        savedStateHandle.get<String>("modelName").orEmpty(),
        "UTF-8"
    )

    private val _info = MutableStateFlow<ShowModelResponse?>(null)
    val info: StateFlow<ShowModelResponse?> = _info.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val server = serverRepository.getDefaultServerSync()
                if (server == null) {
                    _error.value = "No server configured"
                    return@launch
                }
                modelRepository.getModelInfo(server.baseUrl, modelName)
                    .onSuccess { _info.value = it }
                    .onFailure { _error.value = it.message ?: "Failed to load model info" }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load model info"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
