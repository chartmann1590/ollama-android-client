package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ShowModelRequest(
    @SerializedName("name")
    val name: String
)

