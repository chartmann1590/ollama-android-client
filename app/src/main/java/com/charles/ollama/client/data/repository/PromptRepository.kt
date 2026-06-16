package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.database.dao.PromptPresetDao
import com.charles.ollama.client.data.database.entity.PromptPresetEntity
import com.charles.ollama.client.domain.prompt.BuiltInPrompts
import com.charles.ollama.client.domain.prompt.PromptPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Surfaces the built-in persona catalog alongside the user's saved presets, and
 * provides CRUD for the custom ones. Custom preset ids are prefixed with
 * "custom_" so the UI can tell them apart from built-ins.
 */
@Singleton
class PromptRepository @Inject constructor(
    private val promptPresetDao: PromptPresetDao
) {
    val builtIns: List<PromptPreset> = BuiltInPrompts.all
    val builtInPersonas: List<PromptPreset> = BuiltInPrompts.personas
    val builtInUtilities: List<PromptPreset> = BuiltInPrompts.utilities

    val customPresets: Flow<List<PromptPreset>> =
        promptPresetDao.getAllFlow().map { list -> list.map { it.toDomain() } }

    suspend fun saveCustom(title: String, text: String, existingId: String? = null) {
        val rowId = existingId?.removePrefix(CUSTOM_PREFIX)?.toLongOrNull() ?: 0L
        promptPresetDao.upsert(
            PromptPresetEntity(
                id = rowId,
                title = title.trim().ifBlank { "Untitled" },
                text = text.trim()
            )
        )
    }

    suspend fun deleteCustom(id: String) {
        val rowId = id.removePrefix(CUSTOM_PREFIX).toLongOrNull() ?: return
        promptPresetDao.deleteById(rowId)
    }

    private fun PromptPresetEntity.toDomain() = PromptPreset(
        id = "$CUSTOM_PREFIX$id",
        title = title,
        text = text,
        builtIn = false
    )

    companion object {
        const val CUSTOM_PREFIX = "custom_"
    }
}
