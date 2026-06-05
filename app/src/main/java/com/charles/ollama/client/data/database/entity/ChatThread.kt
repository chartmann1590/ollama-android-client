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
    val showThinking: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    // Optional per-thread label/folder for organizing the thread list.
    val label: String? = null,
    // Optional per-thread Ollama model parameters. Null means "use the model default".
    // Sent as the request `options` map for the remote Ollama backend only.
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val numCtx: Int? = null,
    val seed: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

