package com.charles.ollama.client.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatMessage
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.domain.usecase.GetModelsUseCase
import com.charles.ollama.client.domain.usecase.SendChatMessageUseCase
import android.content.Context
import com.charles.ollama.client.util.VibrationHelper
import com.charles.ollama.client.util.ThinkingParser
import com.charles.ollama.client.util.PerformanceMonitor
import com.charles.ollama.client.util.RecentThreadShortcut
import com.charles.ollama.client.util.ThreadExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val serverRepository: ServerRepository,
    private val getModelsUseCase: GetModelsUseCase,
    private val vibrationHelper: VibrationHelper,
    @ApplicationContext private val appContext: Context
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

    // Tracks the in-flight streaming send so the user can stop generation.
    private var streamingJob: Job? = null
    
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

    // In-thread search state
    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    /** Indices into [messages] of bubbles whose content contains [searchQuery] (case-insensitive). */
    val matchMessageIndices: StateFlow<List<Int>> =
        combine(_messages, _searchQuery) { msgs, query ->
            val q = query.trim()
            if (q.isEmpty()) emptyList()
            else msgs.mapIndexedNotNull { idx, m ->
                if (m.content.contains(q, ignoreCase = true)) idx else null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
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
                _showThinking.value = it.showThinking
                // Refresh the "Resume last chat" launcher shortcut so a long-press
                // on the launcher icon jumps straight back into this thread.
                RecentThreadShortcut.update(appContext, it.id, it.title)
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
            val trace = PerformanceMonitor.startViewModelTrace("loadAvailableModels")
            try {
                _isLoadingModels.value = true
                val defaultServer = serverRepository.getDefaultServerSync()
                PerformanceMonitor.addAttribute(
                    trace,
                    "server_url",
                    defaultServer?.baseUrl?.take(100) ?: "none"
                )
                val result = getModelsUseCase()
                result.onSuccess { models ->
                    PerformanceMonitor.addMetric(trace, "models_count", models.size.toLong())
                    _availableModels.value = models
                    _selectedModel.value?.let { updateVisionModelStatus(it) }
                }.onFailure { exception ->
                    PerformanceMonitor.addAttribute(trace, "error", exception.javaClass.simpleName)
                    _error.value = "Failed to load models: ${exception.message}"
                }
            } catch (e: Exception) {
                PerformanceMonitor.addAttribute(trace, "error", e.javaClass.simpleName)
                _error.value = "Failed to load models: ${e.message}"
            } finally {
                _isLoadingModels.value = false
                PerformanceMonitor.stopTrace(trace)
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
                            evalCount = entity.evalCount,
                            evalDurationNs = entity.evalDurationNs,
                            promptEvalCount = entity.promptEvalCount,
                            totalDurationNs = entity.totalDurationNs,
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
        
        streamingJob = viewModelScope.launch {
            val trace = PerformanceMonitor.startViewModelTrace("sendMessage")
            PerformanceMonitor.addAttribute(trace, "has_images", (images?.isNotEmpty() == true).toString())
            PerformanceMonitor.addAttribute(trace, "content_length", content.length.toString())
            PerformanceMonitor.addAttribute(trace, "model", model ?: "unknown")
            
            val thread = chatRepository.getThreadById(currentThreadId).first()
            val streamEnabled = thread?.streamEnabled == true
            val systemPrompt = thread?.systemPrompt
            
            if (streamEnabled) {
                // Handle streaming
                PerformanceMonitor.addAttribute(trace, "streaming", "true")
                _isLoading.value = true
                _error.value = null
                _streamingContent.value = ""
                _streamingThinking.value = null
                val vibrationEnabled = thread?.vibrationEnabled != false // Default to true
                
                try {
                    var fullContent = ""
                    var fullThinking = ""
                    var deltaCount = 0L
                    sendChatMessageUseCase.streamMessage(currentThreadId, content, model, systemPrompt, images)
                        .collect { streamDelta ->
                            deltaCount++
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
                    _error.value = null
                    
                    PerformanceMonitor.addMetric(trace, "stream_deltas", deltaCount)
                    PerformanceMonitor.addMetric(trace, "final_content_length", fullContent.length.toLong())
                    PerformanceMonitor.addMetric(trace, "final_thinking_length", fullThinking.length.toLong())
                    
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // User tapped Stop. The repository persists the partial reply via a
                    // NonCancellable save, so drop the streaming overlay and let the saved
                    // message show through before re-throwing to honor cancellation.
                    _streamingContent.value = null
                    _streamingThinking.value = null
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error streaming message", e)
                    PerformanceMonitor.addAttribute(trace, "error", e.javaClass.simpleName)
                    // Don't show database-related errors to users
                    if (e !is android.database.sqlite.SQLiteBlobTooBigException) {
                        _error.value = e.message ?: "Failed to stream message"
                    }
                } finally {
                    _isLoading.value = false
                }
            } else {
                // Non-streaming
                PerformanceMonitor.addAttribute(trace, "streaming", "false")
                _isLoading.value = true
                _error.value = null
                
                val result = sendChatMessageUseCase(currentThreadId, content, model, false, systemPrompt, images)
                
                result.onFailure { exception ->
                    PerformanceMonitor.addAttribute(trace, "error", exception.javaClass.simpleName)
                    // Don't show database-related errors to users
                    if (exception !is android.database.sqlite.SQLiteBlobTooBigException) {
                        _error.value = exception.message ?: "Failed to send message"
                    }
                }
                result.onSuccess {
                    _error.value = null
                }
                
                _isLoading.value = false
            }
            PerformanceMonitor.stopTrace(trace)
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

    /**
     * Stop an in-flight streaming reply. Cancels the send coroutine; the repository
     * persists whatever text was generated so far so nothing is lost.
     */
    fun stopGeneration() {
        streamingJob?.cancel()
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
    
    /**
     * Persist per-thread Ollama generation parameters. Any null clears that
     * parameter (falls back to the model's default). Applies to the remote
     * Ollama backend only; the on-device LiteRT backend ignores them.
     */
    fun updateModelParams(
        temperature: Float?,
        topP: Float?,
        topK: Int?,
        numCtx: Int?,
        seed: Int?
    ) {
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(
                        temperature = temperature,
                        topP = topP,
                        topK = topK,
                        numCtx = numCtx,
                        seed = seed
                    )
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
        _threadId.value?.let { threadId ->
            viewModelScope.launch {
                val thread = chatRepository.getThreadById(threadId).first()
                thread?.let {
                    val updated = it.copy(showThinking = show)
                    chatRepository.updateThread(updated)
                }
            }
        }
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
    
    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
        if (!active) {
            _searchQuery.value = ""
            _currentMatchIndex.value = 0
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _currentMatchIndex.value = 0
    }

    fun nextMatch() {
        val matches = matchMessageIndices.value
        if (matches.isEmpty()) return
        _currentMatchIndex.value = (_currentMatchIndex.value + 1) % matches.size
    }

    fun previousMatch() {
        val matches = matchMessageIndices.value
        if (matches.isEmpty()) return
        val cur = _currentMatchIndex.value
        _currentMatchIndex.value = if (cur <= 0) matches.size - 1 else cur - 1
    }

    fun shareCurrentThread() {
        val id = _threadId.value ?: return
        viewModelScope.launch {
            try {
                val threadEntity = chatRepository.getThreadById(id).first() ?: return@launch
                val messages = chatRepository.getMessagesForExport(id)
                ThreadExporter.shareThread(appContext, threadEntity, messages)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to share thread", e)
                _error.value = "Failed to share chat: ${e.message}"
            }
        }
    }

    /**
     * Drop a single message. If the user deletes a user message, all the assistant
     * messages that came after also fall away — they are no longer well-grounded.
     * For an assistant message, only that message is removed.
     */
    fun deleteSingleMessage(messageId: Long) {
        val id = _threadId.value ?: return
        viewModelScope.launch {
            try {
                val msg = chatRepository.getMessageById(messageId) ?: return@launch
                if (msg.role == "user") {
                    chatRepository.truncateThreadFrom(id, msg.timestamp)
                } else {
                    chatRepository.deleteMessage(messageId)
                }
                // Optimistically drop from in-memory list so UI updates immediately.
                _messages.value = _messages.value.filterNot {
                    if (msg.role == "user") it.timestamp >= msg.timestamp
                    else it.id == messageId
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to delete message", e)
                _error.value = "Failed to delete message: ${e.message}"
            }
        }
    }

    /**
     * Re-ask the model the same question that produced [assistantMessageId].
     * Drops the assistant turn (and anything after it) and resends the prior
     * user message.
     */
    fun regenerateAssistant(assistantMessageId: Long) {
        val id = _threadId.value ?: return
        viewModelScope.launch {
            try {
                val assistant = chatRepository.getMessageById(assistantMessageId) ?: return@launch
                if (assistant.role != "assistant") return@launch
                // Find the last user message at or before the assistant's timestamp.
                val priorUser = _messages.value
                    .filter { it.role == "user" && it.timestamp <= assistant.timestamp && it.id > 0 }
                    .maxByOrNull { it.timestamp }
                    ?: return@launch
                // Drop the assistant message + everything after the prior user turn.
                chatRepository.truncateThreadFrom(id, priorUser.timestamp + 1)
                _messages.value = _messages.value.filter { it.timestamp <= priorUser.timestamp }
                sendMessage(priorUser.content, priorUser.images)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to regenerate", e)
                _error.value = "Failed to regenerate: ${e.message}"
            }
        }
    }

    /**
     * Edit a previously sent user message and resend. Truncates the thread at
     * (and including) the original message, then issues a fresh send with
     * [newContent] (and [newImages] if provided, otherwise reuses the original).
     */
    fun editAndResend(userMessageId: Long, newContent: String, newImages: List<String>? = null) {
        val id = _threadId.value ?: return
        viewModelScope.launch {
            try {
                val msg = chatRepository.getMessageById(userMessageId) ?: return@launch
                if (msg.role != "user") return@launch
                val imagesToUse = newImages ?: msg.images
                chatRepository.truncateThreadFrom(id, msg.timestamp)
                _messages.value = _messages.value.filter { it.timestamp < msg.timestamp }
                sendMessage(newContent, imagesToUse)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to edit/resend", e)
                _error.value = "Failed to update message: ${e.message}"
            }
        }
    }

    fun shareMessageById(messageId: Long) {
        viewModelScope.launch {
            try {
                val msg = chatRepository.getMessageById(messageId) ?: return@launch
                val title = _threadId.value?.let { tid ->
                    chatRepository.getThreadById(tid).first()?.title
                }
                ThreadExporter.shareMessage(appContext, msg, title)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to share message", e)
                _error.value = "Failed to share message: ${e.message}"
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
                    evalCount = it.evalCount,
                    evalDurationNs = it.evalDurationNs,
                    promptEvalCount = it.promptEvalCount,
                    totalDurationNs = it.totalDurationNs,
                    timestamp = it.timestamp
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Could not get message $messageId", e)
            null
        }
    }
}
