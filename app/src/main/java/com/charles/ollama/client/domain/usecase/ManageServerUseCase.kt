package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import com.charles.ollama.client.data.litert.LitertConstants
import com.charles.ollama.client.data.litert.ServerBackend
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
                backendType = ServerBackend.OLLAMA.name,
                isDefault = isDefault
            )
            val id = serverRepository.insertServer(server)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addLitertLocalServer(isDefault: Boolean): Result<Long> {
        return try {
            val server = ServerConfigEntity(
                name = "On-device (LiteRT / Gemma)",
                baseUrl = LitertConstants.LOCAL_BASE_URL,
                backendType = ServerBackend.LITERT_LOCAL.name,
                isDefault = isDefault
            )
            Result.success(serverRepository.insertServer(server))
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
        backendType = backendType,
        isDefault = isDefault,
        createdAt = createdAt
    )
}

private fun Server.toEntity(): ServerConfigEntity {
    return ServerConfigEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        backendType = backendType,
        isDefault = isDefault,
        createdAt = createdAt
    )
}

