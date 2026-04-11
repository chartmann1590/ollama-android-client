package com.charles.ollama.client.domain.model

data class Server(
    val id: Long,
    val name: String,
    val baseUrl: String,
    /** [com.charles.ollama.client.data.litert.ServerBackend] name */
    val backendType: String = com.charles.ollama.client.data.litert.ServerBackend.OLLAMA.name,
    val isDefault: Boolean,
    val createdAt: Long
)

