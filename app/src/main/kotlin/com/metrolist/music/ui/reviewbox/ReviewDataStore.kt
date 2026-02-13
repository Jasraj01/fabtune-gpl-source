package com.metrolist.music.ui.reviewbox

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Dedicated DataStore for review state
private val Context.reviewDataStore by preferencesDataStore("review_prefs")

class ReviewDataStore(private val context: Context) {

    private val FIRST_OPEN_TIMESTAMP = longPreferencesKey("FIRST_OPEN_TIMESTAMP")
    private val LAST_REVIEW_ATTEMPT_TIMESTAMP = longPreferencesKey("LAST_REVIEW_ATTEMPT_TIMESTAMP")

    /**
     * Ensure FIRST_OPEN_TIMESTAMP is written once on first app launch.
     */
    suspend fun ensureFirstOpenTimestampInitialized(currentTimeMillis: Long = System.currentTimeMillis()) {
        context.reviewDataStore.edit { preferences ->
            if (!preferences.contains(FIRST_OPEN_TIMESTAMP)) {
                preferences[FIRST_OPEN_TIMESTAMP] = currentTimeMillis
            }
        }
    }

    suspend fun getFirstOpenTimestamp(): Long {
        val preferences = context.reviewDataStore.data.first()
        return preferences[FIRST_OPEN_TIMESTAMP] ?: 0L
    }

    suspend fun getLastReviewAttemptTimestamp(): Long {
        val preferences = context.reviewDataStore.data.first()
        return preferences[LAST_REVIEW_ATTEMPT_TIMESTAMP] ?: 0L
    }

    suspend fun updateLastReviewAttemptTimestamp(timestampMillis: Long) {
        context.reviewDataStore.edit { preferences ->
            preferences[LAST_REVIEW_ATTEMPT_TIMESTAMP] = timestampMillis
        }
    }
}
