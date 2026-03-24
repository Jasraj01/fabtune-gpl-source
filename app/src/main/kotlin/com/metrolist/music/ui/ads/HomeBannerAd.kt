package com.metrolist.music.ui.ads

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun MediumRectangleAd(
    modifier: Modifier = Modifier,
    adUnitId: String
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Create AdView once
    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE) // 300x250
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    // Handle lifecycle properly (AdMob requirement)
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy() // IMPORTANT: Always destroy when leaving composition
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { adView }
    )
}