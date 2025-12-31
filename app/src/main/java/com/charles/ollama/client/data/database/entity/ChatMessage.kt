package com.charles.ollama.client.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.charles.ollama.client.data.database.converter.StringListConverter

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["threadId"])]
)
@TypeConverters(StringListConverter::class)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val threadId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinking: String? = null, // Thinking content from thinking models
    val images: List<String>? = null, // Base64 encoded images for vision models
    val timestamp: Long = System.currentTimeMillis()
)

