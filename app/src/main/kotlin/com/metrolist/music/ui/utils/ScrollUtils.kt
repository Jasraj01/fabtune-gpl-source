package com.metrolist.music.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var previousIndex = firstVisibleItemIndex
        var previousScrollOffset = firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                isScrollingUp = if (currentIndex != previousIndex) {
                    previousIndex > currentIndex
                } else {
                    previousScrollOffset >= currentOffset
                }
                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }
    return isScrollingUp
}

@Composable
fun LazyGridState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var previousIndex = firstVisibleItemIndex
        var previousScrollOffset = firstVisibleItemScrollOffset
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                isScrollingUp = if (currentIndex != previousIndex) {
                    previousIndex > currentIndex
                } else {
                    previousScrollOffset >= currentOffset
                }
                previousIndex = currentIndex
                previousScrollOffset = currentOffset
            }
    }
    return isScrollingUp
}

@Composable
fun ScrollState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    LaunchedEffect(this) {
        var previousScrollOffset = value
        snapshotFlow { value }
            .collect { currentOffset ->
                isScrollingUp = previousScrollOffset >= currentOffset
                previousScrollOffset = currentOffset
            }
    }
    return isScrollingUp
}
