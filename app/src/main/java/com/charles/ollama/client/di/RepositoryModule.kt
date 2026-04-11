package com.charles.ollama.client.di

import com.charles.ollama.client.data.api.OllamaApi
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
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
        streamingService: com.charles.ollama.client.data.api.OllamaStreamingService,
        serverRepository: ServerRepository,
        installedLitertModelDao: InstalledLitertModelDao,
        liteRtChatService: com.charles.ollama.client.data.litert.LiteRtChatService
    ): ChatRepository {
        return ChatRepository(
            chatThreadDao,
            chatMessageDao,
            apiFactory,
            streamingService,
            serverRepository,
            installedLitertModelDao,
            liteRtChatService
        )
    }
    
    @Provides
    @Singleton
    fun provideModelRepository(
        apiFactory: com.charles.ollama.client.data.api.OllamaApiFactory,
        installedLitertModelDao: InstalledLitertModelDao,
        modelDownloadManager: com.charles.ollama.client.data.litert.ModelDownloadManager
    ): ModelRepository {
        return ModelRepository(apiFactory, installedLitertModelDao, modelDownloadManager)
    }
    
    @Provides
    @Singleton
    fun provideServerRepository(
        serverConfigDao: ServerConfigDao
    ): ServerRepository {
        return ServerRepository(serverConfigDao)
    }
}

