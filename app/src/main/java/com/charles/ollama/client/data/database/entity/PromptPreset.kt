package com.charles.ollama.client.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-saved system-prompt preset / persona. Built-in presets are not stored here. */
@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
