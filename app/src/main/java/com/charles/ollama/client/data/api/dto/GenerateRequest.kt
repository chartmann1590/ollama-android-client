package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class GenerateRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("prompt")
    val prompt: String,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("options")
    val options: Map<String, Any>? = null
)

