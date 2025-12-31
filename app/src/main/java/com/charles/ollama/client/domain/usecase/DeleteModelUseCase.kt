package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import javax.inject.Inject

class DeleteModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(modelName: String): Result<Unit> {
        val defaultServer = serverRepository.getDefaultServerSync()
            ?: return Result.failure(Exception("No server configured"))
        
        return modelRepository.deleteModel(defaultServer.baseUrl, modelName)
    }
}

