package com.charles.ollama.client.domain.model

data class Server(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val isDefault: Boolean,
    val createdAt: Long
)

