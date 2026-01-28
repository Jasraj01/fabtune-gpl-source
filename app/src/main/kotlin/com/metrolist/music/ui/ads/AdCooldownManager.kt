package com.metrolist.music.ui.ads

import android.content.Context
import androidx.core.content.edit

object AdCooldownManager {

    private const val PREF = "ad_cooldown"
    private const val KEY_LAST_SHOWN = "last_shown"
    private const val COOLDOWN_MS = 12 * 60 * 60 * 1000L // 12 hours

    fun canShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_SHOWN, 0L)
        return System.currentTimeMillis() - last >= COOLDOWN_MS
    }

    fun markShown(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SHOWN, System.currentTimeMillis())
        }
    }
}
