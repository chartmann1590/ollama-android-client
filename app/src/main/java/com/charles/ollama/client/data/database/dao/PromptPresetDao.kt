package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.PromptPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptPresetDao {
    @Query("SELECT * FROM prompt_presets ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<PromptPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PromptPresetEntity): Long

    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
