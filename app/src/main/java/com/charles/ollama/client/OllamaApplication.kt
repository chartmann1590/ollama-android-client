package com.charles.ollama.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.charles.ollama.client.R
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OllamaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Enable Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        
        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this)
        
        // Initialize Firebase Performance Monitoring
        val firebasePerformance = FirebasePerformance.getInstance()
        firebasePerformance.isPerformanceCollectionEnabled = true
        Log.d(TAG, "Firebase Performance Monitoring initialized")
        
        // Initialize Firebase Cloud Messaging
        initializeFCM()
    }
    
    private fun initializeFCM() {
        // Create notification channel early
        createNotificationChannel()
        
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Registration Token: $token")
            
            // TODO: Send token to your server if needed
            // sendRegistrationToServer(token)
        }
        
        // Subscribe to default topic (optional)
        FirebaseMessaging.getInstance().subscribeToTopic("ollama_updates")
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) {
                    "Subscribed to ollama_updates topic"
                } else {
                    "Failed to subscribe to ollama_updates topic"
                }
                Log.d(TAG, msg)
            }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.default_notification_channel_id)
            val channelName = "Ollama Notifications"
            val channelDescription = "Notifications for Ollama messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $channelId")
        }
    }
    
    companion object {
        private const val TAG = "OllamaApplication"
    }
}

