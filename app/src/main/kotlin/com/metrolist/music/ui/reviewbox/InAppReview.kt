package com.metrolist.music.ui.reviewbox

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.random.Random

private const val TWO_DAYS_MILLIS = 2L * 24 * 60 * 60 * 1000
private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
private const val REVIEW_CHANCE = 0.3f

suspend fun isInAppReviewEligible(
    reviewDataStore: ReviewDataStore,
    currentTimeMillis: Long = System.currentTimeMillis(),
    isAppCalm: Boolean,
): Boolean {
    if (!isAppCalm) return false

    // Ensure we have a first-open timestamp stored.
    reviewDataStore.ensureFirstOpenTimestampInitialized(currentTimeMillis)
    val firstOpenTimestamp = reviewDataStore.getFirstOpenTimestamp()
    if (firstOpenTimestamp == 0L || currentTimeMillis - firstOpenTimestamp < TWO_DAYS_MILLIS) {
        return false
    }

    val lastAttemptTimestamp = reviewDataStore.getLastReviewAttemptTimestamp()
    if (lastAttemptTimestamp != 0L &&
        currentTimeMillis - lastAttemptTimestamp < THIRTY_DAYS_MILLIS
    ) {
        return false
    }

    // 30% random chance gate
    val chance = Random.Default.nextFloat()
    return chance < REVIEW_CHANCE
}

/**
 * Triggers Google Play Core In-App Review flow if eligibility conditions pass.
 *
 * - Uses Google In-App Review API ONLY.
 * - No fallback UI.
 * - Does not depend on whether the dialog is actually shown.
 * - LAST_REVIEW_ATTEMPT_TIMESTAMP is updated immediately after requestReviewFlow().
 */
suspend fun triggerInAppReviewIfEligible(
    activity: Activity,
    reviewDataStore: ReviewDataStore,
    isAppCalm: Boolean,
) {
    val currentTimeMillis = System.currentTimeMillis()
    val eligible = isInAppReviewEligible(
        reviewDataStore = reviewDataStore,
        currentTimeMillis = currentTimeMillis,
        isAppCalm = isAppCalm,
    )

    if (!eligible) return

    reviewDataStore.updateLastReviewAttemptTimestamp(currentTimeMillis)

    val reviewManager = ReviewManagerFactory.create(activity)
    val request = reviewManager.requestReviewFlow()

    request.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val reviewInfo = task.result
            reviewManager.launchReviewFlow(activity, reviewInfo)
        }
    }
}

