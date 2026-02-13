package com.metrolist.music.ui.screens

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.image.ProgressiveImageModel
import com.metrolist.music.ui.component.image.ProgressiveNetworkImage
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.MoodAndGenresViewModel
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = if (localConfiguration.orientation == ORIENTATION_LANDSCAPE) 3 else 2

    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        if (moodAndGenresList == null) {
            item {
                ShimmerHost(
                    modifier = Modifier.animateItem()
                ) {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        } else {
            items(
                items = moodAndGenresList!!,
                key = { it.title }
            ) { moodAndGenres ->
                Column {
                    NavigationTitle(
                        title = moodAndGenres.title,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .fillMaxWidth()
                    )

                    val rows = ceil(
                        moodAndGenres.items.size.toDouble() / itemsPerRow.toDouble()
                    ).toInt()
                    val gridHeight = MoodAndGenresButtonHeight * rows + 16.dp * rows

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(itemsPerRow),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight)
                    ) {
                        items(
                            items = moodAndGenres.items,
                            key = { it.endpoint.browseId }
                        ) { item ->
                            MoodAndGenresButton(
                                title = item.title,
                                onClick = {
                                    navController.navigate(
                                        "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}"
                                    )
                                },
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.mood_and_genres)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun MoodAndGenresButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbnailUrl: String? = null,
    height: Dp = MoodAndGenresButtonHeight,
    screenWidthThreshold: Int = 600,
    smallFontSize: Double = 14.5,
    defaultFontSize: Int = 16
) {
    BoxWithConstraints(modifier = modifier) {
        // --- CORRECTED LOGIC ---
        // The font size now only depends on the screen width for consistency.
        val isSmallScreen = this.maxWidth < screenWidthThreshold.dp
        // val isOneWord = title.trim().split(Regex("\\s+")).count() == 1 // <-- REMOVED

        val dynamicFontSize = if (isSmallScreen) { // <-- SIMPLIFIED
            smallFontSize.sp
        } else {
            defaultFontSize.sp
        }

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = tween(durationMillis = 100),
            label = "pressScale"
        )

        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(10.dp))
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                .background(containerColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            try {
                                awaitRelease()
                                onClick()
                            } finally {
                                // release handled automatically
                            }
                        }
                    )
                }
                .height(height)
                .padding(horizontal = 12.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    // Apply the consistent, dynamic font size
                    fontSize = dynamicFontSize,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (thumbnailUrl != null) {
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 10.dp, end = 76.dp)
                    } else {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 12.dp)
                    }
                )

                if (thumbnailUrl != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .rotate(18f)
                            .offset(x = 4.dp, y = 6.dp)
                            .size(height * 0.73f)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        ProgressiveNetworkImage(
                            model = ProgressiveImageModel(
                                stableKey = "mood_button_${title}_${thumbnailUrl}",
                                url = thumbnailUrl,
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholderResId = R.drawable.album_search,
                            errorResId = R.drawable.album_search,
                            fallbackResId = R.drawable.album_search,
                            thumbnailSizePx = 96,
                            mediumSizePx = 200,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

val MoodAndGenresButtonHeight = 82.dp
