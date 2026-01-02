package com.charles.ollama.client.util

import android.util.Log
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility class for Firebase Performance Monitoring
 * Provides easy-to-use methods for tracking performance metrics
 */
object PerformanceMonitor {
    
    private const val TAG = "PerformanceMonitor"
    private val firebasePerformance = FirebasePerformance.getInstance()
    
    /**
     * Creates and starts a custom trace
     * @param traceName Name of the trace (max 100 characters)
     * @return Trace object that must be stopped manually
     */
    fun startTrace(traceName: String): Trace {
        val trace = firebasePerformance.newTrace(traceName)
        trace.start()
        Log.d(TAG, "Started trace: $traceName")
        return trace
    }
    
    /**
     * Executes a block of code and measures its performance
     * @param traceName Name of the trace
     * @param block Code block to measure
     * @return Result of the block execution
     */
    inline fun <T> measure(traceName: String, block: () -> T): T {
        val trace = startTrace(traceName)
        return try {
            block()
        } finally {
            trace.stop()
            Log.d("PerformanceMonitor", "Stopped trace: $traceName")
        }
    }
    
    /**
     * Executes a suspend block of code and measures its performance
     * @param traceName Name of the trace
     * @param block Suspend code block to measure
     * @return Result of the block execution
     */
    suspend inline fun <T> measureSuspend(traceName: String, crossinline block: suspend () -> T): T {
        val trace = startTrace(traceName)
        return try {
            block()
        } finally {
            trace.stop()
            Log.d("PerformanceMonitor", "Stopped trace: $traceName")
        }
    }
    
    /**
     * Adds a custom attribute to a trace
     * @param trace Trace to add attribute to
     * @param attributeName Name of the attribute (max 40 characters)
     * @param attributeValue Value of the attribute (max 100 characters)
     */
    fun addAttribute(trace: Trace, attributeName: String, attributeValue: String) {
        try {
            trace.putAttribute(attributeName, attributeValue)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add attribute $attributeName: ${e.message}")
        }
    }
    
    /**
     * Adds a custom metric to a trace
     * @param trace Trace to add metric to
     * @param metricName Name of the metric
     * @param value Value of the metric
     */
    fun addMetric(trace: Trace, metricName: String, value: Long) {
        try {
            trace.incrementMetric(metricName, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add metric $metricName: ${e.message}")
        }
    }
    
    /**
     * Increments a metric by 1
     * @param trace Trace to increment metric on
     * @param metricName Name of the metric
     */
    fun incrementMetric(trace: Trace, metricName: String) {
        addMetric(trace, metricName, 1)
    }
    
    /**
     * Creates a trace for screen rendering
     * @param screenName Name of the screen
     * @return Trace object
     */
    fun startScreenTrace(screenName: String): Trace {
        return startTrace("screen_$screenName")
    }
    
    /**
     * Creates a trace for network requests
     * @param endpoint API endpoint name
     * @return Trace object
     */
    fun startNetworkTrace(endpoint: String): Trace {
        return startTrace("network_$endpoint")
    }
    
    /**
     * Creates a trace for database operations
     * @param operation Database operation name
     * @return Trace object
     */
    fun startDatabaseTrace(operation: String): Trace {
        return startTrace("database_$operation")
    }
    
    /**
     * Creates a trace for ViewModel operations
     * @param operation ViewModel operation name
     * @return Trace object
     */
    fun startViewModelTrace(operation: String): Trace {
        return startTrace("viewmodel_$operation")
    }
    
    /**
     * Creates a trace for ad operations
     * @param operation Ad operation name
     * @return Trace object
     */
    fun startAdTrace(operation: String): Trace {
        return startTrace("ad_$operation")
    }
    
    /**
     * Creates a trace for image processing
     * @param operation Image operation name
     * @return Trace object
     */
    fun startImageTrace(operation: String): Trace {
        return startTrace("image_$operation")
    }
    
    /**
     * Helper to safely stop a trace
     */
    fun stopTrace(trace: Trace?) {
        trace?.let {
            try {
                it.stop()
                Log.d(TAG, "Stopped trace: ${it.name}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop trace: ${e.message}")
            }
        }
    }
}

