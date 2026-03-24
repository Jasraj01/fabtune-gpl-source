package com.metrolist.music.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import timber.log.Timber

private const val AD_DISPLAY_DURATION_SECONDS = 5
private const val AD_COOLDOWN_MS = 127_000L // 100 seconds (1 minute 40 seconds)

// ---------------------------------------------------------------------------
// PUBLIC COMPOSABLE
// ---------------------------------------------------------------------------

@Composable
fun BannerAdWithTimer(
    adUnitId: String,
    adViewModel: AdViewModel,
    mediaId: String,
    onAdClosed: () -> Unit,
    onAdOpened: () -> Unit = {},
    useTimer: Boolean = true,
    isSubscribed: Boolean = false
) {
    if (isSubscribed) return

    // ---------------------------------------------------------------------------
    // Track if we should show ad (based on cooldown only)
    // ---------------------------------------------------------------------------
    var cooldownComplete by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, adViewModel.getAdClosedTime()) {

        val lastClosed = adViewModel.getAdClosedTime()

        if (lastClosed == null) {
            cooldownComplete = true
            return@LaunchedEffect
        }

        val elapsed = System.currentTimeMillis() - lastClosed
        val remaining = AD_COOLDOWN_MS - elapsed

        if (remaining <= 0) {
            cooldownComplete = true
        } else {
            cooldownComplete = false
            delay(remaining)
            cooldownComplete = true
        }
    }


    // Show AdViewHolder when cooldown is complete
    // It will stay visible until user explicitly closes it
    if (cooldownComplete) {
        key(adViewModel.adReloadTick, mediaId) {
            Timber.d("Rendering AdViewHolder for mediaId=$mediaId")
            AdViewHolder(
                adUnitId = adUnitId,
                adViewModel = adViewModel,
                mediaId = mediaId,
                useTimer = useTimer,
                onAdClosed = {
                    Timber.d("User closed ad - setting cooldown")
                    // Set cooldown timestamp - this will make cooldownComplete false
                    adViewModel.setAdClosedTime(System.currentTimeMillis())
                    // Clear ad state
                    adViewModel.setAdLoaded(false)
                    adViewModel.setLoadedAdFor(null)
                    // Increment reload tick for next ad
                    adViewModel.triggerAdReload()
                    // Call parent callback
                    onAdClosed()
                },
                onAdOpened = onAdOpened
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PRIVATE: AdView host
// ---------------------------------------------------------------------------

@Composable
private fun AdViewHolder(
    adUnitId: String,
    adViewModel: AdViewModel,
    mediaId: String,
    useTimer: Boolean,
    onAdClosed: () -> Unit,
    onAdOpened: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    Timber.d("AdViewHolder: Creating for mediaId=$mediaId")

    val adView = remember {
        Timber.d("Creating new AdView instance")
        AdView(context).apply {
            setAdSize(AdSize.MEDIUM_RECTANGLE)
            this.adUnitId = adUnitId
        }
    }

    var adLoaded by remember { mutableStateOf(false) }
    var adRendered by remember { mutableStateOf(false) }
    var timerValue by remember { mutableIntStateOf(AD_DISPLAY_DURATION_SECONDS) }
    var isTimerFinished by remember { mutableStateOf(false) }
    var hasReportedClose by remember { mutableStateOf(false) }

    val closeAd: () -> Unit = {
        if (!hasReportedClose) {
            Timber.d("Closing ad")
            hasReportedClose = true
            onAdClosed()
        }
    }

    // Load ad once
    LaunchedEffect(Unit) {
        Timber.d("Loading ad request for mediaId=$mediaId")

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Timber.d("✅ Ad loaded successfully")
                adLoaded = true
                onAdOpened()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Timber.e("❌ Ad failed to load: ${error.message} (code ${error.code})")
                closeAd()
            }

            override fun onAdClicked() {
                Timber.d("Ad clicked")
            }
        }

        adView.loadAd(AdRequest.Builder().build())
    }

    // Update ViewModel when ad is rendered
    LaunchedEffect(adRendered) {
        if (adRendered) {
            Timber.d("✅ Ad rendered - updating ViewModel")
            adViewModel.setAdLoaded(true)
            adViewModel.setLoadedAdFor(mediaId)
        }
    }

    // Timer countdown
    LaunchedEffect(adLoaded, useTimer) {
        if (!adLoaded) return@LaunchedEffect
        Timber.d("Starting timer")
        timerValue = AD_DISPLAY_DURATION_SECONDS
        isTimerFinished = false
        if (useTimer) {
            repeat(AD_DISPLAY_DURATION_SECONDS) {
                delay(1_000L)
                timerValue--
            }
        }
        isTimerFinished = true
        Timber.d("Timer finished")
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME  -> adView.resume()
                Lifecycle.Event.ON_PAUSE   -> adView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Timber.d("Disposing AdViewHolder")
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.wrapContentHeight()
    ) {
        Box {
            AndroidView(
                factory = { adView },
                update = {
                    if (adLoaded && !adRendered) {
                        Timber.d("Marking ad as rendered")
                        adRendered = true
                    }
                }
            )

            // Fade in the label
            val labelAlpha by animateFloatAsState(
                targetValue = if (adRendered) 1f else 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "labelAlpha"
            )

            Text(
                text = "Advertisement",
                fontSize = 10.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { alpha = labelAlpha }
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Fade in controls
        val controlsAlpha by animateFloatAsState(
            targetValue = if (adRendered) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "controlsAlpha"
        )

        Box(modifier = Modifier.graphicsLayer { alpha = controlsAlpha }) {
            AdControls(
                isTimerFinished = isTimerFinished,
                timerValue = timerValue,
                onCloseClicked = closeAd
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PRIVATE: close / timer button
// ---------------------------------------------------------------------------

@Composable
private fun AdControls(
    isTimerFinished: Boolean,
    timerValue: Int,
    onCloseClicked: () -> Unit
) {
    Box(modifier = Modifier.padding(top = 8.dp)) {
        val baseModifier = Modifier
            .background(Color(0xFF424242), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)

        Text(
            text = if (isTimerFinished) "Close Ad" else "Ad closes in ${timerValue}s",
            fontSize = 12.sp,
            color = Color.White,
            modifier = if (isTimerFinished) baseModifier.clickable { onCloseClicked() } else baseModifier
        )
    }
}