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
        // 1) initial fetch
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(info: CustomerInfo) {
                _isSubscribed.value = info.entitlements.active.containsKey(entitlementId)
            }
            override fun onError(error: PurchasesError) {
                _isSubscribed.value = false
            }
        })
        // 2) real-time updates
        Purchases.sharedInstance.updatedCustomerInfoListener = listener
    }

    fun clear() {
        Purchases.sharedInstance.updatedCustomerInfoListener = null
    }
}
