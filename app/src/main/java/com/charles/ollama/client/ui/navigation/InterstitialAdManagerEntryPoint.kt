package com.charles.ollama.client.ui.navigation

import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.ads.InterstitialAdManager
import com.charles.ollama.client.ads.RewardedAdManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InterstitialAdManagerEntryPoint {
    fun interstitialAdManager(): InterstitialAdManager
    fun rewardedAdManager(): RewardedAdManager
    fun adGate(): AdGate
}

