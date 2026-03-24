package com.metrolist.music.ui.reviewbox

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch

private const val TWO_DAYS_MILLIS = 2L * 24 * 60 * 60 * 1000
private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
private const val MIN_FOREGROUND_SESSIONS = 5

/**
 * Passive eligibility check for Google In-App Review.
 *
 * A review attempt is allowed ONLY if:
 * 1) (currentTime - FIRST_OPEN_TIMESTAMP) >= 2 days
 * 2) The user has returned to the app at least a few times
 * 3) The user has actual playback history
 * 4) (currentTime - LAST_REVIEW_ATTEMPT_TIMESTAMP) >= 30 days
 * 5) The current app version has not already launched the flow
 * 6) App is in a calm state
 */
suspend fun isInAppReviewEligible(
    reviewDataStore: ReviewDataStore,
    currentVersionCode: Int,
    currentTimeMillis: Long = System.currentTimeMillis(),
    hasPlaybackHistory: Boolean,
    isAppCalm: Boolean,
): Boolean {
    if (!isAppCalm || !hasPlaybackHistory) return false

    // Ensure we have a first-open timestamp stored.
    reviewDataStore.ensureFirstOpenTimestampInitialized(currentTimeMillis)
    val firstOpenTimestamp = reviewDataStore.getFirstOpenTimestamp()
    if (firstOpenTimestamp == 0L || currentTimeMillis - firstOpenTimestamp < TWO_DAYS_MILLIS) {
        return false
    }

    if (reviewDataStore.getForegroundSessionCount() < MIN_FOREGROUND_SESSIONS) {
        return false
    }

    val lastAttemptTimestamp = reviewDataStore.getLastReviewAttemptTimestamp()
    if (lastAttemptTimestamp != 0L &&
        currentTimeMillis - lastAttemptTimestamp < THIRTY_DAYS_MILLIS
    ) {
        return false
    }

    if (reviewDataStore.getLastReviewVersionCode() == currentVersionCode) {
        return false
    }

    return true
}

/**
 * Triggers Google Play Core In-App Review flow if eligibility conditions pass.
 *
 * - Uses Google In-App Review API ONLY.
 * - No fallback UI.
 * - Stores cooldown only after Play accepted the request and launch completed.
 * - Returns true only when a Play review request was actually started.
 */
suspend fun triggerInAppReviewIfEligible(
    activity: Activity,
    reviewDataStore: ReviewDataStore,
    currentVersionCode: Int,
    hasPlaybackHistory: Boolean,
    isAppCalm: Boolean,
): Boolean {
    val lifecycleOwner = activity as? LifecycleOwner ?: return false
    if (activity.isFinishing || activity.isDestroyed) return false
    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false

    val currentTimeMillis = System.currentTimeMillis()
    val eligible = isInAppReviewEligible(
        reviewDataStore = reviewDataStore,
        currentVersionCode = currentVersionCode,
        currentTimeMillis = currentTimeMillis,
        hasPlaybackHistory = hasPlaybackHistory,
        isAppCalm = isAppCalm,
    )

    if (!eligible) return false

    val reviewManager = ReviewManagerFactory.create(activity)
    val request = reviewManager.requestReviewFlow()

    // Asynchronous, non-blocking request; if successful, launch the flow.
    request.addOnCompleteListener(activity) { task ->
        if (task.isSuccessful) {
            if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@addOnCompleteListener
            val reviewInfo = task.result
            reviewManager.launchReviewFlow(activity, reviewInfo).addOnCompleteListener {
                lifecycleOwner.lifecycle.coroutineScope.launch {
                    reviewDataStore.markReviewFlowLaunched(
                        timestampMillis = System.currentTimeMillis(),
                        versionCode = currentVersionCode,
                    )
                }
            }
        }
        // No fallback UI, no retries, and no dependency on whether the dialog appears.
    }

    return true
}
