package com.charles.ollama.client.domain.model

data class ModelWithServer(
    val model: Model,
    val serverId: Long?,
    val serverLabel: String
)
