package com.charles.ollama.client.util

object ThinkingParser {
    /**
     * Parses content that may contain thinking tags and separates thinking from response.
     * Different thinking models use different tags:
     * - deepseek-r1 uses <think>...</think> tags
     * - Some models may use <think>...</think> tags
     * 
     * @param content The raw content that may contain thinking tags
     * @return Pair where first is the thinking content (without tags) and second is the response content
     */
    fun parseThinking(content: String): Pair<String?, String> {
        var remainingContent = content
        val thinkingParts = mutableListOf<String>()
        
        // Extract all thinking blocks - try multiple tag patterns
        // deepseek-r1 uses <think>...</think> tags (most common)
        // Some models may use <think>...</think> tags
        val patterns = listOf(
            Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL),
            Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL),
            Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
        )
        
        // Debug: log the raw content to see what we're parsing
        android.util.Log.d("ThinkingParser", "Parsing content (${content.length} chars). Preview: ${content.take(200)}")
        
        // Check for any thinking-like tags first
        if (content.contains("<think", ignoreCase = true) || content.contains("<redacted", ignoreCase = true)) {
            android.util.Log.d("ThinkingParser", "Content contains thinking-like tags! Full content: ${content.take(2000)}")
        }
        
        for (pattern in patterns) {
            var matchResult = pattern.find(remainingContent)
            while (matchResult != null) {
                val thinkingContent = matchResult.groupValues[1]
                thinkingParts.add(thinkingContent)
                android.util.Log.d("ThinkingParser", "Found thinking block: ${thinkingContent.length} chars, preview: ${thinkingContent.take(100)}")
                // Remove the thinking block from content
                remainingContent = remainingContent.removeRange(matchResult.range)
                matchResult = pattern.find(remainingContent)
            }
        }
        
        // Combine all thinking parts
        val thinking = if (thinkingParts.isNotEmpty()) {
            val combined = thinkingParts.joinToString("\n\n").trim()
            android.util.Log.d("ThinkingParser", "Combined thinking: ${combined.length} chars")
            combined
        } else {
            null
        }
        
        // Clean up the response content (remove any remaining tags or whitespace)
        val response = remainingContent.trim()
        
        android.util.Log.d("ThinkingParser", "Result: thinking=${thinking != null} (${thinking?.length ?: 0} chars), response=${response.length} chars")
        
        return Pair(thinking, response)
    }
    
    /**
     * Checks if content contains thinking tags
     */
    fun hasThinking(content: String): Boolean {
        return (content.contains("<think>", ignoreCase = true) && 
                content.contains("</think>", ignoreCase = true)) ||
               (content.contains("<think>", ignoreCase = true) && 
                content.contains("</think>", ignoreCase = true)) ||
               (content.contains("<think>", ignoreCase = true) && 
                content.contains("</think>", ignoreCase = true))
    }
}
