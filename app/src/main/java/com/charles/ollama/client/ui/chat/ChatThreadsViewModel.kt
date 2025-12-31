package com.charles.ollama.client.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatThread
import com.charles.ollama.client.domain.usecase.GetChatThreadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatThreadsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val getChatThreadsUseCase: GetChatThreadsUseCase,
    private val serverRepository: ServerRepository
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    val threads: StateFlow<List<ChatThread>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                getChatThreadsUseCase()
            } else {
                getChatThreadsUseCase.search(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    val hasServer: StateFlow<Boolean> = serverRepository.getDefaultServer()
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun createThread(title: String, model: String?): Long? {
        var threadId: Long? = null
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val defaultServer = serverRepository.getDefaultServerSync()
                threadId = chatRepository.createThread(
                    title = title,
                    model = model,
                    serverId = defaultServer?.id
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create thread"
            } finally {
                _isLoading.value = false
            }
        }
        return threadId
    }
    
    suspend fun createThreadAsync(title: String, model: String?): Long {
        val defaultServer = serverRepository.getDefaultServerSync()
        return chatRepository.createThread(
            title = title,
            model = model,
            serverId = defaultServer?.id
        )
    }
    
    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            try {
                chatRepository.deleteThread(threadId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete thread"
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}

