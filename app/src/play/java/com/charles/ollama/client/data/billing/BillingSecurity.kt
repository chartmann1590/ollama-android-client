package com.charles.ollama.client.data.billing

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object BillingSecurity {
    private const val TAG = "BillingSecurity"
    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"

    fun verifyPurchase(base64PublicKey: String, signedData: String, signature: String): Boolean {
        if (base64PublicKey.isBlank()) {
            Log.w(TAG, "No Play license key configured; skipping signature verification")
            return true
        }
        if (signedData.isBlank() || signature.isBlank()) return false
        return try {
            verify(generatePublicKey(base64PublicKey), signedData, signature)
        } catch (e: Exception) {
            Log.e(TAG, "Purchase signature verification error", e)
            false
        }
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        val decoded = Base64.decode(encodedPublicKey, Base64.DEFAULT)
        return KeyFactory.getInstance(KEY_FACTORY_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(decoded))
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature64: String): Boolean {
        val signatureBytes = Base64.decode(signature64, Base64.DEFAULT)
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initVerify(publicKey)
            update(signedData.toByteArray())
        }
        if (!sig.verify(signatureBytes)) {
            Log.w(TAG, "Purchase signature did not match")
            return false
        }
        return true
    }
}
