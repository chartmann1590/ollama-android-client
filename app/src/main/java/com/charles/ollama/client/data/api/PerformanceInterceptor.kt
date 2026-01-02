package com.charles.ollama.client.data.api

import com.charles.ollama.client.util.PerformanceMonitor
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that automatically traces HTTP requests using Firebase Performance
 */
class PerformanceInterceptor : Interceptor {
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        
        // Extract endpoint name from URL (e.g., /api/chat -> chat)
        val endpoint = extractEndpoint(url)
        val trace = PerformanceMonitor.startNetworkTrace(endpoint)
        
        try {
            // Add request attributes
            PerformanceMonitor.addAttribute(trace, "url", url.take(100))
            PerformanceMonitor.addAttribute(trace, "method", request.method)
            
            val requestBody = request.body
            if (requestBody != null) {
                PerformanceMonitor.addAttribute(trace, "has_body", "true")
                val contentLength = requestBody.contentLength()
                if (contentLength > 0) {
                    PerformanceMonitor.addMetric(trace, "request_size_bytes", contentLength)
                }
            }
            
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            
            // Add response attributes
            PerformanceMonitor.addAttribute(trace, "status_code", response.code.toString())
            PerformanceMonitor.addMetric(trace, "response_time_ms", duration)
            
            val responseBody = response.body
            if (responseBody != null) {
                val contentLength = responseBody.contentLength()
                if (contentLength > 0) {
                    PerformanceMonitor.addMetric(trace, "response_size_bytes", contentLength)
                }
            }
            
            return response
        } catch (e: IOException) {
            PerformanceMonitor.addAttribute(trace, "error", e.javaClass.simpleName)
            throw e
        } finally {
            PerformanceMonitor.stopTrace(trace)
        }
    }
    
    private fun extractEndpoint(url: String): String {
        return try {
            val path = url.substringAfterLast("/api/")
                .substringBefore("?")
                .substringBefore("#")
            if (path.isBlank()) "unknown" else path
        } catch (e: Exception) {
            "unknown"
        }
    }
}

