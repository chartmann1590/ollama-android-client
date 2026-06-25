package com.charles.ollama.client.data.billing

import android.content.Intent

interface PaymentReturnHandler {
    fun handle(intent: Intent?)
}
