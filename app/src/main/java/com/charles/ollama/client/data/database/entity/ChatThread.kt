package com.charles.ollama.client.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val model: String? = null,
    val serverId: Long? = null,
    val streamEnabled: Boolean = true,
    val systemPrompt: String? = null,
    val vibrationEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

