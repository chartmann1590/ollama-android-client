package com.charles.ollama.client.di

import com.charles.ollama.client.data.billing.GitHubPurchaseBackend
import com.charles.ollama.client.data.billing.GitHubPaymentReturnHandler
import com.charles.ollama.client.data.billing.PaymentReturnHandler
import com.charles.ollama.client.data.billing.PurchaseBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindPurchaseBackend(impl: GitHubPurchaseBackend): PurchaseBackend

    @Binds
    @Singleton
    abstract fun bindPaymentReturnHandler(impl: GitHubPaymentReturnHandler): PaymentReturnHandler
}
