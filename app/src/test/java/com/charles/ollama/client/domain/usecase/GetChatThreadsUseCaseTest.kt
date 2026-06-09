package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.domain.model.ChatThread
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GetChatThreadsUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var getChatThreadsUseCase: GetChatThreadsUseCase

    @Before
    fun setup() {
        chatRepository = mock(ChatRepository::class.java)
        getChatThreadsUseCase = GetChatThreadsUseCase(chatRepository)
    }

    @Test
    fun `invoke should map ChatThreadEntity list to ChatThread list`() = runBlocking {
        // Arrange
        val entity1 = ChatThreadEntity(id = 1, title = "Thread 1", createdAt = 100L, updatedAt = 200L)
        val entity2 = ChatThreadEntity(id = 2, title = "Thread 2", createdAt = 300L, updatedAt = 400L)
        `when`(chatRepository.getThreads(false)).thenReturn(flowOf(listOf(entity1, entity2)))

        // Act
        val result = getChatThreadsUseCase(false).first()

        // Assert
        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Thread 1", result[0].title)
        assertEquals(2L, result[1].id)
        assertEquals("Thread 2", result[1].title)
    }

    @Test
    fun `search should map searched ChatThreadEntity list to ChatThread list`() = runBlocking {
        // Arrange
        val entity1 = ChatThreadEntity(id = 1, title = "Search Thread", createdAt = 100L, updatedAt = 200L)
        `when`(chatRepository.searchThreads("Search", false)).thenReturn(flowOf(listOf(entity1)))

        // Act
        val result = getChatThreadsUseCase.search("Search", false).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Search Thread", result[0].title)
    }

    @Test
    fun `invoke should pass archived flag correctly`() = runBlocking {
        // Arrange
        val entity1 = ChatThreadEntity(id = 1, title = "Archived Thread", isArchived = true, createdAt = 100L, updatedAt = 200L)
        `when`(chatRepository.getThreads(true)).thenReturn(flowOf(listOf(entity1)))

        // Act
        val result = getChatThreadsUseCase(true).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(true, result[0].isArchived)
    }

    @Test
    fun `search should pass archived flag correctly`() = runBlocking {
        // Arrange
        val entity1 = ChatThreadEntity(id = 1, title = "Archived Search Thread", isArchived = true, createdAt = 100L, updatedAt = 200L)
        `when`(chatRepository.searchThreads("Archived", true)).thenReturn(flowOf(listOf(entity1)))

        // Act
        val result = getChatThreadsUseCase.search("Archived", true).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals(true, result[0].isArchived)
    }
}
