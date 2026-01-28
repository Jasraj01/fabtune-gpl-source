package com.metrolist.music.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

class SafePlayer(player: Player) : ForwardingPlayer(player) {
    override fun getBufferedPercentage(): Int {
        return try {
            super.getBufferedPercentage()
        } catch (e: IllegalArgumentException) {
            // Catch exception from Util.percentInt overflow
            0
        }
    }
}
