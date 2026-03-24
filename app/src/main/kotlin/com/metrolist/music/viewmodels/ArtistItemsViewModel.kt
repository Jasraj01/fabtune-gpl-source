package com.metrolist.music.viewmodels

/*
APPLE MUSIC-GRADE PERFORMANCE OPTIMIZATION REPORT

Bottlenecks Identified:
- Network/DataStore work started from default-main coroutines.
- Load-more could be triggered repeatedly for the same continuation token.

Optimizations Applied:
- Moved initial and pagination fetches to Dispatchers.IO.
- Added single-flight pagination guard with Mutex.
- Deduplicated continuation tokens to keep load-more idempotent.

Main-thread work reduced by:
- Removing network/data reads from default-main in this ViewModel.

Recomposition reductions:
- Prevented redundant itemsPage updates from duplicate load-more fetches.

Scroll performance improvements:
- Stabilized pagination behavior under rapid scroll-end triggers.

Image pipeline improvements:
- N/A in this file.

Expected impact on low-end devices:
- Lower risk of burst network work during fling near list tail.
*/

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.models.ItemsPage
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browseId = savedStateHandle.get<String>("browseId")!!
    private val params = savedStateHandle.get<String>("params")

    val title = MutableStateFlow("")
    val itemsPage = MutableStateFlow<ItemsPage?>(null)
    private val loadMoreMutex = Mutex()
    private val loadedContinuationTokens = LinkedHashSet<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube
                .artistItems(
                    BrowseEndpoint(
                        browseId = browseId,
                        params = params,
                    ),
                ).onSuccess { artistItemsPage ->
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                    title.value = artistItemsPage.title
                    itemsPage.value =
                        ItemsPage(
                            items = artistItemsPage.items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs),
                            continuation = artistItemsPage.continuation,
                        )
                    loadedContinuationTokens.clear()
                }.onFailure {
                    reportException(it)
                }
        }
    }

    fun loadMore() {
        viewModelScope.launch(Dispatchers.IO) {
            loadMoreMutex.withLock {
                val oldItemsPage = itemsPage.value ?: return@withLock
                val continuation = oldItemsPage.continuation ?: return@withLock
                if (!loadedContinuationTokens.add(continuation)) return@withLock

                YouTube
                    .artistItemsContinuation(continuation)
                    .onSuccess { artistItemsContinuationPage ->
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                        itemsPage.update {
                            ItemsPage(
                                items =
                                    (oldItemsPage.items + artistItemsContinuationPage.items)
                                        .distinctBy { it.id }
                                        .filterExplicit(hideExplicit)
                                        .filterVideoSongs(hideVideoSongs),
                                continuation = artistItemsContinuationPage.continuation,
                            )
                        }
                    }.onFailure {
                        loadedContinuationTokens.remove(continuation)
                        reportException(it)
                    }
            }
        }
    }
}
