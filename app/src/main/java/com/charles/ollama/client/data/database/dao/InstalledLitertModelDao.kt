package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.InstalledLitertModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledLitertModelDao {
    @Query("SELECT * FROM installed_litert_models")
    fun getAllFlow(): Flow<List<InstalledLitertModelEntity>>

    @Query("SELECT * FROM installed_litert_models")
    suspend fun getAll(): List<InstalledLitertModelEntity>

    @Query("SELECT * FROM installed_litert_models WHERE catalogId = :id LIMIT 1")
    suspend fun getById(id: String): InstalledLitertModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstalledLitertModelEntity)

    @Query("DELETE FROM installed_litert_models WHERE catalogId = :id")
    suspend fun deleteById(id: String)
}
