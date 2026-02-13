package com.metrolist.music.ui.screens

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.dataStore
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

// ------------------------------
// 1) DataStore Keys
// ------------------------------
object PaywallKeys {
    val INSTALL_TIME_KEY = longPreferencesKey("install_time_key")
    val OPEN_COUNT_KEY = intPreferencesKey("open_count_key")
    val LAST_PAYWALL_TIME_KEY = longPreferencesKey("last_paywall_time_key")
    val PAYWALL_SHOWN_TODAY_KEY = intPreferencesKey("paywall_shown_today_key")
    val PAYWALL_DAY_KEY = longPreferencesKey("paywall_day_key")
}

// ------------------------------
// 2) UI State
// ------------------------------
data class PaywallUiState(
    val showPaywall: Boolean = false,
    val isSubscribed: Boolean = false
)

// ------------------------------
// 3) ViewModel
// ------------------------------
class PaywallViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val dataStore = context.dataStore

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            incrementOpenCount()
            checkSubscription()
        }
    }

    // ------------------------------
    // App open tracking
    // ------------------------------
    private suspend fun incrementOpenCount() {
        dataStore.edit { prefs ->
            val current = prefs[PaywallKeys.OPEN_COUNT_KEY] ?: 0
            prefs[PaywallKeys.OPEN_COUNT_KEY] = current + 1

            if (!prefs.contains(PaywallKeys.INSTALL_TIME_KEY)) {
                prefs[PaywallKeys.INSTALL_TIME_KEY] = System.currentTimeMillis()
            }
        }
    }

    // ------------------------------
    // Subscription check
    // ------------------------------
    private fun checkSubscription() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {

            override fun onReceived(customerInfo: CustomerInfo) {
                val isSubbed =
                    customerInfo.entitlements.active.containsKey("premium")

                // 🔑 Re-evaluate paywall AFTER subscription is known
                _uiState.update { it.copy(isSubscribed = isSubbed) }
                evaluatePaywall()
            }

            override fun onError(error: PurchasesError) {
                evaluatePaywall()
            }
        })
    }

    fun onSubscriptionStateChanged(isSubscribed: Boolean) {
        val current = _uiState.value
        if (current.isSubscribed == isSubscribed) return
        _uiState.update {
            it.copy(
                isSubscribed = isSubscribed,
                showPaywall = if (isSubscribed) false else it.showPaywall
            )
        }
    }

    // ------------------------------
    // Core paywall logic
    // ------------------------------
    private fun evaluatePaywall() {
        //  Premium users NEVER see paywall
        if (_uiState.value.isSubscribed) {
            _uiState.update { it.copy(showPaywall = false) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()

            val openCount = prefs[PaywallKeys.OPEN_COUNT_KEY] ?: 0
            if (openCount <= 2) {
                // First 2 opens → no paywall
                _uiState.update { it.copy(showPaywall = false) }
                return@launch
            }

            val now = System.currentTimeMillis()
            val lastShown = prefs[PaywallKeys.LAST_PAYWALL_TIME_KEY] ?: 0L

            //  Minimum 6 hours gap
            val minGapMillis = 6 * 60 * 60 * 1000L
            if (lastShown != 0L && now - lastShown < minGapMillis) {
                _uiState.update { it.copy(showPaywall = false) }
                return@launch
            }

            //  Daily cap reset
            val today = now / (24 * 60 * 60 * 1000L)
            val savedDay = prefs[PaywallKeys.PAYWALL_DAY_KEY] ?: today
            var shownToday = prefs[PaywallKeys.PAYWALL_SHOWN_TODAY_KEY] ?: 0

            if (savedDay != today) {
                shownToday = 0
                dataStore.edit {
                    it[PaywallKeys.PAYWALL_DAY_KEY] = today
                    it[PaywallKeys.PAYWALL_SHOWN_TODAY_KEY] = 0
                }
            }

            //  Max 2 per day
            if (shownToday >= 3) {
                _uiState.update { it.copy(showPaywall = false) }
                return@launch
            }

            //  Random chance (50%)
            val shouldShow = Random.nextFloat() < 0.5f
            _uiState.update { it.copy(showPaywall = shouldShow) }

            if (shouldShow) {
                dataStore.edit {
                    it[PaywallKeys.LAST_PAYWALL_TIME_KEY] = now
                    it[PaywallKeys.PAYWALL_SHOWN_TODAY_KEY] = shownToday + 1
                    it[PaywallKeys.PAYWALL_DAY_KEY] = today
                }
            }
        }
    }

    // Call after closing paywall
    fun markPaywallShown() {
        _uiState.update { it.copy(showPaywall = false) }
    }
}
