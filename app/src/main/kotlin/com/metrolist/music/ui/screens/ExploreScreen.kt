package com.metrolist.music.ui.screens

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.models.*
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.image.PrefetchImageModel
import com.metrolist.music.ui.component.image.PrefetchNextImages
import com.metrolist.music.ui.component.shimmer.GridItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.SnapLayoutInfoProvider
import com.metrolist.music.viewmodels.ChartsViewModel
import com.metrolist.music.viewmodels.ExploreViewModel
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val explorePage by exploreViewModel.explorePage.collectAsStateWithLifecycle()
    val moodAndGenres by exploreViewModel.moodAndGenresGrouped.collectAsStateWithLifecycle()
    val moodThumbnails by exploreViewModel.moodThumbnails.collectAsStateWithLifecycle()
    val isExploreLoading by exploreViewModel.isLoading.collectAsStateWithLifecycle()
    val exploreError by exploreViewModel.error.collectAsStateWithLifecycle()

    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val isChartsLoading by chartsViewModel.isLoading.collectAsStateWithLifecycle()
    val chartsError by chartsViewModel.error.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val topVideosRowState = rememberLazyListState()

    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = remember(localConfiguration.orientation) {
        if (localConfiguration.orientation == ORIENTATION_LANDSCAPE) 3 else 2
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop by backStackEntry?.savedStateHandle
        ?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val spotifyLikeColors = remember {
        listOf(
            Color(0xFFAF2896), Color(0xFF1E3264), Color(0xFF477D95),
            Color(0xFFB49BC8), Color(0xFF148A08), Color(0xFFD84000),
            Color(0xFFE8115B), Color(0xFF7D4CDB), Color(0xFFF59B23),
            Color(0xFF3E7F78), Color(0xFFBA5D07), Color(0xFF008069),
        )
    }
    val chartSections = remember(chartsPage) { chartsPage?.sections.orEmpty() }
    val chartSectionsWithoutVideos = remember(chartSections) {
        chartSections.filter { it.title != "Top music videos" }
    }
    val topVideosSection = remember(chartSections) {
        chartSections.firstOrNull { it.title == "Top music videos" }
    }
    val chartSongsBySection = remember(chartSectionsWithoutVideos) {
        chartSectionsWithoutVideos.map { section ->
            section.items.filterIsInstance<SongItem>().distinctBy { it.id }
        }
    }
    val topVideoItems = remember(topVideosSection) {
        topVideosSection?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
    }
    val moodsGroup = remember(moodAndGenres) {
        moodAndGenres?.firstOrNull { it.title == "Moods & moments" }
    }
    val moodRows = remember(moodsGroup, itemsPerRow) {
        moodsGroup?.items?.mapIndexed { index, item -> index to item }?.chunked(itemsPerRow).orEmpty()
    }
    val genresGroup = remember(moodAndGenres) {
        moodAndGenres?.firstOrNull { it.title == "Genres" }
    }
    val genreRows = remember(genresGroup, itemsPerRow) {
        genresGroup?.items?.mapIndexed { index, item -> index to item }?.chunked(itemsPerRow).orEmpty()
    }
    val topVideoPrefetchModels = remember(topVideoItems) {
        topVideoItems.mapNotNull { song ->
            song.thumbnail?.takeIf { it.isNotBlank() }?.let { url ->
                PrefetchImageModel(
                    stableKey = "top_video_${song.id}",
                    url = url,
                )
            }
        }
    }
    val exploreHasLoadedContent by remember(chartsPage, explorePage) {
        derivedStateOf { chartsPage != null && explorePage != null }
    }
    val inlineErrorText by remember(chartsError, exploreError) {
        derivedStateOf { chartsError ?: exploreError }
    }
    val isInitialLoading by remember(isChartsLoading, isExploreLoading, exploreHasLoadedContent) {
        derivedStateOf { (isChartsLoading || isExploreLoading) && !exploreHasLoadedContent }
    }

    PrefetchNextImages(
        state = topVideosRowState,
        imageModels = topVideoPrefetchModels,
        prefetchCount = 4,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            // Enable overscroll for bounce effect
            flingBehavior = ScrollableDefaults.flingBehavior()
        ) {
            item(key = "top_spacer", contentType = "spacer") {
                Spacer(
                    Modifier.height(
                        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding(),
                    ),
                )
            }

            if (isInitialLoading) {
                item(key = "loading", contentType = "loading") {
                    ShimmerHost {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(0.5f),
                        )
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val horizontalLazyGridItemWidthFactor =
                                if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                            val horizontalLazyGridItemWidth =
                                maxWidth * horizontalLazyGridItemWidthFactor

                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                contentPadding = PaddingValues(start = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * 4),
                                userScrollEnabled = false // Disable scroll in nested grid
                            ) {
                                items(
                                    count = 4,
                                    key = { "charts_loading_$it" },
                                    contentType = { "loading_item" },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .width(horizontalLazyGridItemWidth)
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(ListItemHeight - 16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .height(16.dp)
                                                    .width(120.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .height(12.dp)
                                                    .width(80.dp)
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        repeat(2) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .width(250.dp),
                            )
                            Row {
                                repeat(2) {
                                    GridItemPlaceHolder()
                                }
                            }
                        }
                    }
                }
            } else if (!exploreHasLoadedContent) {
                item(key = "explore_retry", contentType = "retry") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = inlineErrorText ?: stringResource(R.string.error_no_internet),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = {
                                    chartsViewModel.retry()
                                    exploreViewModel.refresh()
                                }
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            } else {
                chartSectionsWithoutVideos.forEachIndexed { sectionIndex, section ->
                        item(key = "section_title_$sectionIndex", contentType = "section_title") {
                            NavigationTitle(
                                title = when (section.title) {
                                    "Trending" -> stringResource(R.string.trending)
                                    else -> section.title.ifEmpty { stringResource(R.string.charts) }
                                },
                            )
                        }

                        item(key = "section_content_$sectionIndex", contentType = "chart_section") {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val horizontalLazyGridItemWidthFactor =
                                    if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                val horizontalLazyGridItemWidth =
                                    maxWidth * horizontalLazyGridItemWidthFactor

                                val lazyGridState = rememberLazyGridState()
                                val sectionSongs = chartSongsBySection[sectionIndex]
                                val sectionPrefetchModels = remember(sectionSongs) {
                                    sectionSongs.mapNotNull { song ->
                                        song.thumbnail?.takeIf { it.isNotBlank() }?.let { url ->
                                            PrefetchImageModel(
                                                stableKey = "chart_song_${song.id}",
                                                url = url,
                                            )
                                        }
                                    }
                                }
                                val snapLayoutInfoProvider = remember(lazyGridState) {
                                    SnapLayoutInfoProvider(
                                        lazyGridState = lazyGridState,
                                        positionInLayout = { layoutSize, itemSize ->
                                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                        },
                                    )
                                }

                                LazyHorizontalGrid(
                                    state = lazyGridState,
                                    rows = GridCells.Fixed(4),
                                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                    contentPadding = WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ListItemHeight * 4),
                                ) {
                                    items(
                                        items = sectionSongs,
                                        key = { it.id },
                                        contentType = { "song_item" },
                                    ) { song ->
                                        YouTubeListItem(
                                            item = song,
                                            isActive = song.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            isSwipeable = false,
                                            trailingContent = {
                                                IconButton(
                                                    onClick = {
                                                        menuState.show {
                                                            YouTubeSongMenu(
                                                                song = song,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
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
                                                .width(horizontalLazyGridItemWidth)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (song.id == mediaMetadata?.id) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    endpoint = WatchEndpoint(videoId = song.id),
                                                                    preloadItem = song.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        menuState.show {
                                                            YouTubeSongMenu(
                                                                song = song,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    },
                                                ),
                                        )
                                    }
                                }

                                PrefetchNextImages(
                                    state = lazyGridState,
                                    imageModels = sectionPrefetchModels,
                                    prefetchCount = 4,
                                )
                            }
                        }
                    }

                topVideosSection?.let {
                        item(key = "top_videos_title", contentType = "section_title") {
                            NavigationTitle(
                                title = stringResource(R.string.top_music_videos),
                            )
                        }

                        item(key = "top_videos_content", contentType = "top_videos") {
                            LazyRow(
                                state = topVideosRowState,
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                            ) {
                                items(
                                    items = topVideoItems,
                                    key = { it.id },
                                    contentType = { "video_item" },
                                ) { video ->
                                    YouTubeGridItem(
                                        item = video,
                                        isActive = video.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (video.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                endpoint = WatchEndpoint(videoId = video.id),
                                                                preloadItem = video.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = video,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }

                moodsGroup?.let { moodGroup ->
                    item(key = "moods_title", contentType = "section_title") {
                        NavigationTitle(title = moodGroup.title)
                    }

                    item(key = "moods_content", contentType = "moods_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            moodRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { (index, item) ->
                                        val key = item.endpoint.browseId + (item.endpoint.params ?: "")
                                        val backgroundColor =
                                            spotifyLikeColors[index % spotifyLikeColors.size]

                                        MoodAndGenresButton(
                                            title = item.title,
                                            onClick = {
                                                navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                                            },
                                            containerColor = backgroundColor,
                                            thumbnailUrl = moodThumbnails[key],
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .weight(1f)
                                                .animateItem(),
                                        )
                                    }
                                    // Add empty space if row is not full
                                    repeat(itemsPerRow - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                genresGroup?.let { genreGroup ->
                    item(key = "genres_title", contentType = "section_title") {
                        NavigationTitle(title = genreGroup.title)
                    }

                    item(key = "genres_content", contentType = "genres_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            genreRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { (index, item) ->
                                        val key = item.endpoint.browseId + (item.endpoint.params ?: "")
                                        val backgroundColor =
                                            spotifyLikeColors[index % spotifyLikeColors.size]

                                        MoodAndGenresButton(
                                            title = item.title,
                                            onClick = {
                                                navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                                            },
                                            containerColor = backgroundColor,
//                                            thumbnailUrl = moodThumbnails[key],
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .weight(1f)
                                                .animateItem(),
                                        )
                                    }
                                    // Add empty space if row is not full
                                    repeat(itemsPerRow - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(
                    Modifier.height(
                        LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
                    )
                )
            }
        }
    }
}
