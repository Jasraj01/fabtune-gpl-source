package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ChartsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.metrolist.music.utils.runResultWithRetry
import javax.inject.Inject

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {
    private val _chartsPage = MutableStateFlow<ChartsPage?>(null)
    val chartsPage = _chartsPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun loadCharts(force: Boolean = false) {
        if (_isLoading.value) return
        if (!force && _chartsPage.value != null) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            runResultWithRetry(
                timeoutMillis = 15_000L,
                maxRetries = 2,
            ) {
                YouTube.getChartsPage()
            }
                .onSuccess { page ->
                    _chartsPage.value = page
                }
                .onFailure { e ->
                    _error.value = "Failed to load charts: ${e.message ?: "Unknown error"}"
                }

            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _chartsPage.value?.continuation?.let { continuation ->
                _isLoading.value = true
                runResultWithRetry(
                    timeoutMillis = 15_000L,
                    maxRetries = 2,
                ) {
                    YouTube.getChartsPage(continuation)
                }
                    .onSuccess { newPage ->
                        _chartsPage.value = _chartsPage.value?.copy(
                            sections = _chartsPage.value?.sections.orEmpty() + newPage.sections,
                            continuation = newPage.continuation
                        )
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load more: ${e.message ?: "Unknown error"}"
                    }
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        loadCharts(force = true)
    }
}
