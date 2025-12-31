package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.domain.model.ChatThread
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetChatThreadsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<ChatThread>> {
        return chatRepository.getAllThreads().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun search(query: String): Flow<List<ChatThread>> {
        return chatRepository.searchThreads(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}

private fun ChatThreadEntity.toDomain(): ChatThread {
    return ChatThread(
        id = id,
        title = title,
        model = model,
        serverId = serverId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

