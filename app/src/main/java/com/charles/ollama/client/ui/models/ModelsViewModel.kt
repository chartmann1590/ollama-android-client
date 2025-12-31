package com.charles.ollama.client.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.repository.PullProgress
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.domain.usecase.DeleteModelUseCase
import com.charles.ollama.client.domain.usecase.GetModelsUseCase
import com.charles.ollama.client.domain.usecase.PullModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val getModelsUseCase: GetModelsUseCase,
    private val pullModelUseCase: PullModelUseCase,
    private val deleteModelUseCase: DeleteModelUseCase
) : ViewModel() {
    
    private val _models = MutableStateFlow<List<Model>>(emptyList())
    val models: StateFlow<List<Model>> = _models.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _pullProgress = MutableStateFlow<PullProgress?>(null)
    val pullProgress: StateFlow<PullProgress?> = _pullProgress.asStateFlow()
    
    private val _isPulling = MutableStateFlow(false)
    val isPulling: StateFlow<Boolean> = _isPulling.asStateFlow()
    
    fun loadModels() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = getModelsUseCase()
                result.onSuccess { modelList ->
                    _models.value = modelList
                }.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to load models"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load models"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun pullModel(modelName: String) {
        viewModelScope.launch {
            try {
                _isPulling.value = true
                _error.value = null
                _pullProgress.value = null
                
                pullModelUseCase.invoke(modelName)
                    .catch { exception ->
                        _error.value = exception.message ?: "Failed to pull model"
                        _isPulling.value = false
                    }
                    .collect { progress ->
                        _pullProgress.value = progress
                        if (progress.status.contains("success") || progress.status.contains("complete")) {
                            _isPulling.value = false
                            loadModels() // Refresh model list
                        }
                    }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to pull model"
                _isPulling.value = false
            }
        }
    }
    
    fun deleteModel(modelName: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = deleteModelUseCase(modelName)
                result.onSuccess {
                    loadModels() // Refresh model list
                }.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to delete model"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete model"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearPullProgress() {
        _pullProgress.value = null
    }
}

