package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ShowModelResponse(
    @SerializedName("modelfile")
    val modelfile: String? = null,
    @SerializedName("parameters")
    val parameters: String? = null,
    @SerializedName("template")
    val template: String? = null,
    @SerializedName("details")
    val details: ModelDetails? = null,
    @SerializedName("license")
    val license: String? = null,
    @SerializedName("system")
    val system: String? = null
)

