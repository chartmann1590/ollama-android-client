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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.charles.ollama.client.data.billing.PaymentReturnHandler
import com.charles.ollama.client.data.billing.PremiumManager
import com.google.android.gms.ads.MobileAds
import com.charles.ollama.client.data.preferences.UiPreferences
import com.charles.ollama.client.data.translation.TranslationRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.charles.ollama.client.ui.navigation.NavGraph
import com.charles.ollama.client.ui.localization.TranslationProvider
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
    lateinit var paymentReturnHandler: PaymentReturnHandler

    @Inject
    lateinit var adGate: AdGate

    @Inject
    lateinit var adConsentManager: AdConsentManager

    @Inject
    lateinit var translationRepository: TranslationRepository

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
        paymentReturnHandler.handle(intent)

        // OEM PhoneWindow bug on certain Android 11 devices: PhoneWindow.generateLayout
        // throws RuntimeException("Window couldn't find content container view") when the
        // OEM's decor layout lacks android.R.id.content. Catch it, record as non-fatal, and
        // recreate the activity once — the transient window state clears on restart.
        try { setContent {
            val themeMode by uiPreferences.themeMode.collectAsState()
            val dynamicColor by uiPreferences.dynamicColor.collectAsState()
            OllamaAndroidTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                TranslationProvider(repository = translationRepository) {
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

        // Obtain GDPR/UMP consent. Deferred to when the window's decor view is
        // fully attached to the Window Manager so the app's view hierarchy is
        // stable before UMP runs (preventing timing crashes like "Window couldn't
        // find content container view"). A timeout is applied so the app never hangs.
        window.decorView.post { requestConsent() }
    }

    /** Run the consent flow on launch. Declined consent disables ads, not the app. */
    private fun requestConsent() {
        if (isFinishing || isDestroyed) return

        // Timeout: if UMP doesn't resolve consent within CONSENT_TIMEOUT_MS,
        // proceed without waiting (fail-open). Prevents the black-screen-first-
        // install problem caused by UMP's WebView init blocking the main thread.
        mainHandler.postDelayed({
            if (!consentTimeoutFired) {
                if (isFinishing || isDestroyed) return@postDelayed
                consentTimeoutFired = true
                Log.w(TAG, "Consent timed out after ${CONSENT_TIMEOUT_MS}ms — proceeding")
                enableAds()
            }
        }, CONSENT_TIMEOUT_MS)

        adConsentManager.ensureConsent(this) { allowed ->
            if (isFinishing || isDestroyed) return@ensureConsent
            if (consentTimeoutFired) return@ensureConsent
            consentTimeoutFired = true
            if (allowed) {
                enableAds()
            } else {
                Log.i(TAG, "Ads consent not granted; continuing with ads disabled")
                adGate.setAdsConsentGranted(false)
            }
        }
    }

    private var adsInitialized = false
    private fun enableAds() {
        if (isFinishing || isDestroyed) return
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
        paymentReturnHandler.handle(intent)
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
                    // Request the permission.
                    // Android 16 throws NPE inside Parcel.createExceptionOrNull when
                    // requestPermissions is IPC'd during Activity launch; catch and
                    // record as non-fatal so the app still starts.
                    Log.d(TAG, "Requesting notification permission")
                    try {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (e: Exception) {
                        Log.w(TAG, "requestPermissions failed (Android ${Build.VERSION.SDK_INT}): ${e.message}")
                        runCatching { FirebaseCrashlytics.getInstance().recordException(e) }
                    }
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
