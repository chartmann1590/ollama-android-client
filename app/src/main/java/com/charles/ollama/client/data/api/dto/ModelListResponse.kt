package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ModelListResponse(
    @SerializedName("models")
    val models: List<ModelInfo>
)

