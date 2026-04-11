package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.api.OllamaApiFactory
import com.charles.ollama.client.data.api.dto.ModelDetails
import com.charles.ollama.client.data.api.dto.ModelInfo
import com.charles.ollama.client.data.api.dto.PullModelRequest
import com.charles.ollama.client.data.database.dao.InstalledLitertModelDao
import com.charles.ollama.client.data.litert.LocalModelCatalog
import com.charles.ollama.client.data.litert.ModelDownloadManager
import com.charles.ollama.client.data.litert.ServerBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ModelRepository @Inject constructor(
    private val apiFactory: OllamaApiFactory,
    private val installedLitertModelDao: InstalledLitertModelDao,
    private val modelDownloadManager: ModelDownloadManager
) {

    suspend fun getModels(baseUrl: String): Result<List<ModelInfo>> {
        return try {
            val api = apiFactory.create(baseUrl)
            val response = api.listModels()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.models)
            } else {
                Result.failure(Exception("Failed to fetch models: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLitertModels(): Result<List<ModelInfo>> {
        return try {
            val installed = installedLitertModelDao.getAll()
            val list = LocalModelCatalog.entries.map { entry ->
                val inst = installed.find { it.catalogId == entry.id }
                ModelInfo(
                    name = entry.threadModelName,
                    modifiedAt = if (inst != null) "installed" else "not installed",
                    size = inst?.expectedBytes ?: entry.approximateSizeBytes,
                    digest = "litert-${entry.id}",
                    details = ModelDetails(
                        parameterSize = "Gemma (LiteRT-LM)",
                        quantizationLevel = if (inst != null) "on-device" else "download required"
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelsForBackend(baseUrl: String, backend: ServerBackend): Result<List<ModelInfo>> {
        return when (backend) {
            ServerBackend.LITERT_LOCAL -> getLitertModels()
            ServerBackend.OLLAMA -> getModels(baseUrl)
        }
    }

    fun pullModel(baseUrl: String, modelName: String): Flow<PullProgress> = flow {
        try {
            val api = apiFactory.create(baseUrl)
            val request = PullModelRequest(name = modelName, stream = true)
            val response = api.pullModel(request)

            if (response.isSuccessful && response.body() != null) {
                val pullResponse = response.body()!!
                val progress = PullProgress(
                    status = pullResponse.status,
                    completed = pullResponse.completed ?: 0L,
                    total = pullResponse.total ?: 0L
                )
                emit(progress)
            } else {
                throw Exception("Failed to pull model: ${response.message()}")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    fun pullLitertModel(modelName: String): Flow<PullProgress> {
        val id = modelName.removePrefix("litert/")
        val entry = LocalModelCatalog.byId(id)
            ?: throw IllegalArgumentException("Unknown LiteRT model: $modelName")
        return modelDownloadManager.downloadAsFlow(entry)
    }

    fun pullModelForBackend(baseUrl: String, backend: ServerBackend, modelName: String): Flow<PullProgress> {
        return when (backend) {
            ServerBackend.LITERT_LOCAL -> pullLitertModel(modelName)
            ServerBackend.OLLAMA -> pullModel(baseUrl, modelName)
        }
    }

    suspend fun deleteModel(baseUrl: String, modelName: String): Result<Unit> {
        return try {
            val api = apiFactory.create(baseUrl)
            val response = api.deleteModel(modelName)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete model: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLitertModel(modelName: String): Result<Unit> {
        return try {
            val id = modelName.removePrefix("litert/")
            LocalModelCatalog.byId(id)
                ?: return Result.failure(Exception("Unknown LiteRT model: $modelName"))
            modelDownloadManager.deleteInstalled(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteModelForBackend(baseUrl: String, backend: ServerBackend, modelName: String): Result<Unit> {
        return when (backend) {
            ServerBackend.LITERT_LOCAL -> deleteLitertModel(modelName)
            ServerBackend.OLLAMA -> deleteModel(baseUrl, modelName)
        }
    }

    suspend fun getModelInfo(baseUrl: String, modelName: String): Result<com.charles.ollama.client.data.api.dto.ShowModelResponse> {
        return try {
            val api = apiFactory.create(baseUrl)
            val request = com.charles.ollama.client.data.api.dto.ShowModelRequest(name = modelName)
            val response = api.showModel(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get model info: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class PullProgress(
    val status: String,
    val completed: Long,
    val total: Long
) {
    val progress: Float
        get() = if (total > 0) completed.toFloat() / total.toFloat() else 0f
}
