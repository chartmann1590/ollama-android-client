package com.charles.ollama.client.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatThread
import com.charles.ollama.client.domain.usecase.GetChatThreadsUseCase
import com.charles.ollama.client.util.ThreadExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatThreadsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val getChatThreadsUseCase: GetChatThreadsUseCase,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    val threads: StateFlow<List<ChatThread>> =
        combine(_searchQuery, _showArchived) { q, archived -> q to archived }
            .flatMapLatest { (query, archived) ->
                if (query.isBlank()) getChatThreadsUseCase(archived = archived)
                else getChatThreadsUseCase.search(query, archived = archived)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /** Convenience: count of archived items, used to show or hide the toggle row. */
    val archivedCount: StateFlow<Int> = getChatThreadsUseCase(archived = true)
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    fun toggleShowArchived() {
        _showArchived.value = !_showArchived.value
    }

    fun setPinned(threadId: Long, pinned: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.setThreadPinned(threadId, pinned)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update thread"
            }
        }
    }

    fun setArchived(threadId: Long, archived: Boolean) {
        viewModelScope.launch {
            try {
                chatRepository.setThreadArchived(threadId, archived)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update thread"
            }
        }
    }

    fun shareThread(threadId: Long) {
        viewModelScope.launch {
            try {
                val threadEntity = chatRepository.getThreadById(threadId).first() ?: return@launch
                val messages = chatRepository.getMessagesForExport(threadId)
                ThreadExporter.shareThread(appContext, threadEntity, messages)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to share chat"
            }
        }
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
