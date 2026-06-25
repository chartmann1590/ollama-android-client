package com.charles.ollama.client.data.billing

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayPaymentReturnHandler @Inject constructor() : PaymentReturnHandler {
    override fun handle(intent: Intent?) = Unit
}
