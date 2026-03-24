package com.metrolist.music.ui.screens

/*
 * ANR REPORT (2026-02-26)
 *
 * Observed log meaning:
 * - "Binder ... Outgoing transactions ... FLAG_ONEWAY" appeared while Android was collecting ANR traces.
 * - In practice this points to main-thread starvation: UI/input/Binder callbacks were delayed long enough
 *   for the system to capture an ANR for `com.fabtune.music.player`.
 *
 * Refactored hotspots in this file:
 * - `HomeScreen()` infinite-scroll `snapshotFlow` collector.
 * - `HomeScreen()` prefetch model preparation used by `PrefetchNextImages`.
 * - `HomeScreen()` per-item Room song observers for Quick Picks / Forgotten Favorites rows.
 *
 * Before:
 * - Scroll-end detection could fire load-more dispatches too aggressively during rapid layout updates.
 * - Prefetch model list construction ran synchronously on Main during composition.
 *
 * After:
 * - Scroll load-more is debounced + deduplicated by continuation token.
 * - Prefetch model list transformations are moved to `Dispatchers.Default`.
 * - Room flow upstream for item observers is explicitly moved to `Dispatchers.IO`.
 * - Behavior/features are unchanged, but main-thread contention during initial compose + scroll is reduced.
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.SmallGridThumbnailHeight
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.constants.ListThumbnailSize
import com.metrolist.music.constants.ThumbnailCornerRadius
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.YouTubeAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.ads.MediumRectangleAd
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.image.PrefetchImageModel
import com.metrolist.music.ui.component.image.PrefetchNextImages
import com.metrolist.music.ui.component.image.ProgressiveImageModel
import com.metrolist.music.ui.component.image.ProgressiveNetworkImage
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

private const val MEDIUM_RECTANGLE_AD_UNIT_ID = ""
private const val QUICK_PICKS_PLACEHOLDER_ITEMS = 8
private enum class HomeRenderStage { Initial, Interactive }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isSubscribed = LocalIsSubscribed.current
    val haptic = LocalHapticFeedback.current

    // Lifecycle-aware collection avoids unnecessary updates while this screen is not active.
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val activeMediaId = mediaMetadata?.id
    val activeAlbumId = mediaMetadata?.album?.id

    // ViewModel state
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val isQuickPicksLoading by viewModel.isQuickPicksLoading.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()

    val allLocalItems by viewModel.allLocalItems.collectAsStateWithLifecycle()
    val allYtItems by viewModel.allYtItems.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()
    val networkError by viewModel.networkError.collectAsStateWithLifecycle()

    val isLoading: Boolean by viewModel.isLoading.collectAsStateWithLifecycle()
    val isMoodAndGenresLoading = isLoading && explorePage?.moodAndGenres == null
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()

    // Stable grid states
    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()
    val keepListeningGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    var renderStage by remember { mutableStateOf(HomeRenderStage.Initial) }
    // ANR fix: keep initial composition side-effects lightweight. We only wait for first frame and
    // defer optional work (prefetch/ad composition) until interactive stage, avoiding heavy startup work.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        // Give the first frame and early touch handling a small head start.
        delay(140)
        renderStage = HomeRenderStage.Interactive
    }
    val canRunPrefetch = renderStage == HomeRenderStage.Interactive
    val canComposeHomeAd = renderStage == HomeRenderStage.Interactive
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val currentGridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val density = LocalDensity.current
    val bodyLargeLineHeight = MaterialTheme.typography.bodyLarge.lineHeight
    val bodyMediumLineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    val keepListeningBaseRowHeight = remember(
        currentGridHeight,
        density,
        bodyLargeLineHeight,
        bodyMediumLineHeight
    ) {
        with(density) {
            currentGridHeight +
                    bodyLargeLineHeight.toDp() * 2 +
                    bodyMediumLineHeight.toDp() * 2
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle(false)

    // ANR fix: prefetch model mapping can be large and was previously built synchronously on Main during
    // composition. Moving this work to Default avoids frame stalls while keeping the same output list.
    val quickPicksPrefetchModels by produceState(
        initialValue = emptyList<PrefetchImageModel>(),
        key1 = quickPicks
    ) {
        value = withContext(Dispatchers.Default) {
            quickPicks.orEmpty().mapNotNull { song ->
                song.song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    PrefetchImageModel(
                        stableKey = "quick_pick_${song.id}",
                        url = url,
                    )
                }
            }
        }
    }
    // ANR fix: same as above for Forgotten Favorites image prefetch candidates.
    val forgottenPrefetchModels by produceState(
        initialValue = emptyList<PrefetchImageModel>(),
        key1 = forgottenFavorites
    ) {
        value = withContext(Dispatchers.Default) {
            forgottenFavorites.orEmpty().mapNotNull { song ->
                song.song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    PrefetchImageModel(
                        stableKey = "forgotten_${song.id}",
                        url = url,
                    )
                }
            }
        }
    }
    // ANR fix: same off-main mapping for Keep Listening prefetch candidates.
    val keepListeningPrefetchModels by produceState(
        initialValue = emptyList<PrefetchImageModel>(),
        key1 = keepListening
    ) {
        value = withContext(Dispatchers.Default) {
            keepListening.orEmpty().mapNotNull { item ->
                item.toPrefetchImageModel("keep_listening")
            }
        }
    }
    val hasAnyHomeContent by remember(
        quickPicks,
        keepListening,
        forgottenFavorites,
        similarRecommendations,
        homePage,
        accountPlaylists,
    ) {
        derivedStateOf {
            quickPicks?.isNotEmpty() == true ||
                    keepListening?.isNotEmpty() == true ||
                    forgottenFavorites?.isNotEmpty() == true ||
                    similarRecommendations?.isNotEmpty() == true ||
                    homePage?.sections?.isNotEmpty() == true ||
                    accountPlaylists?.isNotEmpty() == true
        }
    }
    val continuation by rememberUpdatedState(homePage?.continuation)
    val quickPicksDistinct = remember(quickPicks) { quickPicks.orEmpty().distinctBy { it.id } }
    val shouldShowQuickPicksShimmer = isQuickPicksLoading && quickPicksDistinct.isEmpty()
    val forgottenFavoritesDistinct = remember(forgottenFavorites) { forgottenFavorites.orEmpty().distinctBy { it.id } }
    val accountPlaylistsDistinct = remember(accountPlaylists) { accountPlaylists.orEmpty().distinctBy { it.id } }
    val similarSections = remember(similarRecommendations) { similarRecommendations.orEmpty() }
    val homeSections = remember(homePage) { homePage?.sections.orEmpty() }
    val shouldShowRetryCard by remember(isLoading, isQuickPicksLoading, hasAnyHomeContent, networkError, homePage) {
        derivedStateOf {
            !isLoading && !isQuickPicksLoading && (
                !hasAnyHomeContent ||
                    (networkError != null && homePage == null)
                )
        }
    }
    val shouldShowHomeShimmer =
        isLoading || (homePage?.continuation != null && homeSections.isNotEmpty())
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            // ANR fix: avoid dispatching unnecessary scroll animations when already at top.
            if (lazylistState.firstVisibleItemIndex != 0 ||
                lazylistState.firstVisibleItemScrollOffset != 0
            ) {
                lazylistState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // ANR fix:
    // - Debounce/throttle scroll-end triggers so fast fling/layout changes do not dispatch repeated work.
    // - Emit continuation tokens directly and dedupe them, so each token is loaded once.
    // - `viewModel.loadMoreYouTubeItems` already executes network/data work on Dispatchers.IO in ViewModel.
    LaunchedEffect(lazylistState) {
        snapshotFlow {
            try {
                val layoutInfo = lazylistState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                if (totalItems > 0 && lastVisibleIndex >= totalItems - 3) continuation else null
            } catch (_: IllegalStateException) {
                null
            }
        }
            .debounce(250)
            .distinctUntilChanged()
            .filterNotNull()
            .collect { token ->
                viewModel.loadMoreYouTubeItems(token)
            }
    }



    if (selectedChip != null) {
        BackHandler {
            viewModel.toggleChip(selectedChip)
        }
    }

    LaunchedEffect(activeMediaId) {
        viewModel.updateQuickPicksPlaybackContext(activeMediaId)
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == activeMediaId) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == activeMediaId,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == activeAlbumId,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { navController.navigate("album/${it.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { navController.navigate("artist/${it.id}") },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> {}
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id == activeMediaId || item.id == activeAlbumId,
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier.combinedClickable(
                onClick = {
                    when (item) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue(
                                item.endpoint ?: com.metrolist.innertube.models.WatchEndpoint(
                                    videoId = item.id
                                ),
                                item.toMediaMetadata()
                            )
                        )

                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is AlbumItem -> YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                            is ArtistItem -> YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss
                            )
                            is PlaylistItem -> YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            )
        )
    }
    val quickPicksSongItem: @Composable (Song, Modifier) -> Unit = { originalSong, itemModifier ->
        // ANR fix:
        // - Keep a stable query flow per item id.
        // - Explicitly place Room upstream work on IO so item subscriptions in scrolling lists do not add
        //   avoidable main-thread load.
        val songFlow = remember(originalSong.id) {
            database.song(originalSong.id).flowOn(Dispatchers.IO)
        }
        val song by songFlow.collectAsStateWithLifecycle(initialValue = originalSong)
        val currentSong = song ?: originalSong
        SongListItem(
            song = currentSong,
            showInLibraryIcon = true,
            isActive = currentSong.id == activeMediaId,
            isPlaying = isPlaying,
            isSwipeable = false,
            trailingContent = {
                IconButton(
                    onClick = {
                        menuState.show {
                            SongMenu(
                                originalSong = currentSong,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            },
            modifier = itemModifier
                .combinedClickable(
                    onClick = {
                        if (currentSong.id == activeMediaId) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubeQueue.radio(
                                    currentSong.toMediaMetadata()
                                )
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = currentSong,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                )
        )
    }
    val forgottenFavoritesSongItem: @Composable (Song, Modifier) -> Unit = { originalSong, itemModifier ->
        // ANR fix: same IO-offloaded Room upstream for Forgotten Favorites row items.
        val songFlow = remember(originalSong.id) {
            database.song(originalSong.id).flowOn(Dispatchers.IO)
        }
        val song by songFlow.collectAsStateWithLifecycle(initialValue = originalSong)
        val currentSong = song ?: originalSong

        SongListItem(
            song = currentSong,
            showInLibraryIcon = true,
            isActive = currentSong.id == activeMediaId,
            isPlaying = isPlaying,
            isSwipeable = false,
            trailingContent = {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = currentSong,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null
                    )
                }
            },
            modifier = itemModifier
                .combinedClickable(
                    onClick = {
                        if (currentSong.id == activeMediaId) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                YouTubeQueue.radio(
                                    currentSong.toMediaMetadata()
                                )
                            )
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = currentSong,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                )
        )
    }

    val quickPicksResetToken = remember(quickPicks) {
        Triple(
            quickPicks?.size ?: 0,
            quickPicks?.firstOrNull()?.id,
            quickPicks?.lastOrNull()?.id,
        )
    }
    val forgottenResetToken = remember(forgottenFavorites) {
        Triple(
            forgottenFavorites?.size ?: 0,
            forgottenFavorites?.firstOrNull()?.id,
            forgottenFavorites?.lastOrNull()?.id,
        )
    }

    LaunchedEffect(quickPicksResetToken) {
        if (quickPicksLazyGridState.firstVisibleItemIndex != 0 ||
            quickPicksLazyGridState.firstVisibleItemScrollOffset != 0
        ) {
            quickPicksLazyGridState.scrollToItem(0)
        }
    }
    LaunchedEffect(forgottenResetToken) {
        if (forgottenFavoritesLazyGridState.firstVisibleItemIndex != 0 ||
            forgottenFavoritesLazyGridState.firstVisibleItemScrollOffset != 0
        ) {
            forgottenFavoritesLazyGridState.scrollToItem(0)
        }
    }

    if (canRunPrefetch) {
        PrefetchNextImages(
            state = quickPicksLazyGridState,
            imageModels = quickPicksPrefetchModels,
            prefetchCount = 4,
        )
        PrefetchNextImages(
            state = forgottenFavoritesLazyGridState,
            imageModels = forgottenPrefetchModels,
            prefetchCount = 4,
        )
        PrefetchNextImages(
            state = keepListeningGridState,
            imageModels = keepListeningPrefetchModels,
            prefetchCount = 4,
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
        val horizontalSystemBarsPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()

        val quickPicksSnapLayoutInfoProvider = remember(
            quickPicksLazyGridState,
            horizontalLazyGridItemWidthFactor,
        ) {
            com.metrolist.music.ui.utils.SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider = remember(
            forgottenFavoritesLazyGridState,
            horizontalLazyGridItemWidthFactor,
        ) {
            com.metrolist.music.ui.utils.SnapLayoutInfoProvider(
                lazyGridState = forgottenFavoritesLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val quickPicksFlingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider)
        val forgottenFavoritesFlingBehavior = rememberSnapFlingBehavior(forgottenFavoritesSnapLayoutInfoProvider)

        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            QuickPicksSection(
                quickPicksList = quickPicksDistinct,
                showShimmer = shouldShowQuickPicksShimmer,
                quickPicksLazyGridState = quickPicksLazyGridState,
                quickPicksFlingBehavior = quickPicksFlingBehavior,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding,
                horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                isSubscribed = isSubscribed,
                canComposeHomeAd = canComposeHomeAd,
                quickPicksItemContent = quickPicksSongItem
            )

            if (shouldShowRetryCard) {
                item(key = "home_retry", contentType = "retry") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = networkError ?: stringResource(R.string.error_no_internet),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = viewModel::refresh) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }

            KeepListeningSection(
                keepListeningList = keepListening.orEmpty(),
                keepListeningGridState = keepListeningGridState,
                keepListeningBaseRowHeight = keepListeningBaseRowHeight,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding,
                localGridItem = localGridItem
            )

            AccountPlaylistsSection(
                accountPlaylists = accountPlaylistsDistinct,
                accountName = accountName,
                accountImageUrl = url,
                navController = navController,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding,
                canRunPrefetch = canRunPrefetch,
                ytGridItem = ytGridItem
            )

            // FORGOTTEN FAVORITES
            forgottenFavoritesDistinct.takeIf { it.isNotEmpty() }?.let { forgottenList ->
                item(key = "forgotten_favorites_title", contentType = "section_title") {
                    NavigationTitle(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "forgotten_favorites_grid", contentType = "forgotten_favorites") {
                    val rows = min(4, forgottenList.size)
                    LazyHorizontalGrid(
                        state = forgottenFavoritesLazyGridState,
                        rows = GridCells.Fixed(rows),
                        contentPadding = horizontalSystemBarsPadding,
                        flingBehavior = forgottenFavoritesFlingBehavior,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * rows)
                    ) {
                        // Production crash fix:
                        // Backend/local aggregation can still return duplicate song IDs in edge cases.
                        // Compose requires keys to be unique within a Lazy container, so append index to keep
                        // stable uniqueness without removing items or changing visible ordering.
                        itemsIndexed(
                            items = forgottenList,
                            key = { index, song -> lazyItemKey("forgotten", song.id, index) },
                            contentType = { _, _ -> "song_list_item" }
                        ) { _, originalSong ->
                            forgottenFavoritesSongItem(
                                originalSong,
                                Modifier.width(horizontalLazyGridItemWidth)
                            )
                        }
                    }
                }
            }

            SimilarRecommendationsSection(
                similarSections = similarSections,
                navController = navController,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding,
                canRunPrefetch = canRunPrefetch,
                ytGridItem = ytGridItem
            )

            HomeDynamicSections(
                homeSections = homeSections,
                navController = navController,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding,
                canRunPrefetch = canRunPrefetch,
                ytGridItem = ytGridItem
            )

            HomeLoadingShimmerSection(
                shouldShowHomeShimmer = shouldShowHomeShimmer,
                horizontalSystemBarsPadding = horizontalSystemBarsPadding
            )

            // (Commented mood & genres block remains unchanged)

//            if (isMoodAndGenresLoading) {
//                item {
//                    ShimmerHost(
//                        modifier = Modifier.animateItem()
//                    ) {
//                        TextPlaceholder(
//                            height = 36.dp,
//                            modifier = Modifier
//                                .padding(vertical = 12.dp, horizontal = 12.dp)
//                                .width(250.dp),
//                        )
//
//                        repeat(4) {
//                            Row {
//                                repeat(2) {
//                                    TextPlaceholder(
//                                        height = MoodAndGenresButtonHeight * 0.7f,
//                                        shape = RoundedCornerShape(6.dp),
//                                        modifier = Modifier
//                                            .padding(horizontal = 12.dp)
//                                            .width(200.dp)
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
        }

        HideOnScrollFAB(
            visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
            lazyListState = lazylistState,
            icon = R.drawable.shuffle,
            onClick = {
                val local = when {
                    allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                    allLocalItems.isNotEmpty() -> true
                    else -> false
                }
                scope.launch {
                    // ANR fix: choose random candidates on Default to keep click-path main work minimal.
                    if (local && allLocalItems.isNotEmpty()) {
                        when (val luckyItem = withContext(Dispatchers.Default) { allLocalItems.random() }) {
                            is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                            is Album -> {
                                // ANR fix: keep album lookup off Main.
                                val albumWithSongs = withContext(Dispatchers.IO) {
                                    database.albumWithSongs(luckyItem.id).first()
                                }
                                albumWithSongs?.let {
                                    playerConnection.playQueue(LocalAlbumRadio(it))
                                }
                            }
                            is Artist -> {}
                            is Playlist -> {}
                        }
                    } else if (allYtItems.isNotEmpty()) {
                        when (val luckyItem = withContext(Dispatchers.Default) { allYtItems.random() }) {
                            is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                            is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                            is ArtistItem -> luckyItem.radioEndpoint?.let {
                                playerConnection.playQueue(YouTubeQueue(it))
                            }
                            is PlaylistItem -> luckyItem.playEndpoint?.let {
                                playerConnection.playQueue(YouTubeQueue(it))
                            }
                        }
                    }
                }
            }
        )

        Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.QuickPicksSection(
    quickPicksList: List<Song>,
    showShimmer: Boolean,
    quickPicksLazyGridState: LazyGridState,
    quickPicksFlingBehavior: FlingBehavior,
    horizontalSystemBarsPadding: PaddingValues,
    horizontalLazyGridItemWidth: Dp,
    isSubscribed: Boolean,
    canComposeHomeAd: Boolean,
    quickPicksItemContent: @Composable (Song, Modifier) -> Unit,
) {
    if (quickPicksList.isEmpty() && !showShimmer) return

    item(key = "quick_picks_section", contentType = "quick_picks_section") {
        NavigationTitle(
            title = stringResource(R.string.quick_picks),
            modifier = Modifier
                .animateItem()
        )
    }
    item(key = "quick_picks_grid", contentType = "quick_picks") {
        if (showShimmer) {
            ShimmerHost {
                LazyHorizontalGrid(
                    state = quickPicksLazyGridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = quickPicksFlingBehavior,
                    contentPadding = horizontalSystemBarsPadding,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ListItemHeight * 4)
                ) {
                    items(
                        count = QUICK_PICKS_PLACEHOLDER_ITEMS,
                        key = { index -> "quick_picks_placeholder_$index" },
                        contentType = { "quick_picks_placeholder" }
                    ) {
                        ListItemPlaceHolder(
                            modifier = Modifier.width(horizontalLazyGridItemWidth)
                        )
                    }
                }
            }
        } else {
            LazyHorizontalGrid(
                state = quickPicksLazyGridState,
                rows = GridCells.Fixed(4),
                flingBehavior = quickPicksFlingBehavior,
                contentPadding = horizontalSystemBarsPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ListItemHeight * 4)
            ) {
                itemsIndexed(
                    items = quickPicksList,
                    key = { index, song -> lazyItemKey("quick_picks", song.id, index) },
                    contentType = { _, _ -> "song_list_item" }
                ) { _, originalSong ->
                    quickPicksItemContent(
                        originalSong,
                        Modifier.width(horizontalLazyGridItemWidth)
                    )
                }
            }
        }
    }
    // Commented out per request: UnlimitedDownloadsBanner on Home screen.
    /*
    if (!isSubscribed) {
        item(key = "unlimited_downloads_banner") {
            UnlimitedDownloadsBanner(
                onClick = navigateToPremium,
                onUnlockClick = navigateToPremium,
                showAd = canComposeHomeAd,
                modifier = Modifier.animateItem()
            )
        }
    }
    */
    if (!isSubscribed && canComposeHomeAd) {
        item(key = "home_medium_rectangle_ad", contentType = "home_ad") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                contentAlignment = Alignment.Center
            ) {
                MediumRectangleAd(
                    modifier = Modifier.fillMaxWidth(),
                    adUnitId = MEDIUM_RECTANGLE_AD_UNIT_ID
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.KeepListeningSection(
    keepListeningList: List<LocalItem>,
    keepListeningGridState: LazyGridState,
    keepListeningBaseRowHeight: Dp,
    horizontalSystemBarsPadding: PaddingValues,
    localGridItem: @Composable (LocalItem) -> Unit,
) {
    if (keepListeningList.isEmpty()) return

    item(key = "keep_listening_title") {
        NavigationTitle(
            title = stringResource(R.string.keep_listening),
            modifier = Modifier.animateItem()
        )
    }
    item(key = "keep_listening_list") {
        val rows = if (keepListeningList.size > 6) 2 else 1
        LazyHorizontalGrid(
            state = keepListeningGridState,
            rows = GridCells.Fixed(rows),
            contentPadding = horizontalSystemBarsPadding,
            modifier = Modifier
                .fillMaxWidth()
                .height(keepListeningBaseRowHeight * rows)
        ) {
            itemsIndexed(
                items = keepListeningList,
                key = { index, item -> lazyItemKey("keep_listening", item.id, index) },
                contentType = { _, item -> localItemContentType(item) }
            ) { _, item ->
                localGridItem(item)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    accountName: String,
    accountImageUrl: String?,
    navController: NavController,
    horizontalSystemBarsPadding: PaddingValues,
    canRunPrefetch: Boolean,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    if (accountPlaylists.isEmpty()) return

    item(key = "account_playlists_title", contentType = "section_title") {
        NavigationTitle(
            label = stringResource(R.string.your_youtube_playlists),
            title = accountName,
            thumbnail = {
                if (accountImageUrl != null) {
                    ProgressiveNetworkImage(
                        model = ProgressiveImageModel(
                            stableKey = "account_avatar_$accountImageUrl",
                            url = accountImageUrl,
                        ),
                        contentDescription = null,
                        placeholderResId = R.drawable.person,
                        errorResId = R.drawable.person,
                        fallbackResId = R.drawable.person,
                        contentScale = ContentScale.Crop,
                        thumbnailSizePx = 96,
                        mediumSizePx = 320,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.person),
                        contentDescription = null,
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                }
            },
            onClick = { navController.navigate("account") },
            modifier = Modifier.animateItem()
        )
    }
    item(key = "account_playlists_row", contentType = "account_playlists") {
        val rowState = rememberLazyListState()
        val rowPrefetchModels = remember(accountPlaylists) {
            accountPlaylists.mapNotNull { playlist ->
                playlist.toPrefetchImageModel("account_playlist")
            }
        }
        LazyRow(
            state = rowState,
            contentPadding = horizontalSystemBarsPadding
        ) {
            itemsIndexed(
                items = accountPlaylists,
                key = { index, item -> lazyItemKey("account_playlist", item.id, index) },
                contentType = { _, item -> ytItemContentType(item) }
            ) { _, item ->
                ytGridItem(item)
            }
        }

        if (canRunPrefetch) {
            PrefetchNextImages(
                state = rowState,
                imageModels = rowPrefetchModels,
                prefetchCount = 4,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.SimilarRecommendationsSection(
    similarSections: List<SimilarRecommendation>,
    navController: NavController,
    horizontalSystemBarsPadding: PaddingValues,
    canRunPrefetch: Boolean,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    similarSections.forEachIndexed { index, section ->
        val sectionKey = "similar_${section.title.id}_$index"
        item(key = "${sectionKey}_title", contentType = "section_title") {
            NavigationTitle(
                label = stringResource(R.string.similar_to),
                title = section.title.title,
                thumbnail = section.title.thumbnailUrl?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (section.title is Artist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                        ProgressiveNetworkImage(
                            model = ProgressiveImageModel(
                                stableKey = "similar_title_${section.title.id}",
                                url = thumbnailUrl,
                            ),
                            contentDescription = null,
                            placeholderResId = if (section.title is Artist) R.drawable.artist else R.drawable.album_search,
                            errorResId = if (section.title is Artist) R.drawable.artist else R.drawable.album_search,
                            fallbackResId = if (section.title is Artist) R.drawable.artist else R.drawable.album_search,
                            thumbnailSizePx = 96,
                            mediumSizePx = 360,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = {
                    when (section.title) {
                        is Song -> navController.navigate("album/${section.title.album!!.id}")
                        is Album -> navController.navigate("album/${section.title.id}")
                        is Artist -> navController.navigate("artist/${section.title.id}")
                        is Playlist -> {}
                    }
                },
                modifier = Modifier.animateItem()
            )
        }
        item(key = "${sectionKey}_row", contentType = "similar_row") {
            val rowState = rememberLazyListState()
            val rowPrefetchModels = remember(section.items) {
                section.items.mapNotNull { item ->
                    item.toPrefetchImageModel("similar_${section.title.id}")
                }
            }
            LazyRow(
                state = rowState,
                contentPadding = horizontalSystemBarsPadding
            ) {
                itemsIndexed(
                    items = section.items,
                    key = { itemIndex, item -> lazyItemKey(sectionKey, item.id, itemIndex) },
                    contentType = { _, item -> ytItemContentType(item) }
                ) { _, item ->
                    ytGridItem(item)
                }
            }

            if (canRunPrefetch) {
                PrefetchNextImages(
                    state = rowState,
                    imageModels = rowPrefetchModels,
                    prefetchCount = 4,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.HomeDynamicSections(
    homeSections: List<HomePage.Section>,
    navController: NavController,
    horizontalSystemBarsPadding: PaddingValues,
    canRunPrefetch: Boolean,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    homeSections.forEachIndexed { index, sec ->
        val sectionKey = "home_${sec.endpoint?.browseId ?: sec.title}_$index"
        item(key = "${sectionKey}_title", contentType = "section_title") {
            NavigationTitle(
                title = sec.title,
                label = sec.label,
                thumbnail = sec.thumbnail?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (sec.endpoint?.isArtistEndpoint == true) CircleShape
                            else RoundedCornerShape(ThumbnailCornerRadius)
                        ProgressiveNetworkImage(
                            model = ProgressiveImageModel(
                                stableKey = "home_section_${sec.endpoint?.browseId ?: sec.title}",
                                url = thumbnailUrl,
                            ),
                            contentDescription = null,
                            placeholderResId = if (sec.endpoint?.isArtistEndpoint == true) R.drawable.artist else R.drawable.album_search,
                            errorResId = if (sec.endpoint?.isArtistEndpoint == true) R.drawable.artist else R.drawable.album_search,
                            fallbackResId = if (sec.endpoint?.isArtistEndpoint == true) R.drawable.artist else R.drawable.album_search,
                            thumbnailSizePx = 96,
                            mediumSizePx = 360,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = sec.endpoint?.browseId?.let { browseId ->
                    {
                        if (browseId == "FEmusic_moods_and_genres")
                            navController.navigate("mood_and_genres")
                        else
                            navController.navigate("browse/$browseId")
                    }
                },
                modifier = Modifier.animateItem()
            )
        }
        item(key = "${sectionKey}_row", contentType = "home_row") {
            val rowState = rememberLazyListState()
            val rowPrefetchModels = remember(sec.items) {
                sec.items.mapNotNull { item ->
                    item.toPrefetchImageModel("home_${sec.endpoint?.browseId ?: sec.title}")
                }
            }
            LazyRow(
                state = rowState,
                contentPadding = horizontalSystemBarsPadding
            ) {
                itemsIndexed(
                    items = sec.items,
                    key = { itemIndex, item -> lazyItemKey(sectionKey, item.id, itemIndex) },
                    contentType = { _, item -> ytItemContentType(item) }
                ) { _, item ->
                    ytGridItem(item)
                }
            }

            if (canRunPrefetch) {
                PrefetchNextImages(
                    state = rowState,
                    imageModels = rowPrefetchModels,
                    prefetchCount = 4,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.HomeLoadingShimmerSection(
    shouldShowHomeShimmer: Boolean,
    horizontalSystemBarsPadding: PaddingValues
) {
    if (!shouldShowHomeShimmer) return
    item(key = "home_shimmer", contentType = "loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalSystemBarsPadding)
                .padding(16.dp)
                .animateItem(),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    }
}

private fun localItemContentType(item: LocalItem): String =
    when (item) {
        is Song -> "local_song"
        is Album -> "local_album"
        is Artist -> "local_artist"
        is Playlist -> "local_playlist"
    }

private fun ytItemContentType(item: YTItem): String =
    when (item) {
        is SongItem -> "yt_song"
        is AlbumItem -> "yt_album"
        is ArtistItem -> "yt_artist"
        is PlaylistItem -> "yt_playlist"
    }

private fun lazyItemKey(prefix: String, id: String, index: Int): String = "${prefix}_${id}_$index"

private fun LocalItem.toPrefetchImageModel(prefix: String): PrefetchImageModel? {
    val (stableKey, url) = when (this) {
        is Song -> "song_$id" to song.thumbnailUrl
        is Album -> "album_$id" to album.thumbnailUrl
        is Artist -> "artist_$id" to artist.thumbnailUrl
        is Playlist -> "playlist_$id" to null
    }
    val nonBlankUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return PrefetchImageModel(
        stableKey = "${prefix}_$stableKey",
        url = nonBlankUrl,
    )
}

private fun YTItem.toPrefetchImageModel(prefix: String): PrefetchImageModel? {
    val nonBlankUrl = thumbnail?.takeIf { it.isNotBlank() } ?: return null
    return PrefetchImageModel(
        stableKey = "${prefix}_${id}",
        url = nonBlankUrl,
    )
}

@Composable
private fun UnlimitedDownloadsBanner(
    onClick: () -> Unit,
    onUnlockClick: () -> Unit,
    showAd: Boolean,
    modifier: Modifier = Modifier
) {
    val onCardClick by rememberUpdatedState(onClick)
    val onUnlockCtaClick by rememberUpdatedState(onUnlockClick)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp)
    ) {
        val compact = maxWidth < 360.dp
        val tileSize = if (compact) 42.dp else 46.dp
        val crownSize = if (compact) 22.dp else 24.dp
        val titleSize = if (compact) 12.5.sp else 13.2.sp
        val subtitleSize = if (compact) 10.8.sp else 11.4.sp
        val buttonHeight = if (compact) 34.dp else 36.dp
        val buttonWidth = if (compact) 86.dp else 92.dp
        val horizontalPadding = if (compact) 10.dp else 12.dp
        val verticalPadding = if (compact) 10.dp else 11.dp

        Surface(
            onClick = onCardClick,
            shape = RoundedCornerShape(11.dp),
            color = Color(0xFF181C22),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Original banner content (icon, text, button)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(tileSize)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF262B35))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.crownpro),
                            contentDescription = stringResource(R.string.unlimited_downloads_banner_crown_content_description),
                            modifier = Modifier.size(crownSize)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (compact) 9.dp else 10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.unlimited_downloads_banner_title),
                            fontSize = titleSize,
                            lineHeight = if (compact) 15.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.96f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.unlimited_downloads_banner_subtitle),
                            fontSize = subtitleSize,
                            lineHeight = if (compact) 13.sp else 14.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))

                    Surface(
                        onClick = onUnlockCtaClick,
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFFB000),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .width(buttonWidth)
                            .height(buttonHeight)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.unlimited_downloads_banner_unlock),
                                fontSize = if (compact) 12.8.sp else 13.4.sp,
                                lineHeight = if (compact) 14.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF131313),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Medium Rectangle Ad below the banner content
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showAd) {
                        MediumRectangleAd(
                            modifier = Modifier
                                .fillMaxWidth(),
                            adUnitId = MEDIUM_RECTANGLE_AD_UNIT_ID
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                    }
                }
            }
        }
    }
}
