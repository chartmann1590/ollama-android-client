package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class PullModelResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("digest")
    val digest: String? = null,
    @SerializedName("total")
    val total: Long? = null,
    @SerializedName("completed")
    val completed: Long? = null
)

