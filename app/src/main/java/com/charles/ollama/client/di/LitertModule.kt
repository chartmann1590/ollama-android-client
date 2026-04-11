package com.charles.ollama.client.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiteRtDownloadClient

@Module
@InstallIn(SingletonComponent::class)
object LitertModule {

    /** Long-running downloads without per-chunk body logging. */
    @Provides
    @Singleton
    @LiteRtDownloadClient
    fun provideLiteRtDownloadOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
