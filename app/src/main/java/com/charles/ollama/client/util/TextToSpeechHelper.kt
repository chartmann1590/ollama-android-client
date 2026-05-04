package com.charles.ollama.client.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Lightweight wrapper around [TextToSpeech] that's safe to keep in a Compose
 * screen scope: call [shutdown] from a `DisposableEffect` to stop and release
 * the engine.
 *
 * Initialization is async; use [isReady] to know when [speak] will actually
 * produce audio. Calling [speak] before init queues the request and flushes it
 * once the engine is ready.
 */
class TextToSpeechHelper(context: Context) {

    private val appContext = context.applicationContext

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
                _isReady.value = true
                pendingText?.let {
                    pendingText = null
                    speakInternal(it)
                }
            } else {
                Log.w(TAG, "TTS init failed with status=$status")
            }
        }
    }

    fun speak(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        if (!_isReady.value) {
            pendingText = cleaned
            return
        }
        speakInternal(cleaned)
    }

    private fun speakInternal(text: String) {
        val engine = tts ?: return
        engine.stop()
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "TTS shutdown error", e)
        }
        tts = null
        _isReady.value = false
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "TextToSpeechHelper"
    }
}
