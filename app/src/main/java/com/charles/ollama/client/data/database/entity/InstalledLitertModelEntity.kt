package com.charles.ollama.client.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_litert_models")
data class InstalledLitertModelEntity(
    @PrimaryKey
    val catalogId: String,
    val localFilePath: String,
    val expectedBytes: Long,
    val installedAt: Long = System.currentTimeMillis()
)
