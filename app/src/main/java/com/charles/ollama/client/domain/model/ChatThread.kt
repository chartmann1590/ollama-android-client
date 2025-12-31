package com.charles.ollama.client.domain.model

data class ChatThread(
    val id: Long,
    val title: String,
    val model: String?,
    val serverId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

