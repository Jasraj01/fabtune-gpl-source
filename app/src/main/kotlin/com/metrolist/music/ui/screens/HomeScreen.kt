package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.firestore.FirebaseFirestore
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.SmallGridThumbnailHeight
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
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
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.playback.queues.YouTubeAlbumRadio
import com.metrolist.music.playback.queues.YouTubeQueue
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
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
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

    // ViewModel state
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
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

    val shouldShowWrappedCard by viewModel.showWrappedCard.collectAsStateWithLifecycle()
    val wrappedState by viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    val isWrappedDataReady = wrappedState.isDataReady
    val wrappedTitleFontFamily = remember {
        runCatching { FontFamily(Font(R.font.bbh_bartle_regular)) }
            .getOrDefault(FontFamily.Default)
    }

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val currentGridHeight = if (gridItemSize == GridItemSize.BIG) GridThumbnailHeight else SmallGridThumbnailHeight
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle(false)

    val wrappedDismissed by backStackEntry?.savedStateHandle?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }

    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            scope.launch {
                snackbarHostState.showSnackbar("Found in Settings > Content")
            }
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false) // Reset the value
        }
    }

    // ---------- performance: stabilize frequently-changing references ----------
    val homePageState by rememberUpdatedState(homePage)
    val quickPicksState by rememberUpdatedState(quickPicks)
    val forgottenFavoritesState by rememberUpdatedState(forgottenFavorites)
    val keepListeningState by rememberUpdatedState(keepListening)
    val similarRecommendationsState by rememberUpdatedState(similarRecommendations)
    val accountPlaylistsState by rememberUpdatedState(accountPlaylists)

    val quickPicksPrefetchModels = remember(quickPicksState) {
        quickPicksState.orEmpty().mapNotNull { song ->
            song.song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                PrefetchImageModel(
                    stableKey = "quick_pick_${song.id}",
                    url = url,
                )
            }
        }
    }
    val forgottenPrefetchModels = remember(forgottenFavoritesState) {
        forgottenFavoritesState.orEmpty().mapNotNull { song ->
            song.song.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { url ->
                PrefetchImageModel(
                    stableKey = "forgotten_${song.id}",
                    url = url,
                )
            }
        }
    }
    val keepListeningPrefetchModels = remember(keepListeningState) {
        keepListeningState.orEmpty().mapNotNull { item ->
            item.toPrefetchImageModel("keep_listening")
        }
    }
    val hasAnyHomeContent by remember(
        quickPicksState,
        keepListeningState,
        forgottenFavoritesState,
        similarRecommendationsState,
        homePageState,
        accountPlaylistsState,
    ) {
        derivedStateOf {
            quickPicksState?.isNotEmpty() == true ||
                keepListeningState?.isNotEmpty() == true ||
                forgottenFavoritesState?.isNotEmpty() == true ||
                similarRecommendationsState?.isNotEmpty() == true ||
                homePageState?.sections?.isNotEmpty() == true ||
                accountPlaylistsState?.isNotEmpty() == true
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // Infinite loading trigger (derived state -> fewer recompositions)
    LaunchedEffect(lazylistState) {
        snapshotFlow {
            try {
                val layoutInfo = lazylistState.layoutInfo
                layoutInfo.visibleItemsInfo.lastOrNull()?.index to layoutInfo.totalItemsCount
            } catch (_: IllegalStateException) {
                null to 0
            }
        }.distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItems) ->
                if (totalItems > 0 && lastVisibleIndex != null && lastVisibleIndex >= totalItems - 3) {
                    viewModel.loadMoreYouTubeItems(homePageState?.continuation)
                }
            }
    }



    if (selectedChip != null) {
        BackHandler {
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
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
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
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
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
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

    val quickPicksResetToken = remember(quickPicksState) {
        Triple(
            quickPicksState?.size ?: 0,
            quickPicksState?.firstOrNull()?.id,
            quickPicksState?.lastOrNull()?.id,
        )
    }
    val forgottenResetToken = remember(forgottenFavoritesState) {
        Triple(
            forgottenFavoritesState?.size ?: 0,
            forgottenFavoritesState?.firstOrNull()?.id,
            forgottenFavoritesState?.lastOrNull()?.id,
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

        val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
            com.metrolist.music.ui.utils.SnapLayoutInfoProvider(
                lazyGridState = quickPicksLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                }
            )
        }
        val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
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
            if (!isLoading && !hasAnyHomeContent) {
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

            // QUICK PICKS
            quickPicksState?.takeIf { it.isNotEmpty() }?.let { quickPicksList ->

                item(key = "wrapped_card") {
                    AnimatedVisibility(visible = shouldShowWrappedCard) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isWrappedDataReady) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.wrapped_ready_title),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontFamily = wrappedTitleFontFamily,
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.wrapped_ready_subtitle),
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = {
                                            navController.navigate("wrapped")
                                        }) {
                                            Text("Open")
                                        }
                                    }
                                } else {
                                    ContainedLoadingIndicator()
                                }
                            }
                        }
                    }
                }

                item(key = "quick_picks_title", contentType = "section_title") {
                    NavigationTitle(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier
                            .animateItem()
                    )
                }
                item(key = "quick_picks_grid", contentType = "quick_picks") {
                    val quickPicksDistinct = remember(quickPicksList) { quickPicksList.distinctBy { it.id } }
                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = quickPicksFlingBehavior,
                        contentPadding = horizontalSystemBarsPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4)
                    ) {
                        items(
                            items = quickPicksDistinct,
                            key = { it.id },
                            contentType = { "song_list_item" }
                        ) { originalSong ->
                            // Remember the Flow instance per item id to avoid re-allocating query wrappers.
                            val songFlow = remember(originalSong.id) { database.song(originalSong.id) }
                            val song by songFlow.collectAsStateWithLifecycle(initialValue = originalSong)
                            val currentSong = song ?: originalSong

                            SongListItem(
                                song = currentSong,
                                showInLibraryIcon = true,
                                isActive = currentSong.id == mediaMetadata?.id,
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
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .combinedClickable(
                                        onClick = {
                                            if (currentSong.id == mediaMetadata?.id) {
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
                    }
                }
                if (!isSubscribed) {
                    item(key = "unlimited_downloads_banner") {
                        UnlimitedDownloadsBanner(
                            onClick = {
                                navController.navigate("premium") {
                                    launchSingleTop = true
                                }
                            },
                            onUnlockClick = {
                                navController.navigate("premium") {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
//                if (!isSubscribed) {
//                    item {
//                        SmallBannerAd(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(vertical = 8.dp),
//                        )
//                    }
//                }
            }

            // KEEP LISTENING
            keepListeningState?.takeIf { it.isNotEmpty() }?.let { keepListeningList ->
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
                            .height(
                                (currentGridHeight + with(LocalDensity.current) {
                                    MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                                }) * rows
                            )
                    ) {
                        items(
                            items = keepListeningList,
                            key = {
                                when (it) {
                                    is Song -> it.id
                                    is Album -> it.id
                                    is Artist -> it.id
                                    is Playlist -> it.id
                                }
                            },
                            contentType = { it::class }
                        ) {
                            localGridItem(it)
                        }
                    }
                }
            }

            // ACCOUNT PLAYLISTS
            accountPlaylistsState?.takeIf { it.isNotEmpty() }?.let { accPlaylists ->
                item(key = "account_playlists_title", contentType = "section_title") {
                    NavigationTitle(
                        label = stringResource(R.string.your_youtube_playlists),
                        title = accountName,
                        thumbnail = {
                            if (url != null) {
                                ProgressiveNetworkImage(
                                    model = ProgressiveImageModel(
                                        stableKey = "account_avatar_$url",
                                        url = url,
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
                    val accountPlaylistsDistinct = remember(accPlaylists) { accPlaylists.distinctBy { it.id } }
                    val rowPrefetchModels = remember(accountPlaylistsDistinct) {
                        accountPlaylistsDistinct.mapNotNull { playlist ->
                            playlist.toPrefetchImageModel("account_playlist")
                        }
                    }
                    LazyRow(
                        state = rowState,
                        contentPadding = horizontalSystemBarsPadding
                    ) {
                        items(
                            items = accountPlaylistsDistinct,
                            key = { it.id },
                            contentType = { it::class }
                        ) { item ->
                            ytGridItem(item)
                        }
                    }

                    PrefetchNextImages(
                        state = rowState,
                        imageModels = rowPrefetchModels,
                        prefetchCount = 4,
                    )
                }
            }

            // FORGOTTEN FAVORITES
            forgottenFavoritesState?.takeIf { it.isNotEmpty() }?.let { forgottenList ->
                item(key = "forgotten_favorites_title", contentType = "section_title") {
                    NavigationTitle(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "forgotten_favorites_grid", contentType = "forgotten_favorites") {
                    val rows = min(4, forgottenList.size)
                    val forgottenDistinct = remember(forgottenList) { forgottenList.distinctBy { it.id } }
                    LazyHorizontalGrid(
                        state = forgottenFavoritesLazyGridState,
                        rows = GridCells.Fixed(rows),
                        contentPadding = horizontalSystemBarsPadding,
                        flingBehavior = forgottenFavoritesFlingBehavior,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * rows)
                    ) {
                        items(
                            items = forgottenDistinct,
                            key = { it.id },
                            contentType = { "song_list_item" }
                        ) { originalSong ->
                            val songFlow = remember(originalSong.id) { database.song(originalSong.id) }
                            val song by songFlow.collectAsStateWithLifecycle(initialValue = originalSong)
                            val currentSong = song ?: originalSong

                            SongListItem(
                                song = currentSong,
                                showInLibraryIcon = true,
                                isActive = currentSong.id == mediaMetadata?.id,
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
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .combinedClickable(
                                        onClick = {
                                            if (currentSong.id == mediaMetadata?.id) {
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
                    }
                }
            }

            // SIMILAR RECOMMENDATIONS
            similarRecommendationsState?.forEachIndexed { index, section ->
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
                        items(
                            items = section.items,
                            key = { item ->
                                when (item) {
                                    is SongItem -> item.id
                                    is AlbumItem -> item.id
                                    is ArtistItem -> item.id
                                    is PlaylistItem -> item.id
                                }
                            },
                            contentType = { item -> item::class }
                        ) { item ->
                            ytGridItem(item)
                        }
                    }

                    PrefetchNextImages(
                        state = rowState,
                        imageModels = rowPrefetchModels,
                        prefetchCount = 4,
                    )
                }
            }

            // HOMEPAGE SECTIONS
            homePageState?.sections?.forEachIndexed { index, sec ->
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
                        items(
                            items = sec.items,
                            key = { item ->
                                when (item) {
                                    is SongItem -> item.id
                                    is AlbumItem -> item.id
                                    is ArtistItem -> item.id
                                    is PlaylistItem -> item.id
                                }
                            },
                            contentType = { item -> item::class }
                        ) { item ->
                            ytGridItem(item)
                        }
                    }

                    PrefetchNextImages(
                        state = rowState,
                        imageModels = rowPrefetchModels,
                        prefetchCount = 4,
                    )
                }
            }

            // SHIMMER
            if (isLoading || (homePageState?.continuation != null && homePageState?.sections?.isNotEmpty() == true)) {
                item(key = "home_shimmer", contentType = "loading") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .width(250.dp),
                        )
                        val rowState = rememberLazyListState()
                        LazyRow(
                            state = rowState,
                            contentPadding = horizontalSystemBarsPadding,
                        ) {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }

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
                scope.launch(Dispatchers.Main) {
                    if (local) {
                        when (val luckyItem = allLocalItems.random()) {
                            is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                            is Album -> {
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
                    } else {
                        when (val luckyItem = allYtItems.random()) {
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
    modifier: Modifier = Modifier
) {
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
            onClick = onClick,
            shape = RoundedCornerShape(11.dp),
            color = Color(0xFF181C22),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                    onClick = onUnlockClick,
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
        }
    }
}
