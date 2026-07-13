package com.charles.ollama.client.data.translation

import android.content.Context
import android.content.SharedPreferences
import com.charles.ollama.client.data.preferences.UiPreferences
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TranslationStatus {
    data object Ready : TranslationStatus
    data object Downloading : TranslationStatus
    data object Translating : TranslationStatus
    data class Error(val message: String) : TranslationStatus
}

@Singleton
class TranslationRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val uiPreferences: UiPreferences
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    val supportedLanguages: List<AppLanguage> = AppLanguages.all
    val languageTag: StateFlow<String> = uiPreferences.languageTag

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Ready)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _cacheVersion = MutableStateFlow(0)
    val cacheVersion: StateFlow<Int> = _cacheVersion.asStateFlow()

    suspend fun selectLanguage(languageTag: String) {
        val supported = AppLanguages.find(languageTag).tag
        uiPreferences.setLanguageTag(supported)
        prepareLanguage(supported)
    }

    fun completeLanguageOnboarding() {
        uiPreferences.setLanguageOnboardingComplete(true)
    }

    fun cachedOrSource(source: String): String {
        val target = languageTag.value
        if (target == UiPreferences.DEFAULT_LANGUAGE_TAG || source.isBlank()) return source
        return prefs.getString(cacheKey(target, source), null) ?: source
    }

    suspend fun translate(source: String): String {
        val target = languageTag.value
        if (target == UiPreferences.DEFAULT_LANGUAGE_TAG || source.isBlank()) return source
        prefs.getString(cacheKey(target, source), null)?.let { return it }

        return mutex.withLock {
            prefs.getString(cacheKey(target, source), null)?.let { return@withLock it }
            try {
                prepareLanguage(target)
                val translated = withContext(Dispatchers.IO) {
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(target)
                        .build()
                    val translator = Translation.getClient(options)
                    try {
                        translator.translate(source).await()
                    } finally {
                        translator.close()
                    }
                }
                prefs.edit().putString(cacheKey(target, source), translated).apply()
                _cacheVersion.value += 1
                translated
            } catch (e: Exception) {
                _status.value = TranslationStatus.Error(e.localizedMessage ?: "Translation unavailable")
                source
            }
        }
    }

    suspend fun prepareLanguage(languageTag: String = this.languageTag.value) {
        if (languageTag == UiPreferences.DEFAULT_LANGUAGE_TAG) {
            _status.value = TranslationStatus.Ready
            return
        }
        try {
            _status.value = TranslationStatus.Downloading
            withContext(Dispatchers.IO) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(languageTag)
                    .build()
                val translator = Translation.getClient(options)
                try {
                    val conditions = DownloadConditions.Builder()
                        .requireWifi()
                        .build()
                    translator.downloadModelIfNeeded(conditions).await()
                } finally {
                    translator.close()
                }
            }
            _status.value = TranslationStatus.Ready
        } catch (e: Exception) {
            _status.value = TranslationStatus.Error(e.localizedMessage ?: "Language download failed")
        }
    }

    companion object {
        private const val PREFS_NAME = "translation_cache"

        private fun cacheKey(languageTag: String, source: String): String =
            "$languageTag:${sha256(source)}"

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
