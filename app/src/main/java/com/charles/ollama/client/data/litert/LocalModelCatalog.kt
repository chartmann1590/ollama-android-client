package com.charles.ollama.client.data.litert

/**
 * Built-in LiteRT-LM bundles from Hugging Face
 * [litert-community](https://huggingface.co/litert-community).
 *
 * Every entry points at a `.litertlm` file that [com.google.ai.edge.litertlm.Engine]
 * can consume directly. When picking between quantizations, we prefer the
 * smallest CPU-friendly build (int4 / q8) over f32 so downloads stay
 * reasonable on mobile data and the model actually fits in RAM. Vendor-
 * specific variants (`.mediatek.*`, `.qualcomm.*`) are deliberately skipped —
 * they require device-detection we don't do yet.
 *
 * All listed repos are public. The HF-token machinery in [LitertPreferences] /
 * [ModelDownloadManager] is left in place for future gated entries.
 */
data class LocalModelCatalogEntry(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    /** Approximate file size in bytes (for UI / free-space hints). */
    val approximateSizeBytes: Long,
    val modelCardUrl: String,
    /** `true` if the Hugging Face repo requires accepting a gated license. */
    val requiresHuggingFaceToken: Boolean = false
) {
    /**
     * Ollama-style model name used in thread
     * [com.charles.ollama.client.data.database.entity.ChatThreadEntity.model].
     */
    val threadModelName: String get() = "litert/$id"
}

object LocalModelCatalog {
    const val GEMMA4_E2B = "gemma4_e2b"
    const val GEMMA4_E4B = "gemma4_e4b"
    const val GEMMA3_270M = "gemma3_270m"
    const val GEMMA3_1B = "gemma3_1b"
    const val QWEN3_0_6B = "qwen3_0_6b"
    const val QWEN2_5_1_5B = "qwen2_5_1_5b"
    const val DEEPSEEK_R1_QWEN_1_5B = "deepseek_r1_qwen_1_5b"
    const val PHI4_MINI = "phi4_mini"

    /**
     * Catalog IDs that earlier builds used. Kept so threads that stored those
     * names in [com.charles.ollama.client.data.database.entity.ChatThreadEntity.model]
     * still resolve to the current entry and the user keeps their chat history.
     */
    private val legacyIdAliases: Map<String, String> = mapOf(
        "gemma3n_e2b" to GEMMA4_E2B,
        "gemma3n_e4b" to GEMMA4_E4B
    )

    val entries: List<LocalModelCatalogEntry> = listOf(
        LocalModelCatalogEntry(
            id = GEMMA3_270M,
            displayName = "Gemma 3 270M IT (q8)",
            fileName = "gemma3-270m-it-q8.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm?download=true",
            approximateSizeBytes = 304_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/gemma-3-270m-it"
        ),
        LocalModelCatalogEntry(
            id = GEMMA3_1B,
            displayName = "Gemma 3 1B IT (int4)",
            fileName = "gemma3-1b-it-int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm?download=true",
            approximateSizeBytes = 584_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
        ),
        LocalModelCatalogEntry(
            id = QWEN3_0_6B,
            displayName = "Qwen 3 0.6B",
            fileName = "Qwen3-0.6B.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true",
            approximateSizeBytes = 614_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/Qwen3-0.6B"
        ),
        LocalModelCatalogEntry(
            id = QWEN2_5_1_5B,
            displayName = "Qwen 2.5 1.5B Instruct (q8)",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            approximateSizeBytes = 1_600_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct"
        ),
        LocalModelCatalogEntry(
            id = DEEPSEEK_R1_QWEN_1_5B,
            displayName = "DeepSeek R1 Distill Qwen 1.5B (q8)",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            approximateSizeBytes = 1_830_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B"
        ),
        LocalModelCatalogEntry(
            id = GEMMA4_E2B,
            displayName = "Gemma 4 E2B (LiteRT)",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
            approximateSizeBytes = 2_580_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
        ),
        LocalModelCatalogEntry(
            id = GEMMA4_E4B,
            displayName = "Gemma 4 E4B (LiteRT)",
            fileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            approximateSizeBytes = 3_650_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"
        ),
        LocalModelCatalogEntry(
            id = PHI4_MINI,
            displayName = "Phi-4 Mini Instruct (q8)",
            fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            approximateSizeBytes = 3_910_000_000L,
            modelCardUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct"
        )
    )

    fun byId(id: String): LocalModelCatalogEntry? {
        val canonical = legacyIdAliases[id] ?: id
        return entries.find { it.id == canonical }
    }

    fun fromThreadModelName(threadModel: String?): LocalModelCatalogEntry? {
        if (threadModel == null || !threadModel.startsWith("litert/")) return null
        return byId(threadModel.removePrefix("litert/"))
    }
}
