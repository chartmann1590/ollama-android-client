package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.database.dao.ServerConfigDao
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import com.charles.ollama.client.util.PerformanceMonitor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ServerRepository @Inject constructor(
    private val serverConfigDao: ServerConfigDao
) {
    fun getAllServers(): Flow<List<ServerConfigEntity>> = serverConfigDao.getAllServers()
    
    suspend fun getServerById(serverId: Long): ServerConfigEntity? = 
        serverConfigDao.getServerById(serverId)
    
    fun getDefaultServer(): Flow<ServerConfigEntity?> = serverConfigDao.getDefaultServerFlow()
    
    suspend fun getDefaultServerSync(): ServerConfigEntity? = serverConfigDao.getDefaultServer()
    
    suspend fun insertServer(server: ServerConfigEntity): Long {
        return PerformanceMonitor.measureSuspend("database_insert_server") {
            if (server.isDefault) {
                serverConfigDao.clearDefaultServers()
            }
            serverConfigDao.insertServer(server)
        }
    }
    
    suspend fun updateServer(server: ServerConfigEntity) {
        PerformanceMonitor.measureSuspend("database_update_server") {
            if (server.isDefault) {
                serverConfigDao.clearDefaultServers()
            }
            serverConfigDao.updateServer(server)
        }
    }
    
    suspend fun deleteServer(server: ServerConfigEntity) {
        PerformanceMonitor.measureSuspend("database_delete_server") {
            serverConfigDao.deleteServer(server)
        }
    }
    
    suspend fun setDefaultServer(serverId: Long) {
        PerformanceMonitor.measureSuspend("database_set_default_server") {
            serverConfigDao.clearDefaultServers()
            serverConfigDao.setDefaultServer(serverId)
        }
    }
}

