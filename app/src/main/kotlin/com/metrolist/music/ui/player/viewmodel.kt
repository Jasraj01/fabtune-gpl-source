package com.metrolist.music.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// inside AdViewModel.kt (example)
class AdViewModel : ViewModel() {
    // inside AdViewModel.kt (add these)
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
}

