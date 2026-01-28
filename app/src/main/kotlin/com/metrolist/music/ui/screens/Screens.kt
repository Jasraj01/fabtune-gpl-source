package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.homenew,
        iconIdActive = R.drawable.homwnew_fill,
        route = "home"
    )

    object Explore : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.new_search_simple,
        iconIdActive = R.drawable.new_search,
        route = "explore"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_new,
        iconIdActive = R.drawable.library_filled_new,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Explore , Library)
    }
}
