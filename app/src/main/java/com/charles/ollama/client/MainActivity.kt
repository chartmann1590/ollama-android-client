package com.charles.ollama.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.charles.ollama.client.data.billing.PremiumManager
import com.google.android.gms.ads.MobileAds
import com.charles.ollama.client.data.preferences.UiPreferences
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
        
        // Obtain GDPR/UMP consent before any ads are requested. Outside the EEA
        // this resolves immediately as "not required". Only once resolved do we
        // initialize the Ads SDK, flip the AdGate consent flag, and preload.
        adConsentManager.ensureConsent(this) {
            MobileAds.initialize(this) {}
            adGate.setAdsConsentGranted(true)
            interstitialAdManager.loadAd(this)
            rewardedAdManager.loadAd(this)
        }

        // Register launcher shortcuts (New chat / Models / Servers).
        AppShortcuts.refresh(this)

        val initialThreadId = readShortcutThreadId(intent)
        val initialDest = intent?.getStringExtra(EXTRA_DEST)

        setContent {
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
                    NavGraph(
                        initialThreadId = pendingThreadId,
                        initialDest = pendingDest,
                        onDestConsumed = { pendingDest = null }
                    )
                    UpdateAvailablePrompt()
                }
            }
        }
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
    }
}

