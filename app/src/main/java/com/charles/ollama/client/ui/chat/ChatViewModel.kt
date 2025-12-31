package com.charles.ollama.client.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatMessage
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.domain.usecase.SendChatMessageUseCase
import com.charles.ollama.client.util.VibrationHelper
import com.charles.ollama.client.util.ThinkingParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val serverRepository: ServerRepository,
    private val modelRepository: ModelRepository,
    private val vibrationHelper: VibrationHelper
) : ViewModel() {
    
    private val _threadId = MutableStateFlow<Long?>(null)
    val threadId: StateFlow<Long?> = _threadId.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()
    
    private val _isVisionModel = MutableStateFlow(false)
    val isVisionModel: StateFlow<Boolean> = _isVisionModel.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _availableModels = MutableStateFlow<List<Model>>(emptyList())
    val availableModels: StateFlow<List<Model>> = _availableModels.asStateFlow()
    
    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()
    
    private val _streamingContent = MutableStateFlow<String?>(null)
    val streamingContent: StateFlow<String?> = _streamingContent.asStateFlow()
    
    private val _streamingThinking = MutableStateFlow<String?>(null)
    val streamingThinking: StateFlow<String?> = _streamingThinking.asStateFlow()
    
    private val _showThinking = MutableStateFlow<Boolean>(false)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()
    
    val thread: StateFlow<ChatThreadEntity?> = _threadId
        .flatMapLatest { id ->
            if (id != null) {
                chatRepository.getThreadById(id)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    init {
        observeMessages()
    }
    
    fun setThreadId(id: Long) {
        _threadId.value = id
        viewModelScope.launch {
            val thread = chatRepository.getThreadById(id).first()
            thread?.let {
                _selectedModel.value = it.model
                updateVisionModelStatus(it.model)
            }
            // Load available models from server
            loadAvailableModels()
        }
    }
    
    private fun updateVisionModelStatus(modelName: String?) {
        if (modelName == null) {
            _isVisionModel.value = false
            return
        }
        val model = _availableModels.value.find { it.name == modelName }
        _isVisionModel.value = model?.isVisionModel() ?: false
    }
    
    fun loadAvailableModels() {
        viewModelScope.launch {
            try {
                _isLoadingModels.value = true
                val defaultServer = serverRepository.getDefaultServerSync()
                if (defaultServer != null) {
                    val result = modelRepository.getModels(defaultServer.baseUrl)
                    result.onSuccess { models ->
                        _availableModels.value = models.map { modelInfo ->
                            Model(
                                name = modelInfo.name,
                                modifiedAt = modelInfo.modifiedAt,
                                size = modelInfo.size,
                                digest = modelInfo.digest,
                                parameterSize = modelInfo.details?.parameterSize,
                                quantizationLevel = modelInfo.details?.quantizationLevel
                            )
                        }
                        // Update vision model status if a model is selected
                        _selectedModel.value?.let { updateVisionModelStatus(it) }
                    }.onFailure { exception ->
                        _error.value = "Failed to load models: ${exception.message}"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load models: ${e.message}"
            } finally {
                _isLoadingModels.value = false
            }
        }
    }
    
    private fun observeMessages() {
        viewModelScope.launch {
            combine(
                _threadId
                    .filterNotNull()
                    .flatMapLatest { threadId ->
                        chatRepository.getMessagesByThreadId(threadId)
                    },
                _streamingContent,
                _streamingThinking
            ) { entities, streamingContent, streamingThinking ->
                entities.map { entity ->
                    // If this is the last assistant message, prefer streaming content if available
                    val assistantMessages = entities.filter { it.role == "assistant" }
                    val isLastAssistant = entity.role == "assistant" && 
                        assistantMessages.isNotEmpty() &&
                        assistantMessages.last().id == entity.id
                    
                    val (content, thinking) = if (isLastAssistant && streamingContent != null && streamingContent.isNotEmpty()) {
                        // Use streaming content if available (it's the most up-to-date)
                        // streamingContent is already the parsed response, streamingThinking is already the parsed thinking
                        android.util.Log.d("ChatViewModel", "Using streaming content for message ${entity.id}: thinking=${streamingThinking != null} (${streamingThinking?.length ?: 0} chars)")
                        Pair(streamingContent, streamingThinking)
                    } else {
                        // Use database content
                        android.util.Log.d("ChatViewModel", "Using database content for message ${entity.id}: thinking=${entity.thinking != null} (${entity.thinking?.length ?: 0} chars)")
                        Pair(entity.content, entity.thinking)
                    }
                    
                    // Debug logging
                    if (thinking != null) {
                        android.util.Log.d("ChatViewModel", "Message ${entity.id} has thinking: ${thinking.length} chars, content: ${content.length} chars")
                    } else if (isLastAssistant) {
                        android.util.Log.d("ChatViewModel", "Message ${entity.id} has NO thinking (isLastAssistant=$isLastAssistant)")
                    }
                    
                    ChatMessage(
                        id = entity.id,
                        threadId = entity.threadId,
                        role = entity.role,
                        content = content,
                        thinking = thinking,
                        images = entity.images,
                        timestamp = entity.timestamp
                    )
                }
            }
                .collect { messageList ->
                    _messages.value = messageList
                }
        }
    }
    
    fun sendMessage(content: String, images: List<String>? = null) {
        val currentThreadId = _threadId.value
        val model = _selectedModel.value
        
        if (currentThreadId == null) {
            _error.value = "No thread selected"
            return
        }
        
        if (model == null) {
            _error.value = "No model selected"
            return
        }
        
        if (content.isBlank() && (images == null || images.isEmpty())) {
            return
        }
        
        viewModelScope.launch {
            val thread = chatRepository.getThreadById(currentThreadId).first()
            val streamEnabled = thread?.streamEnabled == true
            val systemPrompt = thread?.systemPrompt
            
            if (streamEnabled) {
                // Handle streaming
                _isLoading.value = true
                _error.value = null
                _streamingContent.value = ""
                _streamingThinking.value = null
                val vibrationEnabled = thread?.vibrationEnabled != false // Default to true
                
                try {
                    var fullContent = ""
                    var fullThinking = ""
                    sendChatMessageUseCase.streamMessage(currentThreadId, content, model, systemPrompt, images)
                        .collect { streamDelta ->
                            fullContent += streamDelta.content
                            streamDelta.thinking?.let { thinkingDelta ->
                                fullThinking += thinkingDelta
                                android.util.Log.d("ChatViewModel", "Received thinking delta: ${thinkingDelta.length} chars, total: ${fullThinking.length}")
                            }
                            
                            _streamingContent.value = fullContent
                            _streamingThinking.value = fullThinking.takeIf { it.isNotEmpty() }
                            
                            // Debug logging
                            if (fullThinking.isNotEmpty()) {
                                android.util.Log.d("ChatViewModel", "Streaming thinking: ${fullThinking.length} chars, content: ${fullContent.length} chars")
                            }
                            
                            // Vibrate on each delta if enabled
                            if (vibrationEnabled && streamDelta.content.isNotEmpty()) {
                                viewModelScope.launch {
                                    vibrationHelper.vibrate(10)
                                }
                            }
                        }
                    
                    // Log final content length for debugging
                    android.util.Log.d("ChatViewModel", "Streaming completed. Final content length: ${fullContent.length}, Final thinking length: ${fullThinking.length}")
                    
                    // Keep streaming content visible briefly to ensure UI updates
                    // The database should have the complete message by now
                    kotlinx.coroutines.delay(100)
                    
                    _streamingContent.value = null
                    _streamingThinking.value = null
                    
                    // Update thread timestamp
                    thread?.let {
                        chatRepository.updateThread(it)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error streaming message", e)
                    _error.value = e.message ?: "Failed to stream message"
                } finally {
                    _isLoading.value = false
                    // Don't clear streaming content immediately - let database update first
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(200)
                        _streamingContent.value = null
                        _streamingThinking.value = null
                    }
                }
            } else {
                // Non-streaming
                _isLoading.value = true
                _error.value = null
                
                val result = sendChatMessageUseCase(currentThreadId, content, model, false, systemPrompt, images)
                
                result.onFailure { exception ->
                    _error.value = exception.message ?: "Failed to send message"
                }
                
                _isLoading.value = false
            }
        }
    }
    
    fun setModel(model: String) {
        _selectedModel.value = model
        updateVisionModelStatus(model)
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(model = model)
                    chatRepository.updateThread(updated)
                }
            }
        }
    }
    
    fun updateThreadTitle(title: String) {
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(title = title)
                    chatRepository.updateThread(updated)
                }
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun updateStreamEnabled(enabled: Boolean) {
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(streamEnabled = enabled)
                    chatRepository.updateThread(updated)
                }
            }
        }
    }
    
    fun updateSystemPrompt(prompt: String?) {
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(systemPrompt = prompt?.takeIf { it.isNotBlank() })
                    chatRepository.updateThread(updated)
                }
            }
        }
    }
    
    fun updateVibrationEnabled(enabled: Boolean) {
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(vibrationEnabled = enabled)
                    chatRepository.updateThread(updated)
                }
            }
        }
    }
    
    fun setShowThinking(show: Boolean) {
        _showThinking.value = show
    }
}

