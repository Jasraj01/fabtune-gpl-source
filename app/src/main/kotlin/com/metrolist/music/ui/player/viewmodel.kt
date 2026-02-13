package com.metrolist.music.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AdViewModel : ViewModel() {
    var isAdVisible by mutableStateOf(false)
        private set

    fun setVisible(value: Boolean) {
        isAdVisible = value
    }

    var adFullyLoaded by mutableStateOf(false)
        private set

    fun setAdLoaded(value: Boolean) {
        adFullyLoaded = value
    }

    var loadedAdMediaId by mutableStateOf<String?>(null)
        private set

    fun setLoadedAdFor(mediaId: String?) {
        loadedAdMediaId = mediaId
    }

    private var lastClosedTime: Long? = null

    fun setAdClosedTime(time: Long) {
        lastClosedTime = time
    }

    fun getAdClosedTime(): Long? = lastClosedTime
// added
    private val _adReloadTick = mutableStateOf(0)
    val adReloadTick: Int get() = _adReloadTick.value

    fun triggerAdReload() {
        _adReloadTick.value++
    }

}