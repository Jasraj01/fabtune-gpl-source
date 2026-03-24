package com.metrolist.music.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AdViewModel : ViewModel() {

    // ---------------------------------------------------------------------------
    // Ad loaded state — true only while a real ad is currently displayed
    // ---------------------------------------------------------------------------
    var adFullyLoaded by mutableStateOf(false)
        private set

    fun setAdLoaded(value: Boolean) {
        adFullyLoaded = value
    }

    // ---------------------------------------------------------------------------
    // Which mediaId the currently-loaded ad belongs to.
    // Used by ThumbnailItem to hide/show artwork correctly.
    // ---------------------------------------------------------------------------
    var loadedAdMediaId by mutableStateOf<String?>(null)
        private set

    fun setLoadedAdFor(mediaId: String?) {
        loadedAdMediaId = mediaId
    }

    // ---------------------------------------------------------------------------
    // Cooldown tracking — only lastClosedTime is needed now.
    // We removed lastAdRequestTime: it was causing double-counting of requests
    // (the cooldown was being reset both when an ad was REQUESTED and when it
    // was CLOSED, which made the effective cooldown much shorter than intended).
    // Now the cooldown only resets when the user actively closes the ad, which
    // is the correct behaviour and reduces total request count significantly.
    // ---------------------------------------------------------------------------
    private var lastClosedTime: Long? = null

    fun setAdClosedTime(time: Long) {
        lastClosedTime = time
    }

    fun getAdClosedTime(): Long? = lastClosedTime

    // ---------------------------------------------------------------------------
    // Reload tick — incremented once per ad slot to give AdViewHolder a stable
    // key. We keep this but it is now only incremented from ONE place
    // (BannerAdWithTimer.onAdClosed path) so it no longer causes runaway
    // AdView recreation on every recomposition.
    // ---------------------------------------------------------------------------
    private val _adReloadTick = mutableStateOf(0)
    val adReloadTick: Int get() = _adReloadTick.value

    fun triggerAdReload() {
        _adReloadTick.value++
    }

    // ---------------------------------------------------------------------------
    // FIX: isAdVisible was previously unused dead state. Removed to avoid
    // confusion. If you need to track visibility externally, derive it from
    // adFullyLoaded instead.
    // ---------------------------------------------------------------------------
}