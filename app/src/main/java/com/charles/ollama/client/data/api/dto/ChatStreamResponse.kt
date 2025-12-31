package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ChatStreamResponse(
    @SerializedName("model")
    val model: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("message")
    val message: ChatMessageDto? = null,
    @SerializedName("done")
    val done: Boolean? = null,
    @SerializedName("total_duration")
    val totalDuration: Long? = null,
    @SerializedName("load_duration")
    val loadDuration: Long? = null,
    @SerializedName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerializedName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    @SerializedName("eval_count")
    val evalCount: Int? = null,
    @SerializedName("eval_duration")
    val evalDuration: Long? = null
)

