package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ManageServerUseCase @Inject constructor(
    private val serverRepository: ServerRepository
) {
    fun getAllServers(): Flow<List<Server>> {
        return serverRepository.getAllServers().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    fun getDefaultServer(): Flow<Server?> {
        return serverRepository.getDefaultServer().map { it?.toDomain() }
    }
    
    suspend fun addServer(name: String, baseUrl: String, isDefault: Boolean): Result<Long> {
        return try {
            val server = ServerConfigEntity(
                name = name,
                baseUrl = baseUrl,
                isDefault = isDefault
            )
            val id = serverRepository.insertServer(server)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateServer(server: Server): Result<Unit> {
        return try {
            val entity = server.toEntity()
            serverRepository.updateServer(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteServer(server: Server): Result<Unit> {
        return try {
            val entity = server.toEntity()
            serverRepository.deleteServer(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun setDefaultServer(serverId: Long): Result<Unit> {
        return try {
            serverRepository.setDefaultServer(serverId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun ServerConfigEntity.toDomain(): Server {
    return Server(
        id = id,
        name = name,
        baseUrl = baseUrl,
        isDefault = isDefault,
        createdAt = createdAt
    )
}

private fun Server.toEntity(): ServerConfigEntity {
    return ServerConfigEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        isDefault = isDefault,
        createdAt = createdAt
    )
}

