package com.charles.ollama.client.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.domain.usecase.ManageServerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val manageServerUseCase: ManageServerUseCase,
    private val modelRepository: ModelRepository
) : ViewModel() {
    
    val servers: StateFlow<List<Server>> = manageServerUseCase.getAllServers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val defaultServer: StateFlow<Server?> = manageServerUseCase.getDefaultServer()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()
    
    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()
    
    fun addServer(name: String, baseUrl: String, isDefault: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = manageServerUseCase.addServer(name, baseUrl, isDefault)
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to add server"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add server"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addLitertLocalServer(isDefault: Boolean) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = manageServerUseCase.addLitertLocalServer(isDefault)
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to add on-device server"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add on-device server"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun updateServer(server: Server) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = manageServerUseCase.updateServer(server)
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to update server"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update server"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteServer(server: Server) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = manageServerUseCase.deleteServer(server)
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to delete server"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete server"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setDefaultServer(serverId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val result = manageServerUseCase.setDefaultServer(serverId)
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to set default server"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to set default server"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun testConnection(baseUrl: String) {
        viewModelScope.launch {
            try {
                _isTestingConnection.value = true
                _connectionTestResult.value = null
                _error.value = null
                if (com.charles.ollama.client.data.litert.LitertConstants.isLitertLocalBaseUrl(baseUrl)) {
                    _connectionTestResult.value =
                        "On-device LiteRT does not use a network URL. Download models from the Models screen."
                    return@launch
                }

                val result = modelRepository.getModels(baseUrl)
                result.onSuccess { models ->
                    _connectionTestResult.value = "Connection successful! Found ${models.size} model(s)."
                }.onFailure { exception ->
                    _connectionTestResult.value = "Connection failed: ${exception.message}"
                }
            } catch (e: Exception) {
                _connectionTestResult.value = "Connection failed: ${e.message}"
            } finally {
                _isTestingConnection.value = false
            }
        }
    }
    
    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }
    
    fun clearError() {
        _error.value = null
    }
}

