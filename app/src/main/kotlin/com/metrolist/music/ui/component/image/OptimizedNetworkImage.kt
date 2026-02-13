package com.metrolist.music.ui.component.image

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import com.metrolist.music.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MAX_CROSSFADE_MS = 120
private const val MAX_MEDIUM_RETRY_ATTEMPTS = 4
private const val INITIAL_MEDIUM_RETRY_DELAY_MS = 350L

@Immutable
data class ProgressiveImageModel(
    val stableKey: String,
    val url: String?,
)

@Immutable
data class PrefetchImageModel(
    val stableKey: String,
    val url: String,
)

@Composable
fun ProgressiveNetworkImage(
    model: ProgressiveImageModel,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes placeholderResId: Int,
    @DrawableRes errorResId: Int = placeholderResId,
    @DrawableRes fallbackResId: Int = placeholderResId,
    thumbnailSizePx: Int = 144,
    mediumSizePx: Int = 360,
) {
    val context = LocalContext.current
    val imageUrl = model.url?.takeIf { it.isNotBlank() }

    val placeholderPainter = painterResource(placeholderResId)
    val errorPainter = painterResource(errorResId)
    val fallbackPainter = painterResource(fallbackResId)

    if (imageUrl == null) {
        Image(
            painter = fallbackPainter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
        return
    }

    val thumbnailUrl = remember(imageUrl, thumbnailSizePx) { imageUrl.resize(width = thumbnailSizePx) }
    val mediumUrl = remember(imageUrl, mediumSizePx) { imageUrl.resize(width = mediumSizePx) }

    var shouldLoadMedium by remember(model.stableKey, imageUrl) { mutableStateOf(false) }
    var mediumLoaded by remember(model.stableKey, imageUrl) { mutableStateOf(false) }
    var mediumRetryAttempt by remember(model.stableKey, imageUrl) { mutableIntStateOf(0) }
    var mediumRetryNonce by remember(model.stableKey, imageUrl) { mutableIntStateOf(0) }

    val thumbnailRequest = remember(model.stableKey, thumbnailUrl, thumbnailSizePx) {
        buildOptimizedImageRequest(
            context = context,
            data = thumbnailUrl,
            sizePx = thumbnailSizePx,
            crossfadeMillis = 0,
            useRgb565 = true,
        )
    }

    val thumbnailPainter = rememberAsyncImagePainter(
        model = thumbnailRequest,
        placeholder = placeholderPainter,
        error = errorPainter,
        fallback = fallbackPainter,
    )
    val thumbnailState by thumbnailPainter.state.collectAsState()

    LaunchedEffect(thumbnailState, model.stableKey, imageUrl) {
        if (thumbnailState is AsyncImagePainter.State.Success) {
            shouldLoadMedium = true
        }
    }

    val mediumRequest = remember(model.stableKey, mediumUrl, mediumSizePx, mediumRetryNonce) {
        buildOptimizedImageRequest(
            context = context,
            data = mediumUrl,
            sizePx = mediumSizePx,
            crossfadeMillis = MAX_CROSSFADE_MS,
            useRgb565 = false,
        )
    }

    val mediumPainter = rememberAsyncImagePainter(
        model = if (shouldLoadMedium) mediumRequest else null,
    )
    val mediumState by mediumPainter.state.collectAsState()

    LaunchedEffect(mediumState, shouldLoadMedium, model.stableKey, imageUrl) {
        when (mediumState) {
            is AsyncImagePainter.State.Success -> {
                if (shouldLoadMedium) {
                    mediumLoaded = true
                }
            }

            is AsyncImagePainter.State.Error -> {
                if (shouldLoadMedium && !mediumLoaded && mediumRetryAttempt < MAX_MEDIUM_RETRY_ATTEMPTS) {
                    val retryDelayMs = INITIAL_MEDIUM_RETRY_DELAY_MS shl mediumRetryAttempt
                    mediumRetryAttempt += 1
                    delay(retryDelayMs)
                    mediumRetryNonce += 1
                }
            }

            else -> Unit
        }
    }

    val mediumAlpha by animateFloatAsState(
        targetValue = if (mediumLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = MAX_CROSSFADE_MS),
        label = "progressiveImageAlpha",
    )

    Box(modifier = modifier) {
        Image(
            painter = thumbnailPainter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        if (shouldLoadMedium) {
            Image(
                painter = mediumPainter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(mediumAlpha),
            )
        }
    }
}

@Composable
fun PrefetchNextImages(
    state: LazyListState,
    imageModels: List<PrefetchImageModel>,
    prefetchCount: Int = 4,
    requestSizePx: Int = 280,
) {
    PrefetchNextImagesInternal(
        visibleIndexProvider = { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
        imageModels = imageModels,
        prefetchCount = prefetchCount,
        requestSizePx = requestSizePx,
    )
}

@Composable
fun PrefetchNextImages(
    state: LazyGridState,
    imageModels: List<PrefetchImageModel>,
    prefetchCount: Int = 4,
    requestSizePx: Int = 280,
) {
    PrefetchNextImagesInternal(
        visibleIndexProvider = { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
        imageModels = imageModels,
        prefetchCount = prefetchCount,
        requestSizePx = requestSizePx,
    )
}

@Composable
private fun PrefetchNextImagesInternal(
    visibleIndexProvider: () -> Int?,
    imageModels: List<PrefetchImageModel>,
    prefetchCount: Int,
    requestSizePx: Int,
) {
    val appContext = LocalContext.current.applicationContext
    val imageLoader = remember(appContext) { appContext.imageLoader }
    val boundedPrefetchCount = remember(prefetchCount) { prefetchCount.coerceIn(4, 6) }
    val distinctModels = remember(imageModels) {
        imageModels
            .filter { it.url.isNotBlank() }
            .distinctBy { it.stableKey }
    }

    LaunchedEffect(distinctModels, boundedPrefetchCount, requestSizePx) {
        if (distinctModels.isEmpty()) return@LaunchedEffect

        // Keep only a bounded rolling window of already-prefetched keys.
        val prefetchedKeys = LinkedHashSet<String>(256)

        snapshotFlow { visibleIndexProvider() }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val lastIndex = lastVisible ?: return@collect
                val maxIndex = distinctModels.lastIndex
                if (maxIndex < 0) return@collect

                val start = (lastIndex + 1).coerceAtMost(maxIndex)
                val end = (start + boundedPrefetchCount - 1).coerceAtMost(maxIndex)
                if (start > end) return@collect

                for (index in start..end) {
                    val model = distinctModels[index]
                    if (!prefetchedKeys.add(model.stableKey)) continue

                    if (prefetchedKeys.size > 300) {
                        prefetchedKeys.firstOrNull()?.let(prefetchedKeys::remove)
                    }

                    imageLoader.enqueue(
                        buildOptimizedImageRequest(
                            context = appContext,
                            data = model.url,
                            sizePx = requestSizePx,
                            crossfadeMillis = 0,
                            useRgb565 = true,
                        )
                    )
                }
            }
    }
}

private fun buildOptimizedImageRequest(
    context: Context,
    data: String,
    sizePx: Int,
    crossfadeMillis: Int,
    useRgb565: Boolean,
): ImageRequest {
    val cacheKey = "$data@$sizePx"

    return ImageRequest.Builder(context)
        .data(data)
        .size(sizePx, sizePx)
        .precision(Precision.INEXACT)
        .memoryCacheKey(cacheKey)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .allowHardware(true)
        .allowRgb565(useRgb565)
        .bitmapConfig(if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        .crossfade(crossfadeMillis.coerceIn(0, MAX_CROSSFADE_MS))
        .build()
}
