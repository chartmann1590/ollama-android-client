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

    init {
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
    }

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
        val cacheDir = context.cacheDir.absolutePath
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = cacheDir
        )
        Engine(engineConfig).use { engine ->
            engine.initialize()
            val initialMessages = buildInitialMessages(historyBeforeUser)
            val convConfig = ConversationConfig(
                systemInstruction = systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                initialMessages = initialMessages
            )
            engine.createConversation(convConfig).use { conversation ->
                conversation.sendMessageAsync(userMessage).collect { chunk ->
                    emit(chunk.toString())
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private fun buildInitialMessages(history: List<ChatMessageEntity>): List<Message> {
        val out = ArrayList<Message>()
        for (msg in history) {
            when (msg.role) {
                "user" -> out.add(Message.user(msg.content))
                "assistant" -> out.add(Message.model(msg.content))
                "system" -> { /* system handled via ConversationConfig */ }
            }
        }
        return out
    }
}
