package com.metrolist.music.ui.screens

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton


// SubscriptionManager.kt
@Singleton
class SubscriptionManager @Inject constructor() {
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    private val entitlementId = "premium"
    private val listener = object : UpdatedCustomerInfoListener {
        override fun onReceived(info: CustomerInfo) {
            _isSubscribed.value = info.entitlements.active.containsKey(entitlementId)
        }
    }

    init {
        fetchCustomerInfo(retryOnError = true)
        // Real-time updates whenever RevenueCat pushes a state change
        Purchases.sharedInstance.updatedCustomerInfoListener = listener
    }

    // FIX: Retry the initial fetch once after a delay if it fails.
    // On cold start, Google Play Billing is often not yet connected when RevenueCat
    // first tries to reach it, causing a transient error that permanently leaves
    // _isSubscribed=false for the session — making subscribed users appear unsubscribed
    // until the app is restarted.
    private fun fetchCustomerInfo(retryOnError: Boolean) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(info: CustomerInfo) {
                _isSubscribed.value = info.entitlements.active.containsKey(entitlementId)
            }
            override fun onError(error: PurchasesError) {
                if (retryOnError) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        fetchCustomerInfo(retryOnError = false)
                    }, 1_500L)
                } else {
                    _isSubscribed.value = false
                }
            }
        })
    }

    fun clear() {
        Purchases.sharedInstance.updatedCustomerInfoListener = null
    }
}