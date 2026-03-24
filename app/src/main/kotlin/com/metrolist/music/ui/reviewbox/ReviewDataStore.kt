package com.metrolist.music.ui.reviewbox

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Dedicated DataStore for review state
private val Context.reviewDataStore by preferencesDataStore("review_prefs")

class ReviewDataStore(private val context: Context) {

    private val FIRST_OPEN_TIMESTAMP = longPreferencesKey("FIRST_OPEN_TIMESTAMP")
    private val LAST_REVIEW_ATTEMPT_TIMESTAMP = longPreferencesKey("LAST_REVIEW_ATTEMPT_TIMESTAMP")
    private val FOREGROUND_SESSION_COUNT = intPreferencesKey("FOREGROUND_SESSION_COUNT")
    private val LAST_REVIEW_VERSION_CODE = intPreferencesKey("LAST_REVIEW_VERSION_CODE")

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

    suspend fun incrementForegroundSessionCount(): Int {
        var updatedCount = 0
        context.reviewDataStore.edit { preferences ->
            updatedCount = (preferences[FOREGROUND_SESSION_COUNT] ?: 0) + 1
            preferences[FOREGROUND_SESSION_COUNT] = updatedCount
        }
        return updatedCount
    }

    suspend fun getForegroundSessionCount(): Int {
        val preferences = context.reviewDataStore.data.first()
        return preferences[FOREGROUND_SESSION_COUNT] ?: 0
    }

    suspend fun getLastReviewVersionCode(): Int? {
        val preferences = context.reviewDataStore.data.first()
        return preferences[LAST_REVIEW_VERSION_CODE]
    }

    suspend fun markReviewFlowLaunched(
        timestampMillis: Long,
        versionCode: Int,
    ) {
        context.reviewDataStore.edit { preferences ->
            preferences[LAST_REVIEW_ATTEMPT_TIMESTAMP] = timestampMillis
            preferences[LAST_REVIEW_VERSION_CODE] = versionCode
        }
    }
}
