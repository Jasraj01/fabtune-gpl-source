package com.metrolist.music.ui.screens.artist

/*
APPLE MUSIC-GRADE PERFORMANCE OPTIMIZATION REPORT

Bottlenecks Identified:
- Song rows were rendered via a single non-lazy Column inside LazyColumn.
- Scroll-end pagination signal could fire repeatedly during layout churn.

Optimizations Applied:
- Restored true lazy virtualization with itemsIndexed song rendering.
- Added debounced/distinct load-more triggers gated by continuation presence.
- Switched UI state collection to lifecycle-aware collection.

Main-thread work reduced by:
- Avoiding eager composition/layout of the entire song list.

Recomposition reductions:
- Fewer full-list recompositions from non-lazy container updates.

Scroll performance improvements:
- Stable pagination trigger behavior under fast fling.

Image pipeline improvements:
- N/A in this file.

Expected impact on low-end devices:
- Smoother long-list scrolling and lower dropped-frame risk.
*/

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.utils.rememberEnumPreference
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.ArtistItemsViewModel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter



//this is more option in artist screen leading to here
//artistscreen
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable

fun ArtistItemsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val title by viewModel.title.collectAsStateWithLifecycle()
    val itemsPage by viewModel.itemsPage.collectAsStateWithLifecycle()
    val hasContinuation by remember(itemsPage) {
        derivedStateOf { itemsPage?.continuation != null }
    }
    val distinctItems = remember(itemsPage?.items) {
        itemsPage?.items.orEmpty().distinctBy { it.id }
    }

    LaunchedEffect(lazyListState, hasContinuation) {
        if (!hasContinuation) return@LaunchedEffect
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
            .distinctUntilChanged()
            .debounce(120)
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    LaunchedEffect(lazyGridState, hasContinuation) {
        if (!hasContinuation) return@LaunchedEffect
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
            .distinctUntilChanged()
            .debounce(120)
            .filter { it }
            .collect { viewModel.loadMore() }
    }

    if (itemsPage == null) {
        ShimmerHost(
            modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        ) {
            repeat(8) {
                ListItemPlaceHolder()
            }
        }
    }

    if (distinctItems.firstOrNull() is SongItem) {
        val hasLoadingItem = itemsPage?.continuation != null

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(key = "top_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }

            itemsIndexed(
                items = distinctItems,
                key = { index, item -> "artist_song_${item.id}_$index" },
                contentType = { _, _ -> "song_item" },
            ) { index, item ->
                val isFirst = index == 0
                val isLast = index == distinctItems.size - 1 && !hasLoadingItem
                val isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(ListItemHeight)
                        .clip(
                            RoundedCornerShape(
                                topStart = if (isFirst) 20.dp else 0.dp,
                                topEnd = if (isFirst) 20.dp else 0.dp,
                                bottomStart = if (isLast) 20.dp else 0.dp,
                                bottomEnd = if (isLast) 20.dp else 0.dp
                            )
                        )
                        .background(
                            if (isActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                ) {
                    YouTubeListItem(
                        item = item,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        when (item) {
                                            is SongItem ->
                                                YouTubeSongMenu(
                                                    song = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is AlbumItem ->
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is ArtistItem ->
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is PlaylistItem ->
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                        item.toMediaMetadata()
                                                    ),
                                                )
                                            }
                                        }

                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        when (item) {
                                            is SongItem ->
                                                YouTubeSongMenu(
                                                    song = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is AlbumItem ->
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is ArtistItem ->
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is PlaylistItem ->
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                        }
                                    }
                                }
                            ),
                    )
                }

                // Keep original row spacing without forcing eager composition.
                if (!isLast) {
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }

            if (hasLoadingItem) {
                item(key = "loading") {
                    ShimmerHost {
                        repeat(3) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    } else {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            items(
                items = distinctItems,
                key = { it.id }
            ) { item ->
                YouTubeGridItem(
                    item = item,
                    isActive = when (item) {
                        is SongItem -> mediaMetadata?.id == item.id
                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                        else -> false
                    },
                    isPlaying = isPlaying,
                    fillMaxWidth = true,
                    coroutineScope = coroutineScope,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem -> playerConnection.playQueue(
                                        YouTubeQueue(
                                            item.endpoint ?: WatchEndpoint(videoId = item.id),
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
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            }
                        )
                        .animateItem()
                )
            }

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost(Modifier.animateItem()) {
                        GridItemPlaceHolder(fillMaxWidth = true)
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}
