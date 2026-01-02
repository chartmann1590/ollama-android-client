package com.charles.ollama.client.ui.navigation

import com.charles.ollama.client.ads.InterstitialAdManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InterstitialAdManagerEntryPoint {
    fun interstitialAdManager(): InterstitialAdManager
}

