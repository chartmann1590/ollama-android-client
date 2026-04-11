package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.api.OllamaApi
import com.charles.ollama.client.data.api.OllamaApiFactory
import com.charles.ollama.client.data.api.OllamaStreamingService
import com.charles.ollama.client.data.api.dto.ChatMessageDto
import com.charles.ollama.client.data.api.dto.ChatRequest
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.litert.LocalModelCatalog
import com.charles.ollama.client.data.litert.LiteRtChatService
import com.charles.ollama.client.data.litert.ServerBackend
import com.charles.ollama.client.util.ThinkingParser
import com.charles.ollama.client.util.PerformanceMonitor
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val chatThreadDao: ChatThreadDao,
    private val chatMessageDao: ChatMessageDao,
    private val apiFactory: OllamaApiFactory,
    private val streamingService: OllamaStreamingService,
    private val serverRepository: ServerRepository,
    private val installedLitertModelDao: InstalledLitertModelDao,
    private val liteRtChatService: LiteRtChatService
) {
    fun getAllThreads(): Flow<List<ChatThreadEntity>> = chatThreadDao.getAllThreads()
    
    fun getThreadById(threadId: Long): Flow<ChatThreadEntity?> = 
        chatThreadDao.getThreadByIdFlow(threadId)
    
    fun searchThreads(query: String): Flow<List<ChatThreadEntity>> = 
        chatThreadDao.searchThreads("%$query%")
    
    suspend fun createThread(title: String, model: String?, serverId: Long?): Long {
        return PerformanceMonitor.measureSuspend("database_create_thread") {
            val thread = ChatThreadEntity(
                title = title,
                model = model,
                serverId = serverId,
                streamEnabled = true // Default to enabled
            )
            chatThreadDao.insertThread(thread)
        }
    }
    
    suspend fun updateThread(thread: ChatThreadEntity) {
        PerformanceMonitor.measureSuspend("database_update_thread") {
            val updated = thread.copy(updatedAt = System.currentTimeMillis())
            chatThreadDao.updateThread(updated)
        }
    }
    
    suspend fun deleteThread(threadId: Long) {
        PerformanceMonitor.measureSuspend("database_delete_thread") {
            chatThreadDao.deleteThreadById(threadId)
            chatMessageDao.deleteMessagesByThreadId(threadId)
        }
    }
    
    fun getMessagesByThreadId(threadId: Long): Flow<List<ChatMessageEntity>> = flow {
        // Load all messages one at a time to avoid CursorWindow overflow
        // Even a single row can be too large, so we load them individually
        suspend fun loadAllMessages(): List<ChatMessageEntity> {
            val allMessages = mutableListOf<ChatMessageEntity>()
            var offset = 0
            val batchSize = 1 // Load one message at a time to handle very large messages
            
            while (true) {
                try {
                    val batch = chatMessageDao.getMessagesByThreadIdPaged(threadId, batchSize, offset)
                    if (batch.isEmpty()) break
                    allMessages.addAll(batch)
                    offset += batchSize
                    // Small delay between loads to avoid overwhelming the system
                    delay(10)
                } catch (e: android.database.sqlite.SQLiteBlobTooBigException) {
                    // If even a single row is too large, try to load metadata only
                    android.util.Log.w("ChatRepository", "Message at offset $offset too large, trying metadata query", e)
                    try {
                        var metadataBatch = chatMessageDao.getMessagesByThreadIdPagedMetadata(threadId, batchSize, offset)
                        if (metadataBatch.isEmpty()) {
                            // Try basic query without images
                            metadataBatch = chatMessageDao.getMessagesByThreadIdPagedBasic(threadId, batchSize, offset)
                            if (metadataBatch.isEmpty()) {
                                offset += 1
                                continue
                            }
                        }
                        // Use metadata messages - they have empty content but preserve structure
                        // Try to load images separately for each message, but fall back to metadata if that fails
                        val fullMessages = metadataBatch.mapNotNull { metadataMsg ->
                            var messageWithImages = metadataMsg
                            // Try to load images separately (only for user messages to avoid unnecessary loads)
                            if (metadataMsg.role == "user") {
                                try {
                                    // Try to get full message by ID - may fail if images are too large
                                    val fullMsg = chatMessageDao.getMessageById(metadataMsg.id)
                                    if (fullMsg != null && fullMsg.images != null) {
                                        messageWithImages = metadataMsg.copy(images = fullMsg.images)
                                    } else if (fullMsg != null) {
                                        // Got full message but it has content - use it
                                        messageWithImages = fullMsg
                                    }
                                } catch (e2: Exception) {
                                    // If we can't load full message (images too large), use metadata version
                                    // Images will be null, but optimistic messages may have them preserved
                                    android.util.Log.w("ChatRepository", "Could not load full message ${metadataMsg.id}, using metadata (images too large)", e2)
                                }
                            } else {
                                // For non-user messages, try to get full message but don't worry about images
                                try {
                                    val fullMsg = chatMessageDao.getMessageById(metadataMsg.id)
                                    if (fullMsg != null) {
                                        messageWithImages = fullMsg
                                    }
                                } catch (e2: Exception) {
                                    // Use metadata version
                                    android.util.Log.w("ChatRepository", "Could not load full message ${metadataMsg.id}, using metadata", e2)
                                }
                            }
                            messageWithImages
                        }
                        allMessages.addAll(fullMessages)
                        offset += batchSize
                    } catch (e2: Exception) {
                        android.util.Log.e("ChatRepository", "Error loading metadata for message at offset $offset", e2)
                        // Try basic query as last resort
                        try {
                            val basicBatch = chatMessageDao.getMessagesByThreadIdPagedBasic(threadId, batchSize, offset)
                            if (basicBatch.isNotEmpty()) {
                                allMessages.addAll(basicBatch)
                                offset += batchSize
                            } else {
                                offset += 1
                            }
                        } catch (e3: Exception) {
                            android.util.Log.e("ChatRepository", "Error loading basic message at offset $offset", e3)
                            offset += 1 // Move to next message
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Error loading message batch at offset $offset", e)
                    break
                }
            }
            return allMessages
        }
        
        // Initial load - wrap in try-catch to handle any exceptions
        var lastMessageCount = 0
        var lastMessages = try {
            loadAllMessages()
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error in initial message load", e)
            emptyList()
        }
        lastMessageCount = lastMessages.size
        emit(lastMessages)
        
        // Poll for updates (every 1 second) to detect new messages or content changes
        while (true) {
            delay(1000)
            try {
                val currentCount = chatMessageDao.getMessageCount(threadId)
                
                // Check if count changed OR if the last assistant message's content changed
                // This handles the case where a placeholder message is created and then filled with content
                val lastAssistantMessage = lastMessages.filter { it.role == "assistant" }.lastOrNull()
                val lastAssistantContentLength = lastAssistantMessage?.content?.length ?: 0
                
                // Get current last assistant message content length to detect content updates
                // Only check if we have messages and the last one was an assistant message
                var contentChanged = false
                var currentAssistantContentLength = 0
                if (currentCount > 0 && lastAssistantMessage != null) {
                    try {
                        val currentLastMessage = chatMessageDao.getMessagesByThreadIdPaged(threadId, 1, currentCount - 1).firstOrNull()
                        currentAssistantContentLength = currentLastMessage?.takeIf { it.role == "assistant" }?.content?.length ?: 0
                        contentChanged = currentAssistantContentLength != lastAssistantContentLength
                    } catch (e: Exception) {
                        // If we can't check, assume no change to avoid unnecessary reloads
                        android.util.Log.w("ChatRepository", "Error checking content change", e)
                    }
                }
                
                if (currentCount != lastMessageCount || contentChanged) {
                    // Messages changed, reload - wrap in try-catch
                    try {
                        lastMessages = loadAllMessages()
                        lastMessageCount = lastMessages.size
                        emit(lastMessages)
                    } catch (e: Exception) {
                        android.util.Log.e("ChatRepository", "Error reloading messages", e)
                        // Continue polling even on error
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error checking for message updates", e)
                // Continue polling even on error
            }
        }
    }.catch { e ->
        android.util.Log.e("ChatRepository", "Error in getMessagesByThreadId Flow", e)
        // For SQLiteBlobTooBigException, just emit empty list - don't propagate
        // For other errors, also emit empty list to prevent crashes
        emit(emptyList())
    }
    
    suspend fun sendMessage(
        threadId: Long,
        content: String,
        model: String,
        baseUrl: String,
        streamEnabled: Boolean = false,
        systemPrompt: String? = null,
        images: List<String>? = null // Base64 encoded images
    ): Result<ChatMessageEntity> {
        return try {
            // Get thread to check streaming settings
            val thread = chatThreadDao.getThreadById(threadId)
            val shouldStream = streamEnabled || (thread?.streamEnabled == true)
            val effectiveSystemPrompt = systemPrompt ?: thread?.systemPrompt
            
            // Get existing messages for context
            // Use pagination to avoid CursorWindow overflow with large messages
            val existingMessages = try {
                chatMessageDao.getMessagesByThreadIdSync(threadId)
            } catch (e: android.database.sqlite.SQLiteBlobTooBigException) {
                // Fallback to paginated loading if we hit the CursorWindow limit
                android.util.Log.w("ChatRepository", "CursorWindow overflow in sendMessage, using paginated loading", e)
                val allMessages = mutableListOf<ChatMessageEntity>()
                var offset = 0
                val batchSize = 50
                while (true) {
                    val batch = chatMessageDao.getMessagesByThreadIdPaged(threadId, batchSize, offset)
                    if (batch.isEmpty()) break
                    allMessages.addAll(batch)
                    offset += batchSize
                }
                allMessages
            }
            val messageHistory = existingMessages.map { msg ->
                ChatMessageDto(role = msg.role, content = msg.content, images = msg.images)
            }
            
            // Build messages list with system prompt if present
            val allMessages = mutableListOf<ChatMessageDto>()
            if (effectiveSystemPrompt != null && effectiveSystemPrompt.isNotBlank()) {
                // Check if system message already exists
                val hasSystemMessage = messageHistory.any { it.role == "system" }
                if (!hasSystemMessage) {
                    allMessages.add(ChatMessageDto(role = "system", content = effectiveSystemPrompt))
                }
            }
            allMessages.addAll(messageHistory)
            
            // Add current user message (with images if provided)
            val userMessage = ChatMessageDto(
                role = "user",
                content = content,
                images = images
            )
            allMessages.add(userMessage)
            
            // Save user message to database (store text content and images)
            val userMessageEntity = ChatMessageEntity(
                threadId = threadId,
                role = "user",
                content = content,
                images = images
            )
            PerformanceMonitor.measureSuspend("database_insert_user_message") {
                chatMessageDao.insertMessage(userMessageEntity)
            }
            
            if (shouldStream) {
                // For streaming, we'll handle it differently
                // Return a placeholder - actual streaming will be handled by streamMessage
                return Result.failure(Exception("Use streamMessage for streaming"))
            }

            val defaultServerNs = serverRepository.getDefaultServerSync()
            val backendNs = defaultServerNs?.let { ServerBackend.fromStored(it.backendType) } ?: ServerBackend.OLLAMA
            if (backendNs == ServerBackend.LITERT_LOCAL) {
                if (!images.isNullOrEmpty()) {
                    return Result.failure(UnsupportedOperationException("Images are not supported for on-device LiteRT in this build."))
                }
                val catalogNs = LocalModelCatalog.fromThreadModelName(model)
                    ?: return Result.failure(IllegalStateException("Select a LiteRT model from the catalog."))
                val installedNs = installedLitertModelDao.getById(catalogNs.id)
                    ?: return Result.failure(IllegalStateException("Download this model from the Models screen first."))
                val full = StringBuilder()
                liteRtChatService.streamChat(
                    modelPath = installedNs.localFilePath,
                    systemPrompt = effectiveSystemPrompt,
                    historyBeforeUser = existingMessages,
                    userMessage = content
                ).collect { chunk -> full.append(chunk) }
                val (thinkingNs, responseContentNs) = ThinkingParser.parseThinking(full.toString())
                val assistantMessageNs = ChatMessageEntity(
                    threadId = threadId,
                    role = "assistant",
                    content = responseContentNs,
                    thinking = thinkingNs
                )
                PerformanceMonitor.measureSuspend("database_insert_assistant_message") {
                    chatMessageDao.insertMessage(assistantMessageNs)
                }
                thread?.let { updateThread(it) }
                return Result.success(assistantMessageNs)
            }

            // Send to API (non-streaming)
            val request = ChatRequest(
                model = model,
                messages = allMessages,
                stream = false
            )
            
            val api = apiFactory.create(baseUrl)
            val response = api.chat(request)
            
            if (response.isSuccessful && response.body() != null) {
                val chatResponse = response.body()!!
                // Parse thinking content for non-streaming responses too
                val (thinking, responseContent) = ThinkingParser.parseThinking(chatResponse.message.content)
                val assistantMessage = ChatMessageEntity(
                    threadId = threadId,
                    role = "assistant",
                    content = responseContent,
                    thinking = thinking
                )
                PerformanceMonitor.measureSuspend("database_insert_assistant_message") {
                    chatMessageDao.insertMessage(assistantMessage)
                }
                
                // Update thread timestamp
                thread?.let {
                    updateThread(it)
                }
                
                Result.success(assistantMessage)
            } else {
                Result.failure(Exception("Failed to get response: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    data class StreamDelta(
        val content: String,
        val thinking: String? = null
    )
    
    fun streamMessage(
        threadId: Long,
        content: String,
        model: String,
        baseUrl: String,
        systemPrompt: String? = null,
        images: List<String>? = null // Base64 encoded images
    ): Flow<StreamDelta> = flow {
        try {
            // Get existing messages for context
            // Use pagination to avoid CursorWindow overflow with large messages
            val existingMessages = try {
                chatMessageDao.getMessagesByThreadIdSync(threadId)
            } catch (e: android.database.sqlite.SQLiteBlobTooBigException) {
                // Fallback to paginated loading if we hit the CursorWindow limit
                android.util.Log.w("ChatRepository", "CursorWindow overflow in streamMessage, using paginated loading", e)
                val allMessages = mutableListOf<ChatMessageEntity>()
                var offset = 0
                val batchSize = 50
                while (true) {
                    val batch = chatMessageDao.getMessagesByThreadIdPaged(threadId, batchSize, offset)
                    if (batch.isEmpty()) break
                    allMessages.addAll(batch)
                    offset += batchSize
                }
                allMessages
            }
            val messageHistory = existingMessages.map { msg ->
                ChatMessageDto(role = msg.role, content = msg.content, images = msg.images)
            }
            
            // Build messages list with system prompt if present
            val allMessages = mutableListOf<ChatMessageDto>()
            if (systemPrompt != null && systemPrompt.isNotBlank()) {
                val hasSystemMessage = messageHistory.any { it.role == "system" }
                if (!hasSystemMessage) {
                    allMessages.add(ChatMessageDto(role = "system", content = systemPrompt))
                }
            }
            allMessages.addAll(messageHistory)
            
            // Add current user message (with images if provided)
            val userMessage = ChatMessageDto(
                role = "user",
                content = content,
                images = images
            )
            allMessages.add(userMessage)
            
            // Save user message to database (store text content and images)
            val userMessageEntity = ChatMessageEntity(
                threadId = threadId,
                role = "user",
                content = content,
                images = images
            )
            PerformanceMonitor.measureSuspend("database_insert_user_message") {
                chatMessageDao.insertMessage(userMessageEntity)
            }
            
            // Create placeholder assistant message for streaming
            val assistantMessageEntity = ChatMessageEntity(
                threadId = threadId,
                role = "assistant",
                content = ""
            )
            val assistantMessageId = PerformanceMonitor.measureSuspend("database_insert_placeholder_message") {
                chatMessageDao.insertMessage(assistantMessageEntity)
            }

            val defaultServer = serverRepository.getDefaultServerSync()
            val backend = defaultServer?.let { ServerBackend.fromStored(it.backendType) } ?: ServerBackend.OLLAMA
            if (backend == ServerBackend.LITERT_LOCAL) {
                if (!images.isNullOrEmpty()) {
                    throw UnsupportedOperationException("Images are not supported for on-device LiteRT in this build.")
                }
                val catalog = LocalModelCatalog.fromThreadModelName(model)
                    ?: throw IllegalStateException("Select a LiteRT model (Gemma) from the model list.")
                val installed = installedLitertModelDao.getById(catalog.id)
                    ?: throw IllegalStateException("Download this model from the Models screen first (can be several GB).")
                val threadRow = chatThreadDao.getThreadById(threadId)
                val effectiveSystem = systemPrompt ?: threadRow?.systemPrompt

                var fullContent = ""
                var deltaCount = 0
                try {
                    liteRtChatService.streamChat(
                        modelPath = installed.localFilePath,
                        systemPrompt = effectiveSystem,
                        historyBeforeUser = existingMessages,
                        userMessage = content
                    ).collect { chunk ->
                        deltaCount++
                        fullContent += chunk
                        if (deltaCount % 5 == 0 || chunk.length > 80) {
                            val updatedMessage = assistantMessageEntity.copy(
                                id = assistantMessageId,
                                content = fullContent,
                                thinking = null
                            )
                            PerformanceMonitor.measureSuspend("database_update_streaming_message") {
                                chatMessageDao.insertMessage(updatedMessage)
                            }
                        }
                        emit(StreamDelta(content = chunk, thinking = null))
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "LiteRT stream error: ${e.message}", e)
                    throw e
                }
                val finalMessage = ChatMessageEntity(
                    id = assistantMessageId,
                    threadId = threadId,
                    role = "assistant",
                    content = fullContent,
                    thinking = null,
                    timestamp = System.currentTimeMillis()
                )
                PerformanceMonitor.measureSuspend("database_save_final_message") {
                    chatMessageDao.insertMessage(finalMessage)
                }
                threadRow?.let { updateThread(it) }
                return@flow
            }

            // Create streaming request
            val request = ChatRequest(
                model = model,
                messages = allMessages,
                stream = true
            )
            
            // Stream the response
            var fullContent = ""
            var fullThinking = ""
            var deltaCount = 0
            
            try {
                streamingService.streamChat(baseUrl, request).collect { streamDelta ->
                    deltaCount++
                    fullContent += streamDelta.content
                    streamDelta.thinking?.let { thinkingDelta ->
                        fullThinking += thinkingDelta
                        android.util.Log.d("ChatRepository", "Received thinking delta: ${thinkingDelta.length} chars, total thinking: ${fullThinking.length}")
                    }
                    android.util.Log.d("ChatRepository", "Received delta $deltaCount: content=${streamDelta.content.length} chars, total content: ${fullContent.length}")
                    
                    // Update the message in database as we stream (throttle to avoid too many DB writes)
                    if (deltaCount % 5 == 0 || streamDelta.content.length > 100 || streamDelta.thinking != null) {
                        val updatedMessage = assistantMessageEntity.copy(
                            id = assistantMessageId,
                            content = fullContent,
                            thinking = fullThinking.takeIf { it.isNotEmpty() }
                        )
                        PerformanceMonitor.measureSuspend("database_update_streaming_message") {
                            chatMessageDao.insertMessage(updatedMessage)
                        }
                    }
                    // Emit both content and thinking
                    emit(StreamDelta(content = streamDelta.content, thinking = streamDelta.thinking))
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error collecting stream: ${e.message}", e)
                // Even if there's an error, save what we have
                if (fullContent.isNotEmpty()) {
                    android.util.Log.d("ChatRepository", "Saving partial content due to error: ${fullContent.length} chars")
                }
                throw e
            }
            
            android.util.Log.d("ChatRepository", "Stream collection completed. Total deltas: $deltaCount, Final content length: ${fullContent.length}, Final thinking length: ${fullThinking.length}")
            
            // CRITICAL: Always save the final complete message - this MUST happen
            android.util.Log.d("ChatRepository", "Streaming complete. Saving final message with ${fullContent.length} chars content, ${fullThinking.length} chars thinking")
            val finalMessage = ChatMessageEntity(
                id = assistantMessageId,
                threadId = threadId,
                role = "assistant",
                content = fullContent,
                thinking = fullThinking.takeIf { it.isNotEmpty() },
                timestamp = System.currentTimeMillis()
            )
            PerformanceMonitor.measureSuspend("database_save_final_message") {
                chatMessageDao.insertMessage(finalMessage)
            }
            android.util.Log.d("ChatRepository", "Final message saved to database with ${finalMessage.content.length} chars")
            
            // Update thread timestamp
            val thread = chatThreadDao.getThreadById(threadId)
            thread?.let {
                updateThread(it)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error in streamMessage", e)
            throw e
        }
    }
    
    suspend fun insertMessage(message: ChatMessageEntity) {
        PerformanceMonitor.measureSuspend("database_insert_message") {
            chatMessageDao.insertMessage(message)
        }
    }
    
    suspend fun getMessageById(messageId: Long): ChatMessageEntity? {
        return try {
            chatMessageDao.getMessageById(messageId)
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "Could not get message $messageId", e)
            null
        }
    }
}

