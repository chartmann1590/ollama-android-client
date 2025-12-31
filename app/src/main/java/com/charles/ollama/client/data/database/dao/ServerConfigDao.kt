package com.charles.ollama.client.data.database.dao

import androidx.room.*
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_configs ORDER BY isDefault DESC, name ASC")
    fun getAllServers(): Flow<List<ServerConfigEntity>>
    
    @Query("SELECT * FROM server_configs WHERE id = :serverId")
    suspend fun getServerById(serverId: Long): ServerConfigEntity?
    
    @Query("SELECT * FROM server_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultServer(): ServerConfigEntity?
    
    @Query("SELECT * FROM server_configs WHERE isDefault = 1 LIMIT 1")
    fun getDefaultServerFlow(): Flow<ServerConfigEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerConfigEntity): Long
    
    @Update
    suspend fun updateServer(server: ServerConfigEntity)
    
    @Delete
    suspend fun deleteServer(server: ServerConfigEntity)
    
    @Query("UPDATE server_configs SET isDefault = 0")
    suspend fun clearDefaultServers()
    
    @Query("UPDATE server_configs SET isDefault = 1 WHERE id = :serverId")
    suspend fun setDefaultServer(serverId: Long)
}

