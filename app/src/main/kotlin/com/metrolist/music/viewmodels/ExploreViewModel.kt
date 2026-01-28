package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {

    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val moodAndGenresGrouped = MutableStateFlow<List<MoodAndGenres>?>(null)
    // Cache for mood/genre thumbnails keyed by browseId+params
    val moodThumbnails = MutableStateFlow<Map<String, String?>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Load both in parallel
            val exploreDeferred = async { loadExplore() }
            val moodsDeferred = async { loadMoodAndGenres() }

            // Wait for both to complete
            exploreDeferred.await()
            moodsDeferred.await()
        }
    }

    private suspend fun loadExplore() {
        YouTube.explore()
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

                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums
                        .sortedBy { album ->
                            val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                            val firstArtistKey =
                                artistIds.firstNotNullOfOrNull { artistId ->
                                    if (artistId in favouriteArtists.values) {
                                        favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                    } else {
                                        artists.entries.firstOrNull { it.value == artistId }?.key
                                    }
                                } ?: Int.MAX_VALUE
                            firstArtistKey
                        }.filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                )
            }.onFailure {
                reportException(it)
            }
    }

    private suspend fun loadMoodAndGenres() {
        YouTube.moodAndGenres()
            .onSuccess {
                if (it.isNotEmpty()) {
                    moodAndGenresGrouped.value = it
                    // Prefetch thumbnails only for "Moods & moments" group to keep UI smooth
                    it.forEach { group ->
                        prefetchMoodThumbnails(group)
                    }
                }
            }.onFailure {
                reportException(it)
            }
    }

    private suspend fun prefetchMoodThumbnails(group: MoodAndGenres) {
        runCatching {
            val existing = moodThumbnails.value.toMutableMap()
            val tasks = group.items.map { item ->
                viewModelScope.async(Dispatchers.IO) {
                    val key = item.endpoint.browseId + (item.endpoint.params ?: "")
                    if (existing.containsKey(key)) return@async
                    val url = YouTube
                        .browse(item.endpoint.browseId, item.endpoint.params)
                        .getOrNull()
                        ?.items
                        ?.flatMap { it.items }
                        ?.firstOrNull { it.thumbnail != null }
                        ?.thumbnail
                    if (!existing.containsKey(key)) existing[key] = url
                }
            }
            tasks.awaitAll()
            moodThumbnails.value = existing
        }.onFailure { reportException(it) }
    }
}
