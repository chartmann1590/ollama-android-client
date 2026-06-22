package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.preferences.UiPreferences
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class OllamaApiFactory(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val uiPreferences: UiPreferences
) {
    fun create(baseUrl: String): OllamaApi {
        val normalizedUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
        val apiUrl = if (normalizedUrl.endsWith("api/")) normalizedUrl else "${normalizedUrl}api/"
        val timeoutSecs = uiPreferences.requestTimeoutSeconds.value.toLong()
        val client = okHttpClient.newBuilder()
            .connectTimeout(timeoutSecs, TimeUnit.SECONDS)
            .readTimeout(timeoutSecs, TimeUnit.SECONDS)
            .writeTimeout(timeoutSecs, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(apiUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OllamaApi::class.java)
    }
}

