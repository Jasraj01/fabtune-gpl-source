package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = Channel<Boolean>(Channel.CONFLATED)
    val networkStatus = _networkStatus.receiveAsFlow()
    private var isCallbackRegistered = false

    // ADVANCED FIX – Track network type and VPN state
    enum class NetworkType {
        WIFI,
        MOBILE,
        VPN,
        OTHER,
        NONE,
    }

    private val _networkType = Channel<NetworkType>(Channel.CONFLATED)
    val networkType = _networkType.receiveAsFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.trySend(true)
            _networkType.trySend(currentNetworkType())
        }

        override fun onLost(network: Network) {
            _networkStatus.trySend(false)
            _networkType.trySend(NetworkType.NONE)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isCallbackRegistered = true
        } catch (e: Exception) {
            // Fallback: assume connected if registration fails
            _networkStatus.trySend(true)
        }

        // Send initial state
        val isInitiallyConnected = isCurrentlyConnected()
        _networkStatus.trySend(isInitiallyConnected)
        _networkType.trySend(currentNetworkType())
    }

    fun unregister() {
        if (!isCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isCallbackRegistered = false
        } catch (_: IllegalArgumentException) {
            // Callback may already be unregistered during shutdown races.
            isCallbackRegistered = false
        }
    }

    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            // Check if we have internet capability
            val hasInternet = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            // For API 23+, also check if connection is validated
            val isValidated = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            } else {
                true // For older versions, assume validated if we have internet capability
            }

            hasInternet && isValidated
        } catch (e: Exception) {
            false
        }
    }

    // ADVANCED FIX – Detect WIFI ↔ MOBILE and VPN on/off
    private fun currentNetworkType(): NetworkType {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

            val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            when {
                hasVpn -> NetworkType.VPN
                isWifi -> NetworkType.WIFI
                isCellular -> NetworkType.MOBILE
                else -> NetworkType.OTHER
            }
        } catch (_: Exception) {
            NetworkType.NONE
        }
    }
}
