package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.api.OllamaApi
import com.charles.ollama.client.data.api.OllamaApiFactory
import com.charles.ollama.client.data.api.dto.ModelInfo
import com.charles.ollama.client.data.api.dto.PullModelRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class ModelRepository @Inject constructor(
    private val apiFactory: OllamaApiFactory
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

