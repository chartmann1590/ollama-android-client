package com.charles.ollama.client.data.database.dao

/**
 * Lightweight projection for global message search. Only a 200-char snippet of the
 * message content is selected (not the full row) so very large messages can't
 * overflow the SQLite CursorWindow.
 */
data class MessageSearchResult(
    val id: Long,
    val threadId: Long,
    val role: String,
    val snippet: String,
    val timestamp: Long,
    val threadTitle: String
)
