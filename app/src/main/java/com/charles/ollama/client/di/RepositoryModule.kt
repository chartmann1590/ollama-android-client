package com.charles.ollama.client.di

import com.charles.ollama.client.data.api.OllamaApi
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.dao.ServerConfigDao
import com.charles.ollama.client.data.repository.ChatRepository
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideChatRepository(
        chatThreadDao: ChatThreadDao,
        chatMessageDao: ChatMessageDao,
        apiFactory: com.charles.ollama.client.data.api.OllamaApiFactory,
        streamingService: com.charles.ollama.client.data.api.OllamaStreamingService
    ): ChatRepository {
        return ChatRepository(chatThreadDao, chatMessageDao, apiFactory, streamingService)
    }
    
    @Provides
    @Singleton
    fun provideModelRepository(
        apiFactory: com.charles.ollama.client.data.api.OllamaApiFactory
    ): ModelRepository {
        return ModelRepository(apiFactory)
    }
    
    @Provides
    @Singleton
    fun provideServerRepository(
        serverConfigDao: ServerConfigDao
    ): ServerRepository {
        return ServerRepository(serverConfigDao)
    }
}

