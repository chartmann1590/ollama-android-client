package com.charles.ollama.client.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.util.PerformanceMonitor
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
) {
    if (!BuildConfig.ADS_ENABLED) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val adView: AdView = remember {
        val view = AdView(context)
        view.setAdSize(AdSize.BANNER)
        view.adUnitId = adUnitId
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        view
    }
    
    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Load ad when composable is first created
        val trace = PerformanceMonitor.startAdTrace("load_banner")
        val adRequest = AdRequest.Builder().build()
        adView.setAdListener(object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                PerformanceMonitor.addAttribute(trace, "status", "loaded")
                PerformanceMonitor.stopTrace(trace)
            }
            
            override fun onAdFailedToLoad(error: LoadAdError) {
                PerformanceMonitor.addAttribute(trace, "status", "failed")
                PerformanceMonitor.addAttribute(trace, "error_code", error.code.toString())
                PerformanceMonitor.stopTrace(trace)
            }
        })
        adView.loadAd(adRequest)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp) // Standard banner height
        )
    }
}

