package com.charles.ollama.client.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.charles.ollama.client.data.database.converter.StringListConverter
import com.charles.ollama.client.data.database.dao.ChatMessageDao
import com.charles.ollama.client.data.database.dao.ChatThreadDao
import com.charles.ollama.client.data.database.dao.PromptPresetDao
import com.charles.ollama.client.data.database.dao.ServerConfigDao
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.charles.ollama.client.data.database.entity.ChatThreadEntity
import com.charles.ollama.client.data.database.entity.InstalledLitertModelEntity
import com.charles.ollama.client.data.database.entity.PromptPresetEntity
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao

@Database(
    entities = [
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        ServerConfigEntity::class,
        InstalledLitertModelEntity::class,
        PromptPresetEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class OllamaDatabase : RoomDatabase() {
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun installedLitertModelDao(): InstalledLitertModelDao
    abstract fun promptPresetDao(): PromptPresetDao
}
