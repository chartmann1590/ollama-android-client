package com.charles.ollama.client.data.litert

import android.content.Context
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtChatService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile private var nativeLogSeverityApplied = false

    /**
     * Streams assistant text deltas for one user turn. Prior history is passed
     * as [historyBeforeUser] (DB rows in order, excluding the current user
     * message row just inserted).
     *
     * The Gemma engine runs on [Dispatchers.Default] via [flowOn]. Do not wrap
     * this body in `withContext`: a `flow { }` builder cannot emit from a
     * different dispatcher than the one that collected it, which triggers
     * `IllegalStateException: Flow invariant is violated` at runtime.
     */
    fun streamChat(
        modelPath: String,
        systemPrompt: String?,
        historyBeforeUser: List<ChatMessageEntity>,
        userMessage: String
    ): Flow<String> = flow {
        if (userMessage.isBlank()) return@flow
        // Touch the JNI lib only when the on-device backend is actually used.
        // Doing this in init crashed singletons on devices where the .so isn't
        // packaged for the ABI (UnsatisfiedLinkError on litertlm_jni).
        if (!nativeLogSeverityApplied) {
            runCatching { Engine.setNativeMinLogSeverity(LogSeverity.ERROR) }
            nativeLogSeverityApplied = true
        }
        val cacheDir = context.cacheDir.absolutePath
        val isGemma4 = modelPath.contains("gemma-4-", ignoreCase = true)
        val backendAttempts = if (isGemma4) {
            listOf(Backend.GPU(), Backend.CPU())
        } else {
            listOf(Backend.CPU())
        }

        var lastAttemptError: Exception? = null
        for (backend in backendAttempts) {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                maxNumTokens = if (isGemma4) GEMMA4_MAX_NUM_TOKENS else null,
                cacheDir = cacheDir
            )
            try {
                Log.i(TAG, "Starting LiteRT engine with backend=${backend.name}, maxTokens=${engineConfig.maxNumTokens}")
                val generatedChunks = ArrayList<String>()
                Engine(engineConfig).use { engine ->
                    engine.initialize()
                    val initialMessages = if (isGemma4) {
                        emptyList()
                    } else {
                        buildInitialMessages(historyBeforeUser)
                    }
                    val convConfig = ConversationConfig(
                        systemInstruction = systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                        initialMessages = initialMessages
                    )
                    engine.createConversation(convConfig).use { conversation ->
                        conversation.sendMessageAsync(userMessage).collect { chunk ->
                            generatedChunks.add(chunk.toString())
                        }
                    }
                }
                generatedChunks.forEach { emit(it) }
                return@flow
            } catch (e: Exception) {
                lastAttemptError = e
                Log.w(TAG, "LiteRT generation failed for backend=${backend.name}", e)
            }
        }
        throw lastAttemptError ?: IllegalStateException("Failed to run LiteRT engine.")
    }.flowOn(Dispatchers.Default)

    private fun buildInitialMessages(history: List<ChatMessageEntity>): List<Message> {
        val completePairs = ArrayList<Pair<String, String>>()
        var pendingUser: String? = null
        for (msg in history) {
            when (msg.role) {
                "user" -> pendingUser = msg.content.takeIf { it.isNotBlank() }
                "assistant" -> {
                    val user = pendingUser
                    val assistant = msg.content.takeIf { it.isNotBlank() }
                    if (user != null && assistant != null) {
                        completePairs.add(user to assistant)
                        pendingUser = null
                    }
                }
                "system" -> { /* system handled via ConversationConfig */ }
            }
        }
        val out = ArrayList<Message>()
        completePairs.takeLast(MAX_HISTORY_PAIRS).forEach { (user, assistant) ->
            out.add(Message.user(user))
            out.add(Message.model(assistant))
        }
        return out
    }

    private companion object {
        const val TAG = "LiteRtChatService"
        const val GEMMA4_MAX_NUM_TOKENS = 1024
        const val MAX_HISTORY_PAIRS = 4
    }
}
