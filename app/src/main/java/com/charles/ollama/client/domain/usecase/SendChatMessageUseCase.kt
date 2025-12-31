package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.ChatMessage
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import javax.inject.Inject

class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(
        threadId: Long,
        content: String,
        model: String,
        streamEnabled: Boolean = false,
        systemPrompt: String? = null,
        images: List<String>? = null // Base64 encoded images
    ): Result<ChatMessage> {
        val defaultServer = serverRepository.getDefaultServerSync()
            ?: return Result.failure(Exception("No server configured"))
        
        val baseUrl = defaultServer.baseUrl
        val result = chatRepository.sendMessage(threadId, content, model, baseUrl, streamEnabled, systemPrompt, images)
        
        return result.map { entity ->
            ChatMessage(
                id = entity.id,
                threadId = entity.threadId,
                role = entity.role,
                content = entity.content,
                thinking = entity.thinking,
                timestamp = entity.timestamp
            )
        }
    }
    
    suspend fun streamMessage(
        threadId: Long,
        content: String,
        model: String,
        systemPrompt: String? = null,
        images: List<String>? = null // Base64 encoded images
    ): kotlinx.coroutines.flow.Flow<com.charles.ollama.client.data.repository.ChatRepository.StreamDelta> {
        val defaultServer = serverRepository.getDefaultServerSync()
            ?: return kotlinx.coroutines.flow.flow { 
                throw Exception("No server configured")
            }
        
        val baseUrl = defaultServer.baseUrl
        return chatRepository.streamMessage(threadId, content, model, baseUrl, systemPrompt, images)
    }
}

