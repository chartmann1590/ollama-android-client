package com.charles.ollama.client.di

import android.content.Context
import androidx.room.Room
import com.charles.ollama.client.data.database.OllamaDatabase
import com.charles.ollama.client.data.database.Migrations
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
import com.charles.ollama.client.data.database.dao.PromptPresetDao
import com.charles.ollama.client.data.database.dao.ServerConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OllamaDatabase {
        return Room.databaseBuilder(
            context,
            OllamaDatabase::class.java,
            "ollama_database"
        )
        .addMigrations(
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_9_10,
            Migrations.MIGRATION_10_11
        )
        .fallbackToDestructiveMigration()
        // Set query executor to handle large queries better
        .setQueryExecutor(java.util.concurrent.Executors.newFixedThreadPool(4))
        .build()
    }
    
    @Provides
    fun provideChatThreadDao(database: OllamaDatabase): ChatThreadDao {
        return database.chatThreadDao()
    }
    
    @Provides
    fun provideChatMessageDao(database: OllamaDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }
    
    @Provides
    fun provideServerConfigDao(database: OllamaDatabase): ServerConfigDao {
        return database.serverConfigDao()
    }

    @Provides
    fun provideInstalledLitertModelDao(database: OllamaDatabase): InstalledLitertModelDao {
        return database.installedLitertModelDao()
    }

    @Provides
    fun providePromptPresetDao(database: OllamaDatabase): PromptPresetDao {
        return database.promptPresetDao()
    }
}
