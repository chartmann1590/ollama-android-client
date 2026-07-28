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
import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.ads.AppOpenAdManager
import com.charles.ollama.client.data.billing.PremiumManager
import com.charles.ollama.client.data.sync.ChatSyncCoordinator
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OllamaApplication : Application() {

    @javax.inject.Inject
    lateinit var adGate: AdGate

    @javax.inject.Inject
    lateinit var premiumManager: PremiumManager

    @javax.inject.Inject
    lateinit var chatSyncCoordinator: ChatSyncCoordinator

    private lateinit var appOpenAdManager: AppOpenAdManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w(TAG, "Realtime Database persistence was already configured", e)
        }

        // Enable Firebase Crashlytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        installMainLoopExceptionFilter()

        // Start Play Billing early so premium status is known before ads load.
        premiumManager.initialize()

        appOpenAdManager = AppOpenAdManager(adGate)
        appOpenAdManager.register(this)
        
        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this)
        
        // Initialize Firebase Performance Monitoring
        val firebasePerformance = FirebasePerformance.getInstance()
        firebasePerformance.isPerformanceCollectionEnabled = true
        Log.d(TAG, "Firebase Performance Monitoring initialized")
        
        // Initialize Firebase Cloud Messaging
        initializeFCM()

        chatSyncCoordinator.start()
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
            Log.d(TAG, "FCM Registration Token: [REDACTED]")
            
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
    // Firebase RTDB throws DatabaseException on the main thread when a write is denied
    // (the default no-listener completion handler in the Java SDK calls error.toException()).
    // Treat these as non-fatal rather than killing the process.
    if (t is com.google.firebase.database.DatabaseException) return true

    // NullPointerExceptions surfacing from Looper.loop() whose entire stack trace is
    // inside platform/library packages (nothing from this app's own code) can't be a
    // bug in our code - there's no app frame between the try block and Looper.loop()
    // for the NPE to originate from. This mirrors the two named-bug checks below, but
    // generalized: rather than matching one specific documented framework bug by class/
    // method name, it verifies app-code involvement is impossible before suppressing,
    // so it can't mask a real app-code NPE (Crashlytics issue #38 - the reported crash's
    // full stack trace wasn't retrievable via the Crashlytics REST API, which only
    // exposes issue summaries, not per-event traces; this is the safe fallback).
    if (t is NullPointerException && t.stackTrace.isNotEmpty()) {
        val touchesAppCode = t.stackTrace.any { it.className.startsWith("com.charles.ollama.client") }
        if (!touchesAppCode) return true
    }

    if (t !is IllegalArgumentException) return false
    val msg = t.message ?: return false

    // ViewGroup.offsetRectBetweenParentAndChild race: focused view detaches between
    // focus search and scroll in ViewRootImpl.scrollToRectOrFocus.
    if (msg.contains("descendant of this view")) {
        return t.stackTrace.any { f ->
            f.className == "android.view.ViewGroup" &&
                f.methodName == "offsetRectBetweenParentAndChild"
        }
    }

    // WindowManagerGlobal race on Android 16: the DecorView is removed from the window
    // manager between a resume transaction and the subsequent updateViewLayout call.
    if (msg.contains("not attached to window manager")) {
        return t.stackTrace.any { f ->
            f.className == "android.view.WindowManagerGlobal" &&
                f.methodName == "findViewLocked"
        }
    }

    return false
}
