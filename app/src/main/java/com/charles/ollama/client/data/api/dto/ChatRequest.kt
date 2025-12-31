package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<ChatMessageDto>,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("options")
    val options: Map<String, Any>? = null
)

data class ChatMessageDto(
    @SerializedName("role")
    val role: String, // "user", "assistant", "system"
    @SerializedName("content")
    val content: String, // Text content - always a string for Ollama
    @SerializedName("images")
    val images: List<String>? = null, // Base64 encoded images for vision models
    @SerializedName("thinking")
    val thinking: String? = null // Thinking content from thinking models
)

