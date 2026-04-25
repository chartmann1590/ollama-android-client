package com.charles.ollama.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.charles.ollama.client.R
import com.charles.ollama.client.ads.AppOpenAdManager
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OllamaApplication : Application() {

    private val appOpenAdManager = AppOpenAdManager()

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        installMainLoopExceptionFilter()

        appOpenAdManager.register(this)
        
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
    
    // Swallow a known framework race in ViewRootImpl.scrollToRectOrFocus where
    // the focused view is detached between focus search and the scroll, throwing
    // IllegalArgumentException("parameter must be a descendant of this view")
    // from ViewGroup.offsetRectBetweenParentAndChild. Report as non-fatal so we
    // still see it in Crashlytics, but don't take down the process for a single
    // dropped frame.
    private fun installMainLoopExceptionFilter() {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                while (true) {
                    try {
                        Looper.loop()
                    } catch (t: Throwable) {
                        if (isSuppressibleFrameworkBug(t)) {
                            runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
                            continue
                        }
                        Thread.getDefaultUncaughtExceptionHandler()
                            ?.uncaughtException(Thread.currentThread(), t)
                        return
                    }
                }
            }
        })
    }

    companion object {
        private const val TAG = "OllamaApplication"
    }
}

internal fun isSuppressibleFrameworkBug(t: Throwable): Boolean {
    if (t !is IllegalArgumentException) return false
    if (t.message?.contains("descendant of this view") != true) return false
    return t.stackTrace.any { f ->
        f.className == "android.view.ViewGroup" &&
            f.methodName == "offsetRectBetweenParentAndChild"
    }
}

