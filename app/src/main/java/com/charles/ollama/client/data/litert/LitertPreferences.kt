package com.charles.ollama.client.data.litert

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight on-device settings for the LiteRT backend. Currently only
 * stores the Hugging Face access token used to download gated Gemma bundles.
 */
@Singleton
class LitertPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHuggingFaceToken(): String? =
        prefs.getString(KEY_HF_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun setHuggingFaceToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_HF_TOKEN) else putString(KEY_HF_TOKEN, token.trim())
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "litert_prefs"
        private const val KEY_HF_TOKEN = "hf_access_token"
    }
}
