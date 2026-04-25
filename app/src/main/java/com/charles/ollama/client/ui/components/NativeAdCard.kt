package com.charles.ollama.client.ui.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.charles.ollama.client.BuildConfig
import com.charles.ollama.client.R
import com.charles.ollama.client.util.PerformanceMonitor
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID
) {
    if (!BuildConfig.ADS_ENABLED) return
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(adUnitId) {
        val trace = PerformanceMonitor.startAdTrace("load_native")
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                PerformanceMonitor.addAttribute(trace, "status", "loaded")
                PerformanceMonitor.stopTrace(trace)
                nativeAd?.destroy()
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    PerformanceMonitor.addAttribute(trace, "status", "failed")
                    PerformanceMonitor.addAttribute(trace, "error_code", error.code.toString())
                    PerformanceMonitor.stopTrace(trace)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad_card, null) as NativeAdView
            },
            update = { adView -> bindNativeAd(adView, ad) }
        )
    }
}

private fun bindNativeAd(adView: NativeAdView, ad: NativeAd) {
    val headline = adView.findViewById<TextView>(R.id.native_ad_headline)
    headline.text = ad.headline
    adView.headlineView = headline

    val body = adView.findViewById<TextView>(R.id.native_ad_body)
    body.text = ad.body
    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    adView.bodyView = body

    val cta = adView.findViewById<Button>(R.id.native_ad_cta)
    cta.text = ad.callToAction
    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    adView.callToActionView = cta

    val icon = adView.findViewById<ImageView>(R.id.native_ad_icon)
    val iconAsset = ad.icon
    if (iconAsset?.drawable != null) {
        icon.setImageDrawable(iconAsset.drawable)
        icon.visibility = View.VISIBLE
    } else {
        icon.visibility = View.GONE
    }
    adView.iconView = icon

    val advertiser = adView.findViewById<TextView>(R.id.native_ad_advertiser)
    if (ad.advertiser.isNullOrBlank()) {
        advertiser.visibility = View.GONE
    } else {
        advertiser.text = ad.advertiser
        advertiser.visibility = View.VISIBLE
    }
    adView.advertiserView = advertiser

    val media = adView.findViewById<MediaView>(R.id.native_ad_media)
    adView.mediaView = media

    adView.setNativeAd(ad)
}
