package com.charles.ollama.client.data.litert

object LitertConstants {
    /** Placeholder [com.charles.ollama.client.data.database.entity.ServerConfigEntity.baseUrl] for on-device LiteRT backend. */
    const val LOCAL_BASE_URL = "litert-local://"

    fun isLitertLocalBaseUrl(baseUrl: String): Boolean =
        baseUrl.trim().equals(LOCAL_BASE_URL, ignoreCase = true) ||
            baseUrl.startsWith("litert-local://", ignoreCase = true)
}

enum class ServerBackend {
    OLLAMA,
    LITERT_LOCAL;

    companion object {
        fun fromStored(value: String?): ServerBackend =
            entries.find { it.name == value } ?: OLLAMA
    }
}
