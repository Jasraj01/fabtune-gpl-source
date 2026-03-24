
package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.syncCoroutine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.WrappedSeenKey
import com.metrolist.music.ui.screens.wrapped.WrappedAudioService
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.metrolist.music.utils.runResultWithRetry
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    private val cookieFlow = context.dataStore.data
        .map { it[InnerTubeCookieKey] }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val isQuickPicksLoading = MutableStateFlow(true)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    private val _networkError = MutableStateFlow<String?>(null)
    val networkError: StateFlow<String?> = _networkError.asStateFlow()

    val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit {
                it[WrappedSeenKey] = true
            }
        }
    }
    // Single-flight guards prevent overlapping heavy work and reduce redundant emissions.
    private val loadMutex = Mutex()
    private val loadMoreMutex = Mutex()
    private val wrappedPrepareMutex = Mutex()
    private val quickPicksMutex = Mutex()
    private val quickPicksPlaybackSongId = MutableStateFlow<String?>(null)

    private suspend inline fun <T> resilientYouTubeRequest(
        timeoutMillis: Long = 15_000L,
        maxRetries: Int = 2,
        crossinline block: suspend () -> Result<T>,
    ): Result<T> = runResultWithRetry(
        timeoutMillis = timeoutMillis,
        maxRetries = maxRetries,
        block = block,
    )

    private suspend fun contentFilters(): Pair<Boolean, Boolean> {
        // Read both preferences in one snapshot to avoid multiple DataStore reads/cross-thread blocking.
        val prefs = context.dataStore.data.first()
        return (prefs[HideExplicitKey] ?: false) to (prefs[HideVideoSongsKey] ?: false)
    }

    private suspend fun currentQuickPicksSeedId(): String? {
        quickPicksPlaybackSongId.value?.let { playbackSongId ->
            return playbackSongId
        }
        return database.events().first().firstOrNull()?.song?.id
    }

    private suspend fun getLocalRelatedSongs(songId: String?): List<Song> {
        if (songId.isNullOrBlank() || !database.hasRelatedSongs(songId)) return emptyList()
        return database.getRelatedSongs(songId).first()
    }

    private suspend fun getContextSimilarSongs(songId: String?): List<Song> {
        if (songId.isNullOrBlank()) return emptyList()

        return try {
            val endpoint = YouTube.next(WatchEndpoint(videoId = songId)).getOrNull()?.relatedEndpoint
                ?: return emptyList()
            val ytSimilarSongs = mutableListOf<Song>()
            YouTube.related(endpoint).onSuccess { page ->
                page.songs.take(12).forEach { ytSong ->
                    database.song(ytSong.id).first()?.let(ytSimilarSongs::add)
                }
            }
            ytSimilarSongs
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportException(e)
            emptyList()
        }
    }

    private suspend fun getQuickPicks() {
        val seedSongId = currentQuickPicksSeedId()
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                supervisorScope {
                    val contextRelatedSongsDeferred = async {
                        try {
                            getLocalRelatedSongs(seedSongId)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                            emptyList()
                        }
                    }
                    val relatedSongsDeferred = async {
                        try {
                            database.quickPicks().first()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                            emptyList()
                        }
                    }
                    val forgottenDeferred = async {
                        try {
                            database.forgottenFavorites().first().take(8)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                            emptyList()
                        }
                    }
                    val ytSimilarSongsDeferred = async {
                        try {
                            getContextSimilarSongs(seedSongId)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                            emptyList()
                        }
                    }

                    awaitAll(
                        contextRelatedSongsDeferred,
                        relatedSongsDeferred,
                        forgottenDeferred,
                        ytSimilarSongsDeferred
                    )
                    val contextRelatedSongs = contextRelatedSongsDeferred.await()
                    val relatedSongs = relatedSongsDeferred.await()
                    val forgotten = forgottenDeferred.await()
                    val ytSimilarSongs = ytSimilarSongsDeferred.await()

                    val combined = (
                        contextRelatedSongs.shuffled().take(12) +
                            relatedSongs.shuffled().take(10) +
                            forgotten.shuffled().take(8) +
                            ytSimilarSongs.shuffled().take(8)
                        )
                        .distinctBy { it.id }
                        .take(20)

                    quickPicks.value = combined.ifEmpty {
                        (contextRelatedSongs + relatedSongs).distinctBy { it.id }.shuffled().take(20)
                    }
                }
            }
            QuickPicks.LAST_LISTEN -> {
                val relatedSongs = getLocalRelatedSongs(seedSongId)
                if (relatedSongs.isNotEmpty()) {
                    quickPicks.value = relatedSongs.shuffled().take(20)
                }
            }
        }
    }

    private fun updateAllLocalItems() {
        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    private suspend fun loadQuickPicks(
        forceRefresh: Boolean = false,
        showLoadingPlaceholder: Boolean = quickPicks.value.isNullOrEmpty(),
    ) {
        quickPicksMutex.withLock {
            if (!forceRefresh && quickPicks.value != null) {
                isQuickPicksLoading.value = false
                return
            }

            if (showLoadingPlaceholder) {
                isQuickPicksLoading.value = true
            }
            try {
                getQuickPicks()
                updateAllLocalItems()
            } finally {
                isQuickPicksLoading.value = false
            }
        }
    }

    fun updateQuickPicksPlaybackContext(songId: String?) {
        if (quickPicksPlaybackSongId.value == songId) return
        quickPicksPlaybackSongId.value = songId
    }

    private suspend fun load() {
        if (loadMutex.isLocked) return
        loadMutex.withLock {
            isLoading.value = true
            _networkError.value = null
            try {
                val (hideExplicit, hideVideoSongs) = contentFilters()
                val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
                supervisorScope {
                    val forgottenFavoritesDeferred = async {
                        try {
                            forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20)
                            updateAllLocalItems()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }
                    val keepListeningDeferred = async {
                        try {
                            supervisorScope {
                                val keepListeningSongsDeferred = async<List<LocalItem>> {
                                    database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first().shuffled().take(10)
                                }
                                val keepListeningAlbumsDeferred = async<List<LocalItem>> {
                                    database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                                }
                                val keepListeningArtistsDeferred = async<List<LocalItem>> {
                                    database.mostPlayedArtists(fromTimeStamp).first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                                }
                                awaitAll(keepListeningSongsDeferred, keepListeningAlbumsDeferred, keepListeningArtistsDeferred)
                                val keepListeningSongs = keepListeningSongsDeferred.await()
                                val keepListeningAlbums = keepListeningAlbumsDeferred.await()
                                val keepListeningArtists = keepListeningArtistsDeferred.await()
                                keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                                updateAllLocalItems()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }
                    val accountPlaylistsDeferred = async {
                        try {
                            if (YouTube.cookie != null) {
                                resilientYouTubeRequest { YouTube.library("FEmusic_liked_playlists").completed() }
                                    .onSuccess {
                                        accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>().filterNot { item -> item.id == "SE" }
                                    }
                                    .onFailure {
                                        _networkError.value = "Could not load your playlists."
                                        reportException(it)
                                    }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }
                    val homePageDeferred = async {
                        try {
                            resilientYouTubeRequest { YouTube.home() }
                                .onSuccess { page ->
                                    homePage.value = page.copy(
                                        sections = page.sections.map { section ->
                                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs))
                                        }
                                    )
                                }
                                .onFailure {
                                    if (homePage.value == null) {
                                        _networkError.value = "Could not load Home content."
                                    }
                                    reportException(it)
                                }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }
                    val explorePageDeferred = async {
                        try {
                            resilientYouTubeRequest { YouTube.explore() }
                                .onSuccess { page ->
                                    explorePage.value = page.copy(
                                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                                    )
                                }
                                .onFailure {
                                    if (explorePage.value == null) {
                                        _networkError.value = "Could not load Explore content."
                                    }
                                    reportException(it)
                                }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }
                    val similarRecommendationsDeferred = async {
                        try {
                            val artistCandidates = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                                .filter { it.artist.isYouTubeArtist }
                                .shuffled().take(4)
                            val songCandidates = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                                .filter { it.album != null }
                                .shuffled().take(3)
                            val albumCandidates = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                                .filter { it.album.thumbnailUrl != null }
                                .shuffled().take(2)

                            val recommendations = supervisorScope {
                                val ioLimited = Dispatchers.IO.limitedParallelism(4)
                                val artistJobs = artistCandidates.map { artist ->
                                    async(ioLimited) {
                                        try {
                                            val items = mutableListOf<YTItem>()
                                            YouTube.artist(artist.id).onSuccess { page ->
                                                // Get more sections for better variety
                                                page.sections.takeLast(3).forEach { section ->
                                                    items += section.items
                                                }
                                            }
                                            SimilarRecommendation(
                                                title = artist,
                                                items = items
                                                    .distinctBy { item -> item.id }
                                                    .filterExplicit(hideExplicit)
                                                    .filterVideoSongs(hideVideoSongs)
                                                    .shuffled()
                                                    .take(12)
                                                    .ifEmpty { return@async null }
                                            )
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            reportException(e)
                                            null
                                        }
                                    }
                                }
                                val songJobs = songCandidates.map { song ->
                                    async(ioLimited) {
                                        try {
                                            val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                                                ?: return@async null
                                            val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                                            SimilarRecommendation(
                                                title = song,
                                                items = (page.songs.shuffled().take(10) +
                                                        page.albums.shuffled().take(5) +
                                                        page.artists.shuffled().take(3) +
                                                        page.playlists.shuffled().take(3))
                                                    .distinctBy { it.id }
                                                    .filterExplicit(hideExplicit)
                                                    .filterVideoSongs(hideVideoSongs)
                                                    .shuffled()
                                                    .ifEmpty { return@async null }
                                            )
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            reportException(e)
                                            null
                                        }
                                    }
                                }
                                val albumJobs = albumCandidates.map { album ->
                                    async(ioLimited) {
                                        try {
                                            val items = mutableListOf<YTItem>()
                                            YouTube.album(album.id).onSuccess { page ->
                                                // Get related albums and artists
                                                page.otherVersions.let { items += it }
                                            }
                                            // Also get artist's other content
                                            album.artists.firstOrNull()?.id?.let { artistId ->
                                                YouTube.artist(artistId).onSuccess { page ->
                                                    page.sections.lastOrNull()?.items?.let { items += it }
                                                }
                                            }
                                            SimilarRecommendation(
                                                title = album,
                                                items = items
                                                    .distinctBy { it.id }
                                                    .filterExplicit(hideExplicit)
                                                    .filterVideoSongs(hideVideoSongs)
                                                    .shuffled()
                                                    .take(10)
                                                    .ifEmpty { return@async null }
                                            )
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            reportException(e)
                                            null
                                        }
                                    }
                                }

                                withContext(Dispatchers.Default) {
                                    (artistJobs + songJobs + albumJobs).awaitAll().filterNotNull().shuffled()
                                }
                            }
                            similarRecommendations.value = recommendations
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                        }
                    }

                    awaitAll(forgottenFavoritesDeferred, keepListeningDeferred)
                    updateAllLocalItems()

                    awaitAll(accountPlaylistsDeferred, homePageDeferred, explorePageDeferred, similarRecommendationsDeferred)
                    allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                            homePage.value?.sections?.flatMap { it.items }.orEmpty()
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value || isRefreshing.value || loadMutex.isLocked) return

        viewModelScope.launch(Dispatchers.IO) {
            if (!loadMoreMutex.tryLock()) return@launch
            _isLoadingMore.value = true
            try {
                val (hideExplicit, hideVideoSongs) = contentFilters()
                val loadMoreResult = resilientYouTubeRequest {
                    YouTube.home(continuation)
                }
                val nextSections = loadMoreResult.getOrElse { throwable ->
                    return@launch
                }

                homePage.value = nextSections.copy(
                    chips = homePage.value?.chips,
                    sections = (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                        section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs))
                    }
                )
            } finally {
                _isLoadingMore.value = false
                loadMoreMutex.unlock()
            }
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val (hideExplicit, hideVideoSongs) = contentFilters()
            val nextSections = resilientYouTubeRequest {
                YouTube.home(params = chip.endpoint?.params)
            }.getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs))
                }
            )
            selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value || loadMutex.isLocked) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            try {
                supervisorScope {
                    val quickPicksDeferred = async {
                        loadQuickPicks(forceRefresh = true, showLoadingPlaceholder = quickPicks.value.isNullOrEmpty())
                    }
                    val homeDeferred = async {
                        load()
                    }
                    awaitAll(quickPicksDeferred, homeDeferred)
                }
            } finally {
                isRefreshing.value = false
            }
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(syncCoroutine) {
            syncUtils.tryAutoSync()
        }
    }

    init {
        // Load home data
        viewModelScope.launch(Dispatchers.IO) {
            supervisorScope {
                launch {
                    loadQuickPicks()
                }
                launch {
                    cookieFlow.first()
                    load()
                }
            }
        }

        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(syncCoroutine) {
            syncUtils.tryAutoSync()
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            showWrappedCard
                .collectLatest { shouldShow ->
                    if (!shouldShow || wrappedManager.state.value.isDataReady) return@collectLatest
                    if (!wrappedPrepareMutex.tryLock()) return@collectLatest
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    } finally {
                        wrappedPrepareMutex.unlock()
                    }
                }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            cookieFlow.collectLatest { cookie ->
                if (!cookie.isNullOrEmpty()) {
                    // Avoid redundant assignment churn when cookie value did not change.
                    if (YouTube.cookie != cookie) YouTube.cookie = cookie

                    // CollectLatest cancels stale account calls when cookie updates rapidly.
                    resilientYouTubeRequest(timeoutMillis = 12_000L, maxRetries = 2) {
                        YouTube.accountInfo()
                    }
                        .onSuccess { info ->
                            accountName.value = info.name
                            accountImageUrl.value = info.thumbnailUrl
                        }
                        .onFailure {
                            reportException(it)
                        }
                } else {
                    accountName.value = "Guest"
                    accountImageUrl.value = null
                    accountPlaylists.value = null
                }
            }
        }
    }

}
