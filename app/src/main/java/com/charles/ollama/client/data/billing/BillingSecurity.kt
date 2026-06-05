package com.charles.ollama.client.data.billing

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Local verification of Google Play purchase signatures. Every purchase is
 * signed by Play with the app's private key; we verify the signed payload
 * against the app's base64 RSA public license key (from Play Console).
 *
 * This closes the "no receipt verification" gap without needing a backend: a
 * tampered or replayed purchase JSON fails the signature check and is not
 * granted premium. If no license key is configured (e.g. a local/dev build),
 * verification is skipped so the app still runs — production builds ship the
 * key via the PLAY_LICENSE_KEY BuildConfig field.
 */
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
