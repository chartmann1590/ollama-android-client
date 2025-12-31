package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ShowModelResponse(
    @SerializedName("modelfile")
    val modelfile: String,
    @SerializedName("parameters")
    val parameters: String,
    @SerializedName("template")
    val template: String,
    @SerializedName("details")
    val details: ModelDetails,
    @SerializedName("license")
    val license: String? = null,
    @SerializedName("system")
    val system: String? = null
)

