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
     * The litertlm-android AAR only ships native binaries for arm64-v8a and
     * x86_64 (verified by inspecting the AAR's jni/ directory across every
     * version currently in use) — there is no armeabi-v7a (32-bit ARM) build.
     * Devices whose primary ABI isn't one of these two get a real
     * UnsatisfiedLinkError the moment LiteRT tries to load its native library.
     * Check this up front so callers can show a clear message instead of
     * attempting and crashing (Crashlytics #19/#29/#33/#37).
     */
    fun isDeviceSupported(): Boolean =
        android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    /**
     * Streams assistant text deltas for one user turn. Prior history is passed
     * as [historyBeforeUser] (DB rows in order, excluding the current user
     * message row just inserted).
     *
     * History is token-budget-compacted before each attempt so the KV cache
     * never overflows. If the engine still throws, we step down to progressively
     * shorter history rather than immediately falling back to an empty context,
     * preserving as much recent conversation as possible.
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

        val effectiveMaxTokens = if (isGemma4) GEMMA4_MAX_NUM_TOKENS else DEFAULT_MAX_NUM_TOKENS

        // Build all complete user/assistant pairs from history, then trim to
        // what fits within the token budget so the KV cache won't overflow.
        val allPairs = buildHistoryPairs(historyBeforeUser)
        val compacted = compactHistoryToTokenBudget(allPairs, effectiveMaxTokens, userMessage)

        // Step-down fallback: compacted → half → 1 pair → empty.
        // This prevents a hard context-overflow crash from silently wiping all
        // history — we always keep as much recent context as the engine can hold.
        val half = compacted.takeLast(maxOf(1, compacted.size / 2))
        val one = compacted.takeLast(1)
        val historyAttempts = listOf(compacted, half, one, emptyList<Pair<String, String>>())
            .distinctBy { it.size }
            .map { pairs -> pairsToMessages(pairs) }

        var lastAttemptError: Throwable? = null
        for (backend in backendAttempts) {
            for (history in historyAttempts) {
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = if (isGemma4) GEMMA4_MAX_NUM_TOKENS else null,
                    cacheDir = cacheDir
                )
                try {
                    Log.i(
                        TAG,
                        "Starting LiteRT engine with backend=${backend.name}, " +
                            "historyMessages=${history.size}, maxTokens=${engineConfig.maxNumTokens}"
                    )
                    val generatedChunks = ArrayList<String>()
                    Engine(engineConfig).use { engine ->
                        engine.initialize()
                        val convConfig = ConversationConfig(
                            systemInstruction = systemPrompt?.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
                            initialMessages = history
                        )
                        engine.createConversation(convConfig).use { conversation ->
                            conversation.sendMessageAsync(userMessage).collect { chunk ->
                                generatedChunks.add(chunk.toString())
                            }
                        }
                    }
                    generatedChunks.forEach { emit(it) }
                    return@flow
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Catch Throwable, not just Exception: loading litertlm_jni throws
                    // UnsatisfiedLinkError (a LinkageError, i.e. an Error, not an
                    // Exception) on devices missing the native ABI (arm64-v8a/x86_64
                    // only - see isDeviceSupported()). Exception-only used to let that
                    // escape uncaught before this fallback loop ever ran, crashing the
                    // app instead of trying the next backend/history combination
                    // (Crashlytics #19/#29/#33/#37).
                    lastAttemptError = e
                    Log.w(
                        TAG,
                        "LiteRT generation failed for backend=${backend.name}, " +
                            "historyMessages=${history.size}",
                        e
                    )
                }
            }
        }
        // Normalize to a plain Exception before it leaves this function: every
        // attempt above failed identically for the same underlying reason (most
        // commonly UnsatisfiedLinkError on an unsupported ABI), and callers up
        // the stack (ChatRepository, ViewModels) already assume `catch (e:
        // Exception)` is enough - don't make them all learn about LinkageError too.
        val failure = lastAttemptError ?: IllegalStateException("Failed to run LiteRT engine.")
        throw if (failure is Exception) failure else RuntimeException(failure.message ?: "Failed to run LiteRT engine.", failure)
    }.flowOn(Dispatchers.Default)

    /**
     * Converts DB history rows into LiteRT [Message] objects, capped at
     * [MAX_HISTORY_PAIRS] complete user/assistant pairs. Kept for backward
     * compatibility and unit tests; [streamChat] uses [compactHistoryToTokenBudget]
     * instead for a token-aware limit.
     */
    internal fun buildInitialMessages(history: List<ChatMessageEntity>): List<Message> =
        pairsToMessages(buildHistoryPairs(history).takeLast(MAX_HISTORY_PAIRS))

    /** Extracts complete user/assistant pairs from DB rows (ignores system rows). */
    private fun buildHistoryPairs(history: List<ChatMessageEntity>): List<Pair<String, String>> {
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
        return completePairs
    }

    private fun pairsToMessages(pairs: List<Pair<String, String>>): List<Message> {
        val out = ArrayList<Message>(pairs.size * 2)
        pairs.forEach { (user, assistant) ->
            out.add(Message.user(user))
            out.add(Message.model(assistant))
        }
        return out
    }

    /**
     * Trims [pairs] to fit within [HISTORY_TOKEN_FRACTION] of [maxTokens], keeping
     * the most recent pairs first. Also tries to retain the very first pair as a
     * conversation anchor (sets topic/framing) and any pairs that share keywords
     * with [userMessage] (lightweight relevance search). Oldest non-relevant pairs
     * are dropped first.
     *
     * Token estimate: (chars / [CHARS_PER_TOKEN]) + [MESSAGE_OVERHEAD_TOKENS] per role.
     */
    internal fun compactHistoryToTokenBudget(
        pairs: List<Pair<String, String>>,
        maxTokens: Int,
        userMessage: String = ""
    ): List<Pair<String, String>> {
        if (pairs.isEmpty()) return emptyList()
        val budget = (maxTokens * HISTORY_TOKEN_FRACTION).toInt()
        fun estimatePairTokens(p: Pair<String, String>): Int =
            (p.first.length + p.second.length) / CHARS_PER_TOKEN + MESSAGE_OVERHEAD_TOKENS * 2

        // --- Step 1: fill sliding window from newest ---
        var spent = 0
        val keptIndices = mutableSetOf<Int>()
        for (i in pairs.indices.reversed()) {
            val cost = estimatePairTokens(pairs[i])
            if (spent + cost > budget) break
            keptIndices.add(i)
            spent += cost
        }

        // --- Step 2: anchor — keep first pair if budget allows ---
        if (0 !in keptIndices) {
            val firstCost = estimatePairTokens(pairs[0])
            if (spent + firstCost <= budget) {
                keptIndices.add(0)
                spent += firstCost
            }
        }

        // --- Step 3: keyword relevance — surface up to 2 older pairs that
        //     share content words with the current user message ---
        if (userMessage.isNotBlank() && spent < budget) {
            val keywords = userMessage.lowercase()
                .split(Regex("\\W+"))
                .filter { it.length >= 4 }
                .toSet()
            if (keywords.isNotEmpty()) {
                for (i in pairs.indices) {
                    if (i in keptIndices) continue
                    val pairText = (pairs[i].first + " " + pairs[i].second).lowercase()
                    if (keywords.any { pairText.contains(it) }) {
                        val cost = estimatePairTokens(pairs[i])
                        if (spent + cost <= budget) {
                            keptIndices.add(i)
                            spent += cost
                        }
                    }
                    // Limit keyword-matched inclusions to avoid over-stuffing
                    if (keptIndices.size > pairs.size - 2) break
                }
            }
        }

        return keptIndices.sorted().map { pairs[it] }
    }

    private companion object {
        const val TAG = "LiteRtChatService"
        const val GEMMA4_MAX_NUM_TOKENS = 2048
        const val DEFAULT_MAX_NUM_TOKENS = 4096
        const val HISTORY_TOKEN_FRACTION = 0.55
        const val CHARS_PER_TOKEN = 4
        const val MESSAGE_OVERHEAD_TOKENS = 10
        const val MAX_HISTORY_PAIRS = 4
    }
}
