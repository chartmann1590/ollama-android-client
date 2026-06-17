package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.api.dto.ModelInfo
import com.charles.ollama.client.data.litert.ServerBackend
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.domain.model.ModelWithServer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetAllAvailableModelsUseCase @Inject constructor(
    private val serverRepository: ServerRepository,
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(): List<ModelWithServer> {
        val result = mutableListOf<ModelWithServer>()

        modelRepository.getLitertModels().onSuccess { infos ->
            infos.filter { it.modifiedAt == "installed" }.forEach { info ->
                result.add(ModelWithServer(info.toModel(), null, "On-Device"))
            }
        }

        val servers = serverRepository.getAllServers().first()
        servers.forEach { server ->
            if (server.backendType == ServerBackend.OLLAMA.name) {
                modelRepository.getModels(server.baseUrl).onSuccess { infos ->
                    infos.forEach { info ->
                        result.add(ModelWithServer(info.toModel(), server.id, server.name))
                    }
                }
            }
        }

        return result
    }

    private fun ModelInfo.toModel() = Model(
        name = name,
        modifiedAt = modifiedAt,
        size = size,
        digest = digest,
        parameterSize = details?.parameterSize,
        quantizationLevel = details?.quantizationLevel
    )
}
