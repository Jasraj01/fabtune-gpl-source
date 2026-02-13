package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.runResultWithRetry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {

    private val _explorePage = MutableStateFlow<ExplorePage?>(null)
    val explorePage: StateFlow<ExplorePage?> = _explorePage.asStateFlow()

    private val _moodAndGenresGrouped = MutableStateFlow<List<MoodAndGenres>?>(null)
    val moodAndGenresGrouped: StateFlow<List<MoodAndGenres>?> = _moodAndGenresGrouped.asStateFlow()

    // Cache for mood/genre thumbnails keyed by browseId+params
    private val _moodThumbnails = MutableStateFlow<Map<String, String?>>(emptyMap())
    val moodThumbnails: StateFlow<Map<String, String?>> = _moodThumbnails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val thumbnailPrefetchSemaphore = Semaphore(permits = 2)
    private var moodThumbnailPrefetchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            moodThumbnailPrefetchJob?.cancel()

            try {
                val exploreDeferred = async(start = CoroutineStart.DEFAULT) { loadExplore() }
                val moodsDeferred = async(start = CoroutineStart.DEFAULT) { loadMoodAndGenres() }
                exploreDeferred.await()
                moodsDeferred.await()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadExplore() {
        runResultWithRetry(
            timeoutMillis = 15_000L,
            maxRetries = 2,
        ) {
            YouTube.explore()
        }
            .onSuccess { page ->
                val artists = mutableMapOf<Int, String>()
                val favouriteArtists = mutableMapOf<Int, String>()

                database.allArtistsByPlayTime().first().let { list ->
                    var favIndex = 0
                    for ((artistsIndex, artist) in list.withIndex()) {
                        artists[artistsIndex] = artist.id
                        if (artist.artist.bookmarkedAt != null) {
                            favouriteArtists[favIndex] = artist.id
                            favIndex++
                        }
                    }
                }

                _explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums
                        .sortedBy { album ->
                            val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                            artistIds.firstNotNullOfOrNull { artistId ->
                                when {
                                    artistId in favouriteArtists.values -> favouriteArtists.entries
                                        .firstOrNull { it.value == artistId }?.key
                                    else -> artists.entries.firstOrNull { it.value == artistId }?.key
                                }
                            } ?: Int.MAX_VALUE
                        }
                        .filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                )
            }.onFailure {
                if (_explorePage.value == null) {
                    _error.value = "Failed to load explore content. Pull to refresh or retry."
                }
                reportException(it)
            }
    }

    private suspend fun loadMoodAndGenres() {
        runResultWithRetry(
            timeoutMillis = 15_000L,
            maxRetries = 2,
        ) {
            YouTube.moodAndGenres()
        }
            .onSuccess {
                if (it.isNotEmpty()) {
                    _moodAndGenresGrouped.value = it

                    // Prefetch in background with bounded concurrency so every mood card can get an image
                    // without blocking first paint on slow networks.
                    it.firstOrNull { group -> group.title == "Moods & moments" }?.let { group ->
                        moodThumbnailPrefetchJob?.cancel()
                        moodThumbnailPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
                            prefetchMoodThumbnails(group)
                        }
                    }
                }
            }.onFailure {
                if (_moodAndGenresGrouped.value == null && _explorePage.value == null) {
                    _error.value = "Failed to load mood and genres. Pull to refresh or retry."
                }
                reportException(it)
            }
    }

    private suspend fun prefetchMoodThumbnails(group: MoodAndGenres) {
        coroutineScope {
            val existing = _moodThumbnails.value.toMutableMap()
            val writeLock = Any()

            val tasks = group.items.map { item ->
                async(Dispatchers.IO) {
                    val key = item.endpoint.browseId + (item.endpoint.params ?: "")
                    if (existing.containsKey(key)) return@async

                    thumbnailPrefetchSemaphore.withPermit {
                        val url = runResultWithRetry(
                            timeoutMillis = 10_000L,
                            maxRetries = 1,
                        ) {
                            YouTube.browse(item.endpoint.browseId, item.endpoint.params)
                        }.getOrNull()
                            ?.items
                            ?.flatMap { it.items }
                            ?.firstOrNull { it.thumbnail != null }
                            ?.thumbnail

                        synchronized(writeLock) {
                            if (!existing.containsKey(key)) {
                                if (url != null) {
                                    existing[key] = url
                                    _moodThumbnails.value = existing.toMap()
                                }
                            }
                        }
                    }
                }
            }

            runCatching { tasks.awaitAll() }
                .onFailure { reportException(it) }

            _moodThumbnails.value = existing
        }
    }
}
