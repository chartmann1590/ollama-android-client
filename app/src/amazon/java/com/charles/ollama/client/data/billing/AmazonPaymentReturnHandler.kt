package com.charles.ollama.client.data.billing

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/** Amazon IAP completes in-process (PurchasingListener); there is no browser return to handle. */
@Singleton
class AmazonPaymentReturnHandler @Inject constructor() : PaymentReturnHandler {
    override fun handle(intent: Intent?) = Unit
}
