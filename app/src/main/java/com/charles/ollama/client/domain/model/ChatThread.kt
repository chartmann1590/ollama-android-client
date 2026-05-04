package com.charles.ollama.client.domain.model

data class ChatThread(
    val id: Long,
    val title: String,
    val model: String?,
    val serverId: Long?,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

