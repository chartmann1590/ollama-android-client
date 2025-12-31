package com.charles.ollama.client.data.api

import com.charles.ollama.client.data.api.dto.ChatRequest
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.IOException
import java.util.concurrent.TimeUnit

class OllamaStreamingService(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    data class StreamDelta(
        val content: String,
        val thinking: String? = null
    )
    
    fun streamChat(baseUrl: String, request: ChatRequest): Flow<StreamDelta> = callbackFlow {
        val normalizedUrl = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
        val apiUrl = if (normalizedUrl.endsWith("api/")) normalizedUrl else "${normalizedUrl}api/"
        val url = "${apiUrl}chat"
        
        android.util.Log.d("OllamaStreaming", "Starting stream to: $url")
        
        // Create a request with stream=true
        val streamingRequest = request.copy(stream = true)
        val requestJson = gson.toJson(streamingRequest)
        android.util.Log.d("OllamaStreaming", "Request: $requestJson")
        
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            requestJson
        )
        
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        
        val call = okHttpClient.newCall(httpRequest)
        
        // Use withContext for blocking I/O in coroutine context
        try {
            withContext(Dispatchers.IO) {
                val response = call.execute()
                
                android.util.Log.d("OllamaStreaming", "Response code: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "No error body"
                    android.util.Log.e("OllamaStreaming", "Error response: $errorBody")
                    close(IOException("Unexpected code ${response.code}: ${response.message}. Body: $errorBody"))
                    return@withContext
                }
                
                response.body?.let { body ->
                    try {
                        body.source().use { source ->
                            var lineCount = 0
                            var isDone = false
                            var totalContentEmitted = 0
                            
                            // Read ALL lines until stream is completely exhausted
                            while (true) {
                                val line = try {
                                    source.readUtf8Line()
                                } catch (e: Exception) {
                                    android.util.Log.e("OllamaStreaming", "Error reading line", e)
                                    break
                                }
                                
                                if (line == null) {
                                    // End of stream - break only if we've seen done flag
                                    if (isDone) {
                                        android.util.Log.d("OllamaStreaming", "Reached end of stream after done flag")
                                        break
                                    }
                                    // If not done, check if stream is exhausted
                                    if (source.exhausted()) {
                                        android.util.Log.d("OllamaStreaming", "Stream exhausted without done flag")
                                        break
                                    }
                                    continue
                                }
                                
                                lineCount++
                                
                                if (line.isBlank()) {
                                    continue
                                }
                                
                                android.util.Log.d("OllamaStreaming", "Received line $lineCount: ${line.take(200)}...")
                                
                                // Ollama sends JSON objects line by line when streaming
                                try {
                                    val chatResponse = gson.fromJson(
                                        line,
                                        com.charles.ollama.client.data.api.dto.ChatStreamResponse::class.java
                                    )
                                    
                                    // Extract content and thinking separately
                                    // In responses, content is always a String
                                    val contentDelta = chatResponse.message?.content ?: ""
                                    val thinkingDelta = chatResponse.message?.thinking
                                    
                                    // Emit content delta if present
                                    if (contentDelta.isNotEmpty()) {
                                        totalContentEmitted += contentDelta.length
                                        android.util.Log.d("OllamaStreaming", "Content delta (${contentDelta.length} chars): ${contentDelta.take(50)}")
                                        try {
                                            send(StreamDelta(content = contentDelta, thinking = null))
                                        } catch (e: Exception) {
                                            android.util.Log.e("OllamaStreaming", "Failed to send content delta - channel closed: ${e.message}")
                                            throw e
                                        }
                                    }
                                    
                                    // Emit thinking delta if present
                                    if (thinkingDelta != null && thinkingDelta.isNotEmpty()) {
                                        android.util.Log.w("OllamaStreaming", "*** THINKING DELTA FOUND! (${thinkingDelta.length} chars): ${thinkingDelta.take(100)}")
                                        try {
                                            send(StreamDelta(content = "", thinking = thinkingDelta))
                                        } catch (e: Exception) {
                                            android.util.Log.e("OllamaStreaming", "Failed to send thinking delta - channel closed: ${e.message}")
                                            throw e
                                        }
                                    }
                                    
                                    // Log if done flag has empty content
                                    if (chatResponse.done == true && (chatResponse.message?.content.isNullOrEmpty())) {
                                        android.util.Log.d("OllamaStreaming", "Done flag with empty content - this is the completion marker")
                                    }
                                    
                                    // Mark as done but CONTINUE reading to get any remaining data
                                    if (chatResponse.done == true) {
                                        android.util.Log.d("OllamaStreaming", "Stream done flag received. Total lines so far: $lineCount. Total content emitted: $totalContentEmitted chars")
                                        isDone = true
                                        // Don't break immediately - read a few more lines in case there's trailing data
                                        var additionalLinesRead = 0
                                        while (additionalLinesRead < 5 && !source.exhausted()) {
                                            val additionalLine = try {
                                                source.readUtf8Line()
                                            } catch (e: Exception) {
                                                break
                                            }
                                            if (additionalLine == null || additionalLine.isBlank()) {
                                                break
                                            }
                                            additionalLinesRead++
                                            android.util.Log.d("OllamaStreaming", "Reading additional line after done: ${additionalLine.take(100)}")
                                            try {
                                                val additionalResponse = gson.fromJson(
                                                    additionalLine,
                                                    com.charles.ollama.client.data.api.dto.ChatStreamResponse::class.java
                                                )
                                                val additionalContent = additionalResponse.message?.content ?: ""
                                                val additionalThinking = additionalResponse.message?.thinking
                                                if (additionalContent.isNotEmpty()) {
                                                    totalContentEmitted += additionalContent.length
                                                    android.util.Log.d("OllamaStreaming", "Found additional content after done flag: ${additionalContent.length} chars, total: $totalContentEmitted")
                                                    try {
                                                        send(StreamDelta(content = additionalContent, thinking = null))
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("OllamaStreaming", "Failed to send additional delta - channel closed: ${e.message}")
                                                        throw e
                                                    }
                                                }
                                                if (additionalThinking != null && additionalThinking.isNotEmpty()) {
                                                    android.util.Log.d("OllamaStreaming", "Found additional thinking after done flag: ${additionalThinking.length} chars")
                                                    try {
                                                        send(StreamDelta(content = "", thinking = additionalThinking))
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("OllamaStreaming", "Failed to send additional thinking - channel closed: ${e.message}")
                                                        throw e
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // Not JSON, ignore
                                            }
                                        }
                                        if (additionalLinesRead > 0) {
                                            android.util.Log.d("OllamaStreaming", "Read $additionalLinesRead additional lines after done flag")
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Skip malformed JSON lines, but log for debugging
                                    android.util.Log.w("OllamaStreaming", "Failed to parse line: ${line.take(100)}", e)
                                }
                            }
                            
                            android.util.Log.d("OllamaStreaming", "Finished reading stream, total lines: $lineCount, done: $isDone, total content emitted: $totalContentEmitted chars")
                            
                            // Small delay to ensure all send calls are processed
                            delay(50)
                        }
                        // Close the channel normally - this signals completion to the collector
                        android.util.Log.d("OllamaStreaming", "Closing channel normally after reading all data")
                        close()
                    } catch (e: Exception) {
                        android.util.Log.e("OllamaStreaming", "Error reading stream", e)
                        close(e)
                    }
                } ?: run {
                    android.util.Log.e("OllamaStreaming", "Response body is null")
                    close(IOException("Response body is null"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OllamaStreaming", "Error executing request", e)
            close(e)
        }
        
        awaitClose {
            android.util.Log.d("OllamaStreaming", "Channel closing, cancelling call")
            call.cancel()
        }
    }
}

