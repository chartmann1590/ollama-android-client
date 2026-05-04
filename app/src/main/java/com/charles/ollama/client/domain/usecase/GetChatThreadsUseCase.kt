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
    operator fun invoke(archived: Boolean = false): Flow<List<ChatThread>> {
        return chatRepository.getThreads(archived).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun search(query: String, archived: Boolean = false): Flow<List<ChatThread>> {
        return chatRepository.searchThreads(query, archived).map { entities ->
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
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

