package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class PullModelRequest(
    @SerializedName("name")
    val name: String,
    @SerializedName("stream")
    val stream: Boolean = false
)

