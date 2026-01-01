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
            try {
                combine(
                    _threadId
                        .filterNotNull()
                        .flatMapLatest { threadId ->
                            chatRepository.getMessagesByThreadId(threadId)
                                .catch { e ->
                                    // Handle errors in the Flow gracefully
                                    android.util.Log.e("ChatViewModel", "Error in message Flow", e)
                                    // Don't set error state for database issues - just log and emit empty list
                                    if (e !is android.database.sqlite.SQLiteBlobTooBigException) {
                                        _error.value = "Failed to load messages: ${e.message}"
                                    }
                                    emit(emptyList())
                                }
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
                        
                        val chatMessage = ChatMessage(
                            id = entity.id,
                            threadId = entity.threadId,
                            role = entity.role,
                            content = content,
                            thinking = thinking,
                            images = entity.images,
                            timestamp = entity.timestamp
                        )
                        chatMessage
                    }
                }
                    .collect { messageList ->
                        // Merge with optimistic messages (messages with negative IDs are temporary)
                        val currentOptimistic = _messages.value.filter { it.id < 0 }
                        val dbMessages = messageList
                        
                        // Match DB messages with optimistic messages and preserve images from optimistic if DB has null
                        val dbMessagesWithPreservedImages = dbMessages.map { dbMsg ->
                            // Find matching optimistic message
                            val matchingOptimistic = currentOptimistic.firstOrNull { optMsg ->
                                optMsg.role == dbMsg.role &&
                                optMsg.content == dbMsg.content &&
                                kotlin.math.abs(optMsg.timestamp - dbMsg.timestamp) < 5000
                            }
                            
                            // If DB message has null images but optimistic has images, preserve optimistic images
                            if (matchingOptimistic != null && dbMsg.images == null && matchingOptimistic.images != null) {
                                dbMsg.copy(images = matchingOptimistic.images)
                            } else {
                                dbMsg
                            }
                        }
                        
                        // Keep optimistic messages that don't have a DB match yet
                        val unmatchedOptimistic = currentOptimistic.filter { optMsg ->
                            dbMessagesWithPreservedImages.none { dbMsg ->
                                optMsg.role == dbMsg.role &&
                                optMsg.content == dbMsg.content &&
                                kotlin.math.abs(optMsg.timestamp - dbMsg.timestamp) < 5000
                            }
                        }
                        
                        // Combine and sort by timestamp
                        val newMessages = (unmatchedOptimistic + dbMessagesWithPreservedImages).sortedBy { it.timestamp }
                        _messages.value = newMessages
                    }
            } catch (e: Exception) {
                // Catch any unexpected errors
                android.util.Log.e("ChatViewModel", "Unexpected error in observeMessages", e)
                // Don't show database-related errors to users
                if (e !is android.database.sqlite.SQLiteBlobTooBigException) {
                    _error.value = "Failed to load messages: ${e.message}"
                }
            }
        }
    }
    
    fun sendMessage(content: String, images: List<String>? = null) {
        val currentThreadId = _threadId.value
        val model = _selectedModel.value
        
        android.util.Log.d("ChatViewModel", "sendMessage called: content='$content', images=${images?.size ?: 0}, isLoading=${_isLoading.value}, threadId=$currentThreadId, model=$model")
        
        if (currentThreadId == null) {
            _error.value = "No thread selected"
            return
        }
        
        if (model == null) {
            _error.value = "No model selected"
            return
        }
        
        if (content.isBlank() && (images == null || images.isEmpty())) {
            android.util.Log.w("ChatViewModel", "sendMessage: Both content and images are empty, ignoring")
            return
        }
        
        // Don't block if already loading - allow queuing or at least log it
        if (_isLoading.value) {
            android.util.Log.w("ChatViewModel", "sendMessage: Already loading, but proceeding anyway")
        }
        
        // Optimistically add user message to UI immediately
        val optimisticUserMessage = ChatMessage(
            id = -System.currentTimeMillis(), // Negative ID to mark as temporary/optimistic
            threadId = currentThreadId,
            role = "user",
            content = content,
            thinking = null,
            images = images,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + optimisticUserMessage
        android.util.Log.d("ChatViewModel", "Added optimistic message with ${images?.size ?: 0} images")
        
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
                    
                    // Update thread timestamp
                    thread?.let {
                        chatRepository.updateThread(it)
                    }
                    
                    // Wait for database polling to detect the content change before clearing streaming content
                    // Polling now detects content changes (not just count), so wait 1.2 seconds to ensure it's detected
                    // This prevents the UI from showing a gap where neither streaming nor database content is visible
                    kotlinx.coroutines.delay(1200)
                    
                    _streamingContent.value = null
                    _streamingThinking.value = null
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error streaming message", e)
                    // Don't show database-related errors to users
                    if (e !is android.database.sqlite.SQLiteBlobTooBigException) {
                        _error.value = e.message ?: "Failed to stream message"
                    }
                } finally {
                    _isLoading.value = false
                }
            } else {
                // Non-streaming
                _isLoading.value = true
                _error.value = null
                
                val result = sendChatMessageUseCase(currentThreadId, content, model, false, systemPrompt, images)
                
                result.onFailure { exception ->
                    // Don't show database-related errors to users
                    if (exception !is android.database.sqlite.SQLiteBlobTooBigException) {
                        _error.value = exception.message ?: "Failed to send message"
                    }
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
    
    // Try to load images for a message on-demand (when they couldn't be loaded initially)
    fun loadMessageImages(messageId: Long) {
        viewModelScope.launch {
            try {
                val messageEntity = chatRepository.getMessageById(messageId)
                if (messageEntity != null && messageEntity.images != null) {
                    // Update the message in the list with images
                    val currentMessages = _messages.value
                    val updatedMessages = currentMessages.map { msg ->
                        if (msg.id == messageId && msg.images == null) {
                            android.util.Log.d("ChatViewModel", "Loading images on-demand for message $messageId: ${messageEntity.images?.size ?: 0} images")
                            msg.copy(images = messageEntity.images)
                        } else {
                            msg
                        }
                    }
                    _messages.value = updatedMessages
                }
            } catch (e: Exception) {
                android.util.Log.w("ChatViewModel", "Could not load images on-demand for message $messageId", e)
            }
        }
    }
    
    suspend fun getMessageById(messageId: Long): ChatMessage? {
        return try {
            val entity = chatRepository.getMessageById(messageId)
            entity?.let {
                ChatMessage(
                    id = it.id,
                    threadId = it.threadId,
                    role = it.role,
                    content = it.content,
                    thinking = it.thinking,
                    images = it.images,
                    timestamp = it.timestamp
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Could not get message $messageId", e)
            null
        }
    }
}

