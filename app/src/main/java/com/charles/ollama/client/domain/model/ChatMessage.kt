package com.charles.ollama.client.domain.model

data class ChatMessage(
    val id: Long,
    val threadId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinking: String? = null, // Thinking content from thinking models
    val images: List<String>? = null, // Base64 encoded images for vision models
    // Generation metrics reported by Ollama (null for on-device LiteRT replies).
    val evalCount: Int? = null,
    val evalDurationNs: Long? = null,
    val promptEvalCount: Int? = null,
    val totalDurationNs: Long? = null,
    val timestamp: Long
)

