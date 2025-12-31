package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface OllamaApi {
    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>
    
    @POST("generate")
    suspend fun generate(@Body request: GenerateRequest): Response<GenerateResponse>
    
    @GET("tags")
    suspend fun listModels(): Response<ModelListResponse>
    
    @POST("pull")
    suspend fun pullModel(@Body request: PullModelRequest): Response<PullModelResponse>
    
    @DELETE("delete")
    suspend fun deleteModel(@Query("name") modelName: String): Response<Unit>
    
    @POST("show")
    suspend fun showModel(@Body request: ShowModelRequest): Response<ShowModelResponse>
}

