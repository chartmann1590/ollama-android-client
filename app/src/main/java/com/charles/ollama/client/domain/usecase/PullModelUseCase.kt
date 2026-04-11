package com.charles.ollama.client.domain.usecase

import com.charles.ollama.client.data.litert.ServerBackend
import com.charles.ollama.client.data.repository.ModelRepository
import com.charles.ollama.client.data.repository.ServerRepository
import com.charles.ollama.client.data.repository.PullProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PullModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val serverRepository: ServerRepository
) {
    suspend operator fun invoke(modelName: String): Flow<PullProgress> {
        val defaultServer = serverRepository.getDefaultServerSync()
            ?: throw Exception("No server configured")
        val backend = ServerBackend.fromStored(defaultServer.backendType)
        return modelRepository.pullModelForBackend(defaultServer.baseUrl, backend, modelName)
    }
}

