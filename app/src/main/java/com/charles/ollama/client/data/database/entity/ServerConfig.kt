package com.charles.ollama.client.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_configs")
data class ServerConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

