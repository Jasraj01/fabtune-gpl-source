//package com.metrolist.music.ui.player
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.wrapContentHeight
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalLifecycleOwner
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.LifecycleEventObserver
//import androidx.lifecycle.compose.LocalLifecycleOwner
//import com.google.android.gms.ads.AdListener
//import com.google.android.gms.ads.AdRequest
//import com.google.android.gms.ads.AdSize
//import com.google.android.gms.ads.AdView
//import com.google.android.gms.ads.LoadAdError
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import timber.log.Timber
//
//private const val AD_DISPLAY_DURATION_SECONDS = 7
//private const val AD_COOLDOWN_MS = 60_000L // 1 minute
//
//@Composable
//fun BannerAdWithTimer(
//    adUnitId: String,
//    adViewModel: AdViewModel,
//    mediaId: String,
//    onAdClosed: () -> Unit,
//    onAdOpened: () -> Unit = {}, // notify parent when ad becomes visible
//    useTimer: Boolean = true
//) {
//    var adVisible by remember { mutableStateOf(true) }
//
//    val canShowAd = remember(adViewModel) {
//        val lastClosed = adViewModel.getAdClosedTime()
//        val currentTime = System.currentTimeMillis()
//        lastClosed == null || (currentTime - lastClosed) >= AD_COOLDOWN_MS
//    }
//
//    if (canShowAd && adVisible) {
//        AdViewHolder(
//            adUnitId = adUnitId,
//            adViewModel = adViewModel,
//            mediaId = mediaId,
//            useTimer = useTimer,
//            onAdClosed = {
//                adVisible = false
//                adViewModel.setAdLoaded(false)
//                onAdClosed()
//            },
//            onAdOpened = { onAdOpened() }
//        )
//    }
//}
//
//@Composable
//private fun AdViewHolder(
//    adUnitId: String,
//    adViewModel: AdViewModel,
//    mediaId: String,
//    useTimer: Boolean,
//    onAdClosed: () -> Unit,
//    onAdOpened: () -> Unit
//) {
//    val context = LocalContext.current
//    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
//    val coroutineScope = rememberCoroutineScope()
//
//    // Remember a single AdView instance across recompositions
//    val adView = remember {
//        AdView(context).apply {
//            setAdSize(AdSize.MEDIUM_RECTANGLE)
//            this.adUnitId = adUnitId
//        }
//    }
//
//    var adLoaded by remember { mutableStateOf(false) }
//    var timerValue by remember { mutableIntStateOf(AD_DISPLAY_DURATION_SECONDS) }
//    var isTimerFinished by remember { mutableStateOf(false) }
//
//    // Load the ad only once
//    LaunchedEffect(Unit) {
//        adView.adListener = object : AdListener() {
//            override fun onAdLoaded() {
//                adLoaded = true
//                adViewModel.setAdLoaded(true)
//                adViewModel.setLoadedAdFor(mediaId)
//                onAdOpened()
//            }
//
//            override fun onAdFailedToLoad(error: LoadAdError) {
//                Timber.e("Ad failed to load: $error")
//                onAdClosed()
//            }
//        }
//        adView.loadAd(AdRequest.Builder().build())
//    }
//
//    // Start timer only after ad loads, not on every recomposition
//    LaunchedEffect(adLoaded) {
//        if (adLoaded) {
//            if (useTimer) {
//                coroutineScope.launch {
//                    repeat(AD_DISPLAY_DURATION_SECONDS) {
//                        delay(1000L)
//                        timerValue--
//                    }
//                    isTimerFinished = true
//                }
//            } else {
//                isTimerFinished = true
//            }
//        }
//    }
//
//    // Lifecycle management for AdView
//    DisposableEffect(lifecycleOwner, adView) {
//        val observer = LifecycleEventObserver { _, event ->
//            when (event) {
//                Lifecycle.Event.ON_RESUME -> adView.resume()
//                Lifecycle.Event.ON_PAUSE -> adView.pause()
//                Lifecycle.Event.ON_DESTROY -> adView.destroy()
//                else -> {}
//            }
//        }
//        lifecycleOwner.lifecycle.addObserver(observer)
//        onDispose {
//            lifecycleOwner.lifecycle.removeObserver(observer)
//            adView.destroy()
//        }
//    }
//
//    Column(
//        horizontalAlignment = Alignment.CenterHorizontally,
//        modifier = Modifier.wrapContentHeight()
//    ) {
//        AndroidView(factory = { adView })
//
//        if (adLoaded) {
//            AdControls(
//                isTimerFinished = isTimerFinished,
//                timerValue = timerValue,
//                onCloseClicked = onAdClosed
//            )
//        }
//    }
//}
//
//@Composable
//private fun AdControls(
//    isTimerFinished: Boolean,
//    timerValue: Int,
//    onCloseClicked: () -> Unit
//) {
//    Box(
//        modifier = Modifier.padding(top = 8.dp)
//    ) {
//        val modifier = Modifier
//            .background(Color.Gray.copy(alpha = 1.0f), RoundedCornerShape(50))
//            .padding(horizontal = 10.dp, vertical = 5.dp)
//
//        Text(
//            text = if (isTimerFinished) "Close Ad" else "Ad: ${timerValue}s",
//            fontSize = 12.sp,
//            color = Color.White,
//            modifier = if (isTimerFinished) modifier.clickable { onCloseClicked() } else modifier
//        )
//    }
//}
