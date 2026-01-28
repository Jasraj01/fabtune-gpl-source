package com.metrolist.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import com.metrolist.music.ui.ads.AdCooldownManager
import com.metrolist.music.ui.ads.SmallBannerAd
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
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
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
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
    // Subscription check (kept identical)
    val subscriptionState = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                subscriptionState.value = customerInfo.entitlements.active.containsKey("premium")
            }
            override fun onError(error: PurchasesError) {
                subscriptionState.value = false
            }
        })
    }
    val isSubscribed = subscriptionState.value

    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // ViewModel state
    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()
    val selectedChip by viewModel.selectedChip.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isMoodAndGenresLoading = isLoading && explorePage?.moodAndGenres == null
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    // Stable grid states
    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()
    val keepListeningGridState = rememberLazyGridState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")

    val shouldShowWrappedCard by viewModel.showWrappedCard.collectAsState()
    val wrappedState by viewModel.wrappedManager.state.collectAsState()
    val isWrappedDataReady = wrappedState.isDataReady

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
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val wrappedDismissed by backStackEntry?.savedStateHandle?.getStateFlow("wrapped_seen", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }

    val context = LocalContext.current
    val canShowBanner = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val doc = FirebaseFirestore.getInstance()
            .collection("ad_control")
            .document("global")
            .get()
            .await()

        val showAds = doc.getBoolean("show_ads") ?: true

        canShowBanner.value = if (showAds) {
            true // normal behavior
        } else {
            // apply local 12-hour cooldown
            AdCooldownManager.canShow(context)
        }
    }


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

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // Infinite loading trigger (derived state â†’ fewer recompositions)
    LaunchedEffect(lazylistState) {
        snapshotFlow {
            try {
                lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            } catch (_: IllegalStateException) {
                null
            }
        }.distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalItems = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 3) {
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

    LaunchedEffect(quickPicks) { quickPicksLazyGridState.scrollToItem(0) }
    LaunchedEffect(forgottenFavorites) { forgottenFavoritesLazyGridState.scrollToItem(0) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize() // smoother when player insets change
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

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

        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
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
                                        val bbhFont = try {
                                            FontFamily(Font(R.font.bbh_bartle_regular))
                                        } catch (e: Exception) {
                                            FontFamily.Default
                                        }
                                        Text(
                                            text = stringResource(R.string.wrapped_ready_title),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontFamily = bbhFont,
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

                item {
                    if (!isSubscribed) {
                        PromoBanner(navController = navController)
                    }
                    NavigationTitle(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier
                            .animateItem()
                    )
                }
                item {
                    val quickPicksDistinct = remember(quickPicksList) { quickPicksList.distinctBy { it.id } }
                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4)
                    ) {
                        items(
                            items = quickPicksDistinct,
                            key = { it.id },
                            contentType = { "song_list_item" }
                        ) { originalSong ->
                            // keep database flow in the item, but don't cause full recomposition storms
                            val song by database.song(originalSong.id)
                                .collectAsState(initial = originalSong)
                            val songStable by rememberUpdatedState(song)

                            SongListItem(
                                song = songStable!!,
                                showInLibraryIcon = true,
                                isActive = songStable!!.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songStable!!,
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
                                            if (songStable!!.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(songStable!!.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songStable!!,
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
                if (!isSubscribed && canShowBanner.value) {
                    item(key = "home_banner_ad") {
                        SmallBannerAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            onAdLoadedOnce = {
                                AdCooldownManager.markShown(context)
                                canShowBanner.value = false
                            }
                        )
                    }
                }
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
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                (currentGridHeight  + with(LocalDensity.current) {
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
                item {
                    NavigationTitle(
                        label = stringResource(R.string.your_youtube_playlists),
                        title = accountName,
                        thumbnail = {
                            val ctx = LocalContext.current
                            if (url != null) {
                                val rememberedRequest = remember(url) {
                                    ImageRequest.Builder(ctx)
                                        .data(url)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .diskCacheKey(url)
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = rememberedRequest,
                                    placeholder = painterResource(id = R.drawable.person),
                                    error = painterResource(id = R.drawable.person),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
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
                item {
                    val rowState = rememberLazyListState()
                    val accountPlaylistsDistinct = remember(accPlaylists) { accPlaylists.distinctBy { it.id } }
                    LazyRow(
                        state = rowState,
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues()
                    ) {
                        items(
                            items = accountPlaylistsDistinct,
                            key = { it.id },
                            contentType = { it::class }
                        ) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            // FORGOTTEN FAVORITES
            forgottenFavoritesState?.takeIf { it.isNotEmpty() }?.let { forgottenList ->
                item {
                    NavigationTitle(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem()
                    )
                }
                item {
                    val rows = min(4, forgottenList.size)
                    val forgottenDistinct = remember(forgottenList) { forgottenList.distinctBy { it.id } }
                    LazyHorizontalGrid(
                        state = forgottenFavoritesLazyGridState,
                        rows = GridCells.Fixed(rows),
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        flingBehavior = rememberSnapFlingBehavior(forgottenFavoritesSnapLayoutInfoProvider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * rows)
                    ) {
                        items(
                            items = forgottenDistinct,
                            key = { it.id },
                            contentType = { "song_list_item" }
                        ) { originalSong ->
                            val song by database.song(originalSong.id)
                                .collectAsState(initial = originalSong)
                            val songStable by rememberUpdatedState(song)

                            SongListItem(
                                song = songStable!!,
                                showInLibraryIcon = true,
                                isActive = songStable!!.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songStable!!,
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
                                            if (songStable!!.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(songStable!!.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = songStable!!,
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
            similarRecommendationsState?.forEach { section ->
                item {
                    NavigationTitle(
                        label = stringResource(R.string.similar_to),
                        title = section.title.title,
                        thumbnail = section.title.thumbnailUrl?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (section.title is Artist) CircleShape else RoundedCornerShape(ThumbnailCornerRadius)
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
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
                item {
                    val rowState = rememberLazyListState()
                    LazyRow(
                        state = rowState,
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues()
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
                }
            }

            // HOMEPAGE SECTIONS
            homePageState?.sections?.forEach { sec ->
                item {
                    NavigationTitle(
                        title = sec.title,
                        label = sec.label,
                        thumbnail = sec.thumbnail?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (sec.endpoint?.isArtistEndpoint == true) CircleShape
                                    else RoundedCornerShape(ThumbnailCornerRadius)
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
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
                item {
                    val rowState = rememberLazyListState()
                    LazyRow(
                        state = rowState,
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues()
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
                }
            }

            // SHIMMER
            if (isLoading || (homePageState?.continuation != null && homePageState?.sections?.isNotEmpty() == true)) {
                item {
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
                            contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                        ) {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }

            // (Commented mood & genres block remains unchanged)

            if (isMoodAndGenresLoading) {
                item {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .width(250.dp),
                        )

                        repeat(4) {
                            Row {
                                repeat(2) {
                                    TextPlaceholder(
                                        height = MoodAndGenresButtonHeight * 0.7f,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .width(200.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
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