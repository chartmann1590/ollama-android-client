package com.charles.ollama.client.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingParserTest {

    @Test
    fun `content without tags returns null thinking`() {
        val (thinking, response) = ThinkingParser.parseThinking("Hello there.")
        assertNull(thinking)
        assertEquals("Hello there.", response)
    }

    @Test
    fun `single thinking block is extracted`() {
        val raw = "<think>Let me compute it.</think>The answer is 42."
        val (thinking, response) = ThinkingParser.parseThinking(raw)
        assertNotNull(thinking)
        assertTrue(thinking!!.contains("Let me compute it."))
        assertEquals("The answer is 42.", response)
    }

    @Test
    fun `multiple thinking blocks are concatenated`() {
        val raw = "<think>A</think>Step 1.<think>B</think>Step 2."
        val (thinking, response) = ThinkingParser.parseThinking(raw)
        assertNotNull(thinking)
        assertTrue(thinking!!.contains("A"))
        assertTrue(thinking.contains("B"))
        // Both blocks should be removed from the visible response.
        assertTrue(response.contains("Step 1."))
        assertTrue(response.contains("Step 2."))
        assertFalse(response.contains("<think>"))
    }

    @Test
    fun `multi-line thinking spans newlines`() {
        val raw = "<think>line one\nline two</think>Done."
        val (thinking, response) = ThinkingParser.parseThinking(raw)
        assertNotNull(thinking)
        assertTrue(thinking!!.contains("line one"))
        assertTrue(thinking.contains("line two"))
        assertEquals("Done.", response)
    }

    @Test
    fun `hasThinking detects paired tags`() {
        assertTrue(ThinkingParser.hasThinking("<think>x</think> reply"))
        assertFalse(ThinkingParser.hasThinking("no tags here"))
    }

}
