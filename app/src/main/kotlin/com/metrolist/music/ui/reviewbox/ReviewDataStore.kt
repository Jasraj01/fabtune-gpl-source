package com.metrolist.music.ui.reviewbox

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.dataStore by preferencesDataStore("review_prefs")

class ReviewDataStore(private val context: Context) {

    // Keys
    private val REVIEW_SHOWN_KEY = booleanPreferencesKey("review_shown")
    private val FIRST_OPEN_KEY = longPreferencesKey("first_open")
    private val OPEN_COUNT_KEY = intPreferencesKey("open_count")
    // New key to track when the dialog was last dismissed ("Not Now" clicked)
    private val LAST_DISMISSED_KEY = longPreferencesKey("last_dismissed")

    // Flow to observe whether the review dialog has already been shown.
    val hasReviewed: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REVIEW_SHOWN_KEY] ?: false
    }

    // Call this function every time the app is opened.
    suspend fun incrementOpenCount() {
        context.dataStore.edit { preferences ->
            if (!preferences.contains(FIRST_OPEN_KEY)) {
                preferences[FIRST_OPEN_KEY] = System.currentTimeMillis()
            }
            val count = preferences[OPEN_COUNT_KEY] ?: 0
            preferences[OPEN_COUNT_KEY] = count + 1
            println("Open count incremented to: ${count + 1}")
        }
    }

    // Determine whether to show the review dialog.
    // In production you might use thresholds like 5 opens and 5 days;
    // here we use lower values for testing.
    suspend fun shouldShowReview(): Boolean {
        val preferences = context.dataStore.data.first()
        val reviewShown = preferences[REVIEW_SHOWN_KEY] ?: false
        if (reviewShown) {
            println("Review already shown")
            return false
        }
        // Check if the dialog was dismissed ("Not Now") recently.
        val lastDismissed = preferences[LAST_DISMISSED_KEY] ?: 0L
        val currentTime = System.currentTimeMillis()
//        val fiveDaysInMillis = 1
        val fiveDaysInMillis = 3 * 24 * 60 * 60 * 1000L
        if (lastDismissed != 0L && (currentTime - lastDismissed) < fiveDaysInMillis) {
            println("Review dismissed recently. Not showing yet.")
            return false
        }
        val openCount = preferences[OPEN_COUNT_KEY] ?: 0
        val firstOpen = preferences[FIRST_OPEN_KEY] ?: currentTime

        // Production thresholds could be:
        val thresholdOpens = 1
        val thresholdMillis = 1 * 24 * 60 * 60 * 1000L
//        val thresholdOpens = 1
//        val thresholdMillis = 1

        println("Open count: $openCount, Time passed: ${currentTime - firstOpen} ms, Thresholds: ($thresholdOpens, $thresholdMillis)")
        return (openCount >= thresholdOpens && (currentTime - firstOpen) >= thresholdMillis)
    }

    // Mark review as shown so that we don't show it again.
    suspend fun setReviewed() {
        context.dataStore.edit { preferences ->
            preferences[REVIEW_SHOWN_KEY] = true
            println("Review marked as shown")
        }
    }

    // Save the current timestamp when the user dismisses the review dialog ("Not Now").
    suspend fun setDismissed() {
        context.dataStore.edit { preferences ->
            preferences[LAST_DISMISSED_KEY] = System.currentTimeMillis()
            println("Review dismissed timestamp set")
        }
    }

    // (Optional) Function to clear DataStore for testing purposes.
    suspend fun clearDataStore() {
        context.dataStore.edit { preferences ->
            preferences.clear()
            println("DataStore cleared")
        }
    }
}
