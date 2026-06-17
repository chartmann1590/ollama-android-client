package com.charles.ollama.client.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatThread
import com.charles.ollama.client.domain.model.ModelWithServer
import com.charles.ollama.client.domain.usecase.GetAllAvailableModelsUseCase
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
    private val getAllAvailableModelsUseCase: GetAllAvailableModelsUseCase,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _selectedLabel = MutableStateFlow<String?>(null)
    val selectedLabel: StateFlow<String?> = _selectedLabel.asStateFlow()

    /** Distinct, non-empty labels currently in use, for the filter chip row. */
    val labels: StateFlow<List<String>> = chatRepository.getLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val threads: StateFlow<List<ChatThread>> =
        combine(_searchQuery, _showArchived, _selectedLabel) { q, archived, label ->
            Triple(q, archived, label)
        }
            .flatMapLatest { (query, archived, label) ->
                val base = if (query.isBlank()) getChatThreadsUseCase(archived = archived)
                else getChatThreadsUseCase.search(query, archived = archived)
                base.map { list ->
                    if (label == null) list else list.filter { it.label == label }
                }
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

    private val _modelOptions = MutableStateFlow<List<ModelWithServer>>(emptyList())
    val modelOptions: StateFlow<List<ModelWithServer>> = _modelOptions.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

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

    fun setSelectedLabel(label: String?) {
        _selectedLabel.value = label
    }

    fun setLabel(threadId: Long, label: String?) {
        viewModelScope.launch {
            try {
                chatRepository.setThreadLabel(threadId, label)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update label"
            }
        }
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

    fun loadModelOptions() {
        viewModelScope.launch {
            _isLoadingModels.value = true
            _modelOptions.value = getAllAvailableModelsUseCase()
            _isLoadingModels.value = false
        }
    }

    suspend fun createThreadAsync(
        title: String,
        model: String?,
        serverId: Long? = null,
        systemPrompt: String? = null
    ): Long = chatRepository.createThread(title, model, serverId, systemPrompt)

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
