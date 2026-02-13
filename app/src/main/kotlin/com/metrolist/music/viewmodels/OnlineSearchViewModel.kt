package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.net.URLDecoder
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
    private val loadMoreMutex = Mutex()

    init {
        viewModelScope.launch {
            filter.collectLatest { filter ->
                if (filter == null) {
                    if (summaryPage == null) {
                        YouTube
                            .searchSummary(query)
                            .onSuccess {
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                summaryPage =
                                    it.filterExplicit(
                                        hideExplicit,
                                    ).filterVideoSongs(hideVideoSongs)
                            }.onFailure {
                                reportException(it)
                            }
                    }
                } else {
                    if (viewStateMap[filter.value] == null) {
                        YouTube
                            .search(query, filter)
                            .onSuccess { result ->
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                viewStateMap[filter.value] =
                                    ItemsPage(
                                        result.items
                                            .distinctBy { it.id }
                                            .filterExplicit(
                                                hideExplicit,
                                            )
                                            .filterVideoSongs(hideVideoSongs),
                                        result.continuation,
                                    )
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val filterValue = filter.value?.value ?: return@launch
            loadMoreMutex.withLock {
                val viewState = viewStateMap[filterValue] ?: return@withLock
                val continuation = viewState.continuation ?: return@withLock

                val searchResult = YouTube.searchContinuation(continuation).getOrNull() ?: return@withLock
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val newItems = searchResult.items
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                viewStateMap[filterValue] = ItemsPage(
                    (viewState.items + newItems).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}
