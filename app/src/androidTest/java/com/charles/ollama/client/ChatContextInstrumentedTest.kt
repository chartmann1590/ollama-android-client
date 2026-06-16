package com.charles.ollama.client

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end smoke test that exercises the on-device chat path and verifies,
 * via logcat observation, that the LiteRT service is invoked with prior
 * conversation history rather than an empty context list.
 *
 * The test itself navigates into a thread, sends two turns, and waits for the
 * assistant reply. The actual context verification is done by grepping logcat
 * for the `LiteRtChatService` tag and confirming `historyMessages` is non-zero.
 */
class ChatContextInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onDeviceLiteRT_chatUsesConversationHistory() {
        // Open an existing thread that uses the on-device Gemma model.
        composeTestRule.onNodeWithText("hello").performClick()
        composeTestRule.waitForIdle()

        // Send a first turn that establishes a fact.
        composeTestRule.onNodeWithText("Type a message...").performTextInput("My name is Alice")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        // Wait for the on-device model to stream a reply.
        composeTestRule.waitForIdle()
        Thread.sleep(8_000)

        // Send a second turn that requires remembering the prior fact.
        composeTestRule.onNodeWithText("Type a message...").performTextInput("What is my name?")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(8_000)
    }
}
