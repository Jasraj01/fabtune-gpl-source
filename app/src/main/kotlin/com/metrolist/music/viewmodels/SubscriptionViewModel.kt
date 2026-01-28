package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import com.metrolist.music.ui.screens.SubscriptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject


@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionManager: SubscriptionManager
) : ViewModel() {
    val isSubscribed: StateFlow<Boolean> = subscriptionManager.isSubscribed
}