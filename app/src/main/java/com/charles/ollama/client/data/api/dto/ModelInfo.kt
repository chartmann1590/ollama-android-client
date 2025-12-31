package com.charles.ollama.client.data.api.dto

import com.google.gson.annotations.SerializedName

data class ModelInfo(
    @SerializedName("name")
    val name: String,
    @SerializedName("modified_at")
    val modifiedAt: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("digest")
    val digest: String,
    @SerializedName("details")
    val details: ModelDetails? = null
)

data class ModelDetails(
    @SerializedName("parent_model")
    val parentModel: String? = null,
    @SerializedName("format")
    val format: String? = null,
    @SerializedName("family")
    val family: String? = null,
    @SerializedName("families")
    val families: List<String>? = null,
    @SerializedName("parameter_size")
    val parameterSize: String? = null,
    @SerializedName("quantization_level")
    val quantizationLevel: String? = null
)

