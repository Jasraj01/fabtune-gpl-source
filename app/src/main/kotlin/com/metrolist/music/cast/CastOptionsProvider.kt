package com.metrolist.music.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * CastOptionsProvider for Google Cast integration.
 * This class provides the Cast options for the app.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        cachedCastOptions?.let { return it }

        synchronized(castOptionsLock) {
            cachedCastOptions?.let { return it }

            val notificationOptions = NotificationOptions.Builder()
                .setActions(castNotificationActions, compactActionIndices)
                .build()

            val mediaOptions = CastMediaOptions.Builder()
                .setNotificationOptions(notificationOptions)
                .build()

            return CastOptions.Builder()
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .setCastMediaOptions(mediaOptions)
                .setStopReceiverApplicationWhenEndingSession(true)
                .build()
                .also { cachedCastOptions = it }
        }
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }

    private companion object {
        private val castOptionsLock = Any()

        @Volatile
        private var cachedCastOptions: CastOptions? = null

        private val castNotificationActions = listOf(
            MediaIntentReceiver.ACTION_SKIP_PREV,
            MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
            MediaIntentReceiver.ACTION_SKIP_NEXT,
            MediaIntentReceiver.ACTION_STOP_CASTING
        )

        private val compactActionIndices = intArrayOf(1, 2)
    }
}
