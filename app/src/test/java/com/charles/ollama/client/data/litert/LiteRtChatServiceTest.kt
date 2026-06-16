package com.charles.ollama.client.data.litert

import android.content.Context
import com.charles.ollama.client.data.database.entity.ChatMessageEntity
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Regression coverage for the `UnsatisfiedLinkError: litertlm_jni` crash on
 * app open. The previous `init { Engine.setNativeMinLogSeverity(...) }` block
 * forced the JNI .so to load the moment Hilt instantiated the singleton —
 * killing devices where the lib is missing for the device's ABI even if they
 * never used the on-device backend.
 *
 * On a JVM unit test the litertlm_jni library is not loadable. If anyone
 * re-introduces an eager LiteRT native call in `init` or in field initializers,
 * this test fails with `UnsatisfiedLinkError` at construction time.
 */
class LiteRtChatServiceTest {

    @Test
    fun `construction does not load the litertlm native library`() {
        val context = mock(Context::class.java)
        val service = LiteRtChatService(context)
        assertNotNull(service)
    }

    @Test
    fun `buildInitialMessages returns empty list for empty history`() {
        val service = LiteRtChatService(mock(Context::class.java))
        assertEquals(emptyList<Message>(), service.buildInitialMessages(emptyList()))
    }

    @Test
    fun `buildInitialMessages drops unpaired trailing user message`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = listOf(
            ChatMessageEntity(threadId = 1, role = "user", content = "Hello")
        )
        assertEquals(emptyList<Message>(), service.buildInitialMessages(history))
    }

    @Test
    fun `buildInitialMessages builds one user-model pair`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = listOf(
            ChatMessageEntity(threadId = 1, role = "user", content = "Hello"),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "Hi there")
        )
        val messages = service.buildInitialMessages(history)
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("Hello", messages[0].textContent())
        assertEquals(Role.MODEL, messages[1].role)
        assertEquals("Hi there", messages[1].textContent())
    }

    @Test
    fun `buildInitialMessages ignores system messages`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = listOf(
            ChatMessageEntity(threadId = 1, role = "system", content = "You are helpful"),
            ChatMessageEntity(threadId = 1, role = "user", content = "Hello"),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "Hi")
        )
        val messages = service.buildInitialMessages(history)
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals(Role.MODEL, messages[1].role)
    }

    @Test
    fun `buildInitialMessages filters blank content`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = listOf(
            ChatMessageEntity(threadId = 1, role = "user", content = "   "),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "Hi"),
            ChatMessageEntity(threadId = 1, role = "user", content = "Hello"),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "")
        )
        assertEquals(emptyList<Message>(), service.buildInitialMessages(history))
    }

    @Test
    fun `buildInitialMessages keeps only the last MAX_HISTORY_PAIRS complete pairs`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = (1..10).flatMap { i ->
            listOf(
                ChatMessageEntity(threadId = 1, role = "user", content = "Question $i"),
                ChatMessageEntity(threadId = 1, role = "assistant", content = "Answer $i")
            )
        }
        val messages = service.buildInitialMessages(history)
        // MAX_HISTORY_PAIRS = 4, each pair is 2 messages.
        assertEquals(8, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("Question 7", messages[0].textContent())
        assertEquals(Role.MODEL, messages[1].role)
        assertEquals("Answer 7", messages[1].textContent())
        assertEquals(Role.USER, messages[6].role)
        assertEquals("Question 10", messages[6].textContent())
        assertEquals(Role.MODEL, messages[7].role)
        assertEquals("Answer 10", messages[7].textContent())
    }

    @Test
    fun `buildInitialMessages handles interleaved incomplete pairs`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val history = listOf(
            ChatMessageEntity(threadId = 1, role = "user", content = "Q1"),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "A1"),
            ChatMessageEntity(threadId = 1, role = "user", content = "Q2"),
            ChatMessageEntity(threadId = 1, role = "user", content = "Q3"),
            ChatMessageEntity(threadId = 1, role = "assistant", content = "A3")
        )
        val messages = service.buildInitialMessages(history)
        // Q2 is dropped because it has no paired assistant; Q1/A1 and Q3/A3 are kept.
        assertEquals(4, messages.size)
        assertEquals("Q1", messages[0].textContent())
        assertEquals("A1", messages[1].textContent())
        assertEquals("Q3", messages[2].textContent())
        assertEquals("A3", messages[3].textContent())
    }

    // --- compactHistoryToTokenBudget ---

    @Test
    fun `compactHistoryToTokenBudget keeps all pairs when under budget`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val pairs = listOf("Hi" to "Hello", "How are you?" to "Fine")
        // Budget is 4096 * 0.55 = 2252 tokens; two tiny pairs cost ~1 each — all kept
        val result = service.compactHistoryToTokenBudget(pairs, 4096)
        assertEquals(2, result.size)
    }

    @Test
    fun `compactHistoryToTokenBudget drops oldest pairs when over budget`() {
        val service = LiteRtChatService(mock(Context::class.java))
        // Create pairs whose combined size exceeds a tiny budget
        val pairs = listOf(
            "A".repeat(200) to "B".repeat(200),  // ~100 tokens
            "C".repeat(200) to "D".repeat(200),  // ~100 tokens
            "E".repeat(200) to "F".repeat(200),  // ~100 tokens
        )
        // Budget = 300 * 0.55 = 165 tokens; only 1 pair (~100 tokens + 20 overhead) fits
        val result = service.compactHistoryToTokenBudget(pairs, 300)
        assertEquals(1, result.size)
        // Should keep the NEWEST pair
        assertEquals("E".repeat(200), result[0].first)
    }

    @Test
    fun `compactHistoryToTokenBudget returns empty list when budget too small for any pair`() {
        val service = LiteRtChatService(mock(Context::class.java))
        val pairs = listOf("Hello world" to "Hi there!")
        // Budget of 10 tokens is too small for even one pair
        val result = service.compactHistoryToTokenBudget(pairs, 10)
        assertEquals(0, result.size)
    }

    private fun Message.textContent(): String {
        val content = contents.contents.firstOrNull()
            ?: throw AssertionError("Message has no content")
        return (content as Content.Text).text
    }
}
