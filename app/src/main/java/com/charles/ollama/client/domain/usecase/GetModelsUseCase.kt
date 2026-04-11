package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.litert.ServerBackend
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.data.api.dto.ModelInfo
import javax.inject.Inject

class GetModelsUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(): Result<List<Model>> {
        val defaultServer = serverRepository.getDefaultServerSync()
            ?: return Result.failure(Exception("No server configured"))
        val backend = ServerBackend.fromStored(defaultServer.backendType)
        val result = modelRepository.getModelsForBackend(defaultServer.baseUrl, backend)
        return result.map { models ->
            models.map { it.toDomain() }
        }
    }
}

private fun ModelInfo.toDomain(): Model {
    return Model(
        name = name,
        modifiedAt = modifiedAt,
        size = size,
        digest = digest,
        parameterSize = details?.parameterSize,
        quantizationLevel = details?.quantizationLevel
    )
}

