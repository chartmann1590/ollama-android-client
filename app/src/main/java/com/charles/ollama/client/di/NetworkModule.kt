package com.charles.ollama.client.di

import com.charles.ollama.client.data.api.OllamaApi
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.charles.ollama.client.data.api.PerformanceInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Add Firebase Performance interceptor first to capture all network metrics
        val performanceInterceptor = PerformanceInterceptor()
        
        return OkHttpClient.Builder()
            .addInterceptor(performanceInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        // Default base URL - will be overridden dynamically
        return Retrofit.Builder()
            .baseUrl("http://localhost:11434/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOllamaApi(retrofit: Retrofit): OllamaApi {
        return retrofit.create(OllamaApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideOllamaApiFactory(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): com.charles.ollama.client.data.api.OllamaApiFactory {
        return com.charles.ollama.client.data.api.OllamaApiFactory(okHttpClient, gson)
    }
    
    @Provides
    @Singleton
    fun provideOllamaStreamingService(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): com.charles.ollama.client.data.api.OllamaStreamingService {
        return com.charles.ollama.client.data.api.OllamaStreamingService(okHttpClient, gson)
    }
}

