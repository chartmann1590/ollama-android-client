package com.charles.ollama.client.data.api

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OllamaApiFactory(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    fun create(baseUrl: String): OllamaApi {
        val normalizedUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
        val apiUrl = if (normalizedUrl.endsWith("api/")) normalizedUrl else "${normalizedUrl}api/"
        
        return Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OllamaApi::class.java)
    }
}

