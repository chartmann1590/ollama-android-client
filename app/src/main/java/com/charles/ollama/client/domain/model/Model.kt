package com.charles.ollama.client.domain.model

data class Model(
    val name: String,
    val modifiedAt: String,
    val size: Long,
    val digest: String,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null
) {
    /**
     * Detects if this model supports vision capabilities.
     * Vision models typically have "vision" in their name or are known vision models.
     */
    fun isVisionModel(): Boolean {
        val lowerName = name.lowercase()
        return lowerName.contains("vision") || 
               lowerName.contains("llava") ||
               lowerName.contains("bakllava") ||
               lowerName.contains("moondream") ||
               lowerName.contains("minicpm-v")
    }
}

