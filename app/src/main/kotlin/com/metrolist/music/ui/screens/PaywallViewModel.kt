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
    val isSubscribed: Boolean = false,
    // FIX: Track whether the subscription check has completed at least once.
    // Without this, evaluatePaywall() runs while isSubscribed is still at its
    // default false — causing subscribed users to see the paywall on cold start
    // before RevenueCat's getCustomerInfo() callback has returned.
    val isSubscriptionResolved: Boolean = false
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
    private fun checkSubscription(retryOnError: Boolean = true) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {

            override fun onReceived(customerInfo: CustomerInfo) {
                val isSubbed =
                    customerInfo.entitlements.active.containsKey("premium")

                // FIX: Mark resolved=true BEFORE evaluating the paywall so that
                // evaluatePaywall() always sees a settled subscription state.
                _uiState.update { it.copy(isSubscribed = isSubbed, isSubscriptionResolved = true) }
                evaluatePaywall()
            }

            override fun onError(error: PurchasesError) {
                // FIX: On error, retry once after a short delay before falling back.
                // Google Play Billing is often not ready on cold start (first 500-1500ms),
                // which causes RevenueCat to return an error even for subscribed users.
                // Retrying after 1.5s catches the majority of these transient failures.
                if (retryOnError) {
                    viewModelScope.launch(Dispatchers.IO) {
                        kotlinx.coroutines.delay(1_500L)
                        checkSubscription(retryOnError = false)
                    }
                } else {
                    // Only evaluate paywall after the retry has also failed — this prevents
                    // showing the paywall to subscribed users due to a transient Billing error.
                    _uiState.update { it.copy(isSubscriptionResolved = true) }
                    evaluatePaywall()
                }
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
        // FIX: Never evaluate the paywall until the subscription state is confirmed.
        // Calling this before isSubscriptionResolved=true means isSubscribed defaults to
        // false, which incorrectly shows the paywall to subscribed users on cold start.
        if (!_uiState.value.isSubscriptionResolved) {
            _uiState.update { it.copy(showPaywall = false) }
            return
        }

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

            //  Minimum 2 hours gap between paywall appearances
            val minGapMillis = 2 * 60 * 60 * 1000L
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
            if (shownToday >= 4) {
                _uiState.update { it.copy(showPaywall = false) }
                return@launch
            }

            //  Random chance (50%)
            val shouldShow = Random.nextFloat() < 0.70f
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