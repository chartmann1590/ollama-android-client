package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.api.OllamaApi
import com.charles.ollama.client.data.api.OllamaApiFactory
import com.charles.ollama.client.data.api.OllamaStreamingService
import com.charles.ollama.client.data.api.dto.ChatMessageDto
import com.charles.ollama.client.data.api.dto.ChatRequest
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.util.ThinkingParser
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val chatThreadDao: ChatThreadDao,
    private val chatMessageDao: ChatMessageDao,
    private val apiFactory: OllamaApiFactory,
    private val streamingService: OllamaStreamingService
) {
    fun getAllThreads(): Flow<List<ChatThreadEntity>> = chatThreadDao.getAllThreads()
    
    fun getThreadById(threadId: Long): Flow<ChatThreadEntity?> = 
        chatThreadDao.getThreadByIdFlow(threadId)
    
    fun searchThreads(query: String): Flow<List<ChatThreadEntity>> = 
        chatThreadDao.searchThreads("%$query%")
    
    suspend fun createThread(title: String, model: String?, serverId: Long?): Long {
        val thread = ChatThreadEntity(
            title = title,
            model = model,
            serverId = serverId,
            streamEnabled = true // Default to enabled
        )
        return chatThreadDao.insertThread(thread)
    }
    
    suspend fun updateThread(thread: ChatThreadEntity) {
        val updated = thread.copy(updatedAt = System.currentTimeMillis())
        chatThreadDao.updateThread(updated)
    }
    
    suspend fun deleteThread(threadId: Long) {
        chatThreadDao.deleteThreadById(threadId)
        chatMessageDao.deleteMessagesByThreadId(threadId)
    }
    
    fun getMessagesByThreadId(threadId: Long): Flow<List<ChatMessageEntity>> = 
        chatMessageDao.getMessagesByThreadId(threadId)
    
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
            val existingMessages = chatMessageDao.getMessagesByThreadIdSync(threadId)
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
            chatMessageDao.insertMessage(userMessageEntity)
            
            if (shouldStream) {
                // For streaming, we'll handle it differently
                // Return a placeholder - actual streaming will be handled by streamMessage
                return Result.failure(Exception("Use streamMessage for streaming"))
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
                chatMessageDao.insertMessage(assistantMessage)
                
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
            val existingMessages = chatMessageDao.getMessagesByThreadIdSync(threadId)
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
            chatMessageDao.insertMessage(userMessageEntity)
            
            // Create placeholder assistant message for streaming
            val assistantMessageEntity = ChatMessageEntity(
                threadId = threadId,
                role = "assistant",
                content = ""
            )
            val assistantMessageId = chatMessageDao.insertMessage(assistantMessageEntity)
            
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
                        chatMessageDao.insertMessage(updatedMessage)
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
            chatMessageDao.insertMessage(finalMessage)
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
        chatMessageDao.insertMessage(message)
    }
}

