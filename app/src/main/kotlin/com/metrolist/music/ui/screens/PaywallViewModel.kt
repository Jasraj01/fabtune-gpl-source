package com.metrolist.music.ui.screens

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.dataStore
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------
// 1) DataStore Setup (Keys)
// ------------------------------
object PaywallKeys {
    val INSTALL_TIME_KEY = longPreferencesKey("install_time_key")
    val FIRST_PAYWALL_SHOWN_KEY = booleanPreferencesKey("first_paywall_shown_key")
}

// ------------------------------
// 2) Paywall UiState
// ------------------------------
data class PaywallUiState(
    val showPaywall: Boolean = false,
    val isSubscribed: Boolean = false
)

// ------------------------------
// 3) Paywall ViewModel
// ------------------------------
class PaywallViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val dataStore = context.dataStore

    private var installTime: Long = 0L
    private var firstPaywallShown: Boolean = false

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            installTime = prefs[PaywallKeys.INSTALL_TIME_KEY] ?: 0L
            firstPaywallShown = prefs[PaywallKeys.FIRST_PAYWALL_SHOWN_KEY] ?: false

            if (installTime == 0L) {
                val now = System.currentTimeMillis()
                saveInstallTime(now)
                saveFirstPaywallShown(false)
                installTime = now
                firstPaywallShown = false
            }

            // IMPORTANT: only check subscription here
            checkSubscription()
        }
    }

    private suspend fun saveInstallTime(time: Long) {
        dataStore.edit { it[PaywallKeys.INSTALL_TIME_KEY] = time }
    }

    private suspend fun saveFirstPaywallShown(shown: Boolean) {
        dataStore.edit { it[PaywallKeys.FIRST_PAYWALL_SHOWN_KEY] = shown }
        firstPaywallShown = shown
    }

    private fun checkSubscription() {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {

            override fun onReceived(customerInfo: CustomerInfo) {
                val isSubbed =
                    customerInfo.entitlements.active.containsKey("premium")

                _uiState.update {
                    it.copy(isSubscribed = isSubbed)
                }

                // 🔑 Re-evaluate paywall AFTER subscription is known
                evaluatePaywall()
            }

            override fun onError(error: PurchasesError) {
                // Still evaluate with isSubscribed = false
                evaluatePaywall()
            }
        })
    }

    private fun evaluatePaywall() {
        // Premium users NEVER see paywall
        if (_uiState.value.isSubscribed) {
            _uiState.update { it.copy(showPaywall = false) }
            return
        }

        // First app open → do NOT show paywall
        if (!firstPaywallShown) {
            viewModelScope.launch(Dispatchers.IO) {
                saveFirstPaywallShown(true)
            }
            _uiState.update { it.copy(showPaywall = false) }
            return
        }

        // Second open onwards → show paywall
        _uiState.update { it.copy(showPaywall = true) }
    }

    fun markPaywallShown() {
        _uiState.update { it.copy(showPaywall = false) }
    }
}
