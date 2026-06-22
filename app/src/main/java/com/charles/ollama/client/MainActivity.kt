package com.charles.ollama.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.charles.ollama.client.ads.AdConsentManager
import com.charles.ollama.client.ads.AdGate
import com.charles.ollama.client.ads.InterstitialAdManager
import com.charles.ollama.client.ads.RewardedAdManager
import com.charles.ollama.client.data.billing.PremiumManager
import com.google.android.gms.ads.MobileAds
import com.charles.ollama.client.data.preferences.UiPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.charles.ollama.client.ui.navigation.NavGraph
import com.charles.ollama.client.ui.theme.OllamaAndroidTheme
import com.charles.ollama.client.ui.update.UpdateAvailablePrompt
import com.charles.ollama.client.util.AppShortcuts
import com.charles.ollama.client.util.RecentThreadShortcut
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import android.os.Bundle as AndroidBundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var interstitialAdManager: InterstitialAdManager

    @Inject
    lateinit var rewardedAdManager: RewardedAdManager

    @Inject
    lateinit var uiPreferences: UiPreferences

    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var adGate: AdGate

    @Inject
    lateinit var adConsentManager: AdConsentManager

    // Drives the consent wall: true once the user has been shown a consent form
    // and declined. Compose observes this to swap the app for the wall screen.
    private val consentBlocked = androidx.compose.runtime.mutableStateOf(false)

    private val mainHandler = Handler(Looper.getMainLooper())

    // Guards against double-dispatch between the timeout runnable and the UMP
    // callback in requestConsent(). Whoever runs first wins; the other is a no-op.
    private var consentTimeoutFired = false

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            // Permission granted, FCM notifications will work
        } else {
            Log.w(TAG, "Notification permission denied")
            // Permission denied, notifications won't work
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request notification permission on Android 13+ (API 33+)
        requestNotificationPermission()
        
        // Initialize Firebase Analytics
        firebaseAnalytics = Firebase.analytics
        
        // Log app open event
        val bundle = AndroidBundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle)
        
        // Register launcher shortcuts (New chat / Models / Servers).
        AppShortcuts.refresh(this)

        val initialThreadId = readShortcutThreadId(intent)
        val initialDest = intent?.getStringExtra(EXTRA_DEST)

        // OEM PhoneWindow bug on certain Android 11 devices: PhoneWindow.generateLayout
        // throws RuntimeException("Window couldn't find content container view") when the
        // OEM's decor layout lacks android.R.id.content. Catch it, record as non-fatal, and
        // recreate the activity once — the transient window state clears on restart.
        try { setContent {
            val themeMode by uiPreferences.themeMode.collectAsState()
            val dynamicColor by uiPreferences.dynamicColor.collectAsState()
            OllamaAndroidTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                // Use mutableState so we can re-route when a new shortcut intent
                // arrives via onNewIntent (singleTask launchMode).
                var pendingThreadId by remember { mutableStateOf(initialThreadId) }
                var pendingDest by remember { mutableStateOf(initialDest) }
                pendingShortcutHandler = { newId ->
                    if (newId > 0L) pendingThreadId = newId
                }
                pendingDestHandler = { dest -> pendingDest = dest }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (consentBlocked.value) {
                        // User declined required consent — block the app and let
                        // them retry. They cannot proceed until they accept.
                        ConsentRequiredScreen(onReview = { requestConsentRetry() })
                    } else {
                        NavGraph(
                            initialThreadId = pendingThreadId,
                            initialDest = pendingDest,
                            onDestConsumed = { pendingDest = null }
                        )
                        UpdateAvailablePrompt()
                    }
                }
            }
        }         } catch (e: RuntimeException) {
            if (!contentSetupRetried && e.message?.contains("content container view") == true) {
                contentSetupRetried = true
                runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
                recreate()
                return
            }
            throw e
        }

        // Obtain GDPR/UMP consent. Deferred to the next main-thread frame so
        // Compose finishes laying out the initial view hierarchy before UMP does
        // any work (UMP's WebView init on first install can block the main thread
        // and prevent the ComposeView from being rendered). A timeout is applied
        // so the app never hangs on consent.
        mainHandler.post { requestConsent() }
    }

    /** Run the consent flow on launch. Declined → raise the consent wall. */
    private fun requestConsent() {
        // Timeout: if UMP doesn't resolve consent within CONSENT_TIMEOUT_MS,
        // proceed without waiting (fail-open). Prevents the black-screen-first-
        // install problem caused by UMP's WebView init blocking the main thread.
        mainHandler.postDelayed({
            if (!consentTimeoutFired) {
                consentTimeoutFired = true
                Log.w(TAG, "Consent timed out after ${CONSENT_TIMEOUT_MS}ms — proceeding")
                enableAds()
            }
        }, CONSENT_TIMEOUT_MS)

        adConsentManager.ensureConsent(this) { allowed ->
            if (consentTimeoutFired) return@ensureConsent
            consentTimeoutFired = true
            if (allowed) {
                consentBlocked.value = false
                enableAds()
            } else {
                consentBlocked.value = true
            }
        }
    }

    /** Re-prompt from the consent wall; on acceptance, drop the wall + enable ads. */
    private fun requestConsentRetry() {
        adConsentManager.rePrompt(this) { allowed ->
            if (allowed) {
                consentBlocked.value = false
                enableAds()
            }
            // else: stay on the wall; the user can try again.
        }
    }

    private var adsInitialized = false
    private fun enableAds() {
        adGate.setAdsConsentGranted(true)
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(this) {}
        interstitialAdManager.loadAd(this)
        rewardedAdManager.loadAd(this)
    }

    override fun onResume() {
        super.onResume()
        // Reconcile premium status with Play each time we return to the foreground.
        premiumManager.refreshPurchases()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newThreadId = readShortcutThreadId(intent)
        if (newThreadId > 0L) {
            pendingShortcutHandler?.invoke(newThreadId)
        }
        intent.getStringExtra(EXTRA_DEST)?.let { pendingDestHandler?.invoke(it) }
    }

    private fun readShortcutThreadId(intent: Intent?): Long {
        val id = intent?.getLongExtra(RecentThreadShortcut.EXTRA_THREAD_ID, -1L) ?: -1L
        return if (id > 0L) id else -1L
    }

    private var pendingShortcutHandler: ((Long) -> Unit)? = null
    private var pendingDestHandler: ((String) -> Unit)? = null
    
    private fun requestNotificationPermission() {
        // POST_NOTIFICATIONS permission is required on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    Log.d(TAG, "Notification permission already granted")
                }
                else -> {
                    // Request the permission
                    Log.d(TAG, "Requesting notification permission")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_DEST = "nav_dest"
        const val DEST_NEW_CHAT = "new_chat"
        const val DEST_MODELS = "models"
        const val DEST_SERVERS = "servers"

        // Guards against infinite recreate loop on persistent OEM window bug.
        private var contentSetupRetried = false

        /** Max time to wait for UMP to resolve consent before proceeding anyway. */
        private const val CONSENT_TIMEOUT_MS = 2_000L
    }
}

/**
 * Full-screen block shown when the user declines the required privacy/ads
 * consent. They cannot use the app until they accept; [onReview] re-opens the
 * consent form.
 */
@androidx.compose.runtime.Composable
private fun ConsentRequiredScreen(onReview: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            text = "Consent required",
            style = MaterialTheme.typography.headlineSmall,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Text(
            text = "This app is supported by ads and needs your consent to continue. " +
                "Please review and accept your privacy choices to use the app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onReview) {
            androidx.compose.material3.Text("Review privacy choices")
        }
    }
}

