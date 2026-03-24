@file:OptIn(ExperimentalCoroutinesApi::class)

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.AddToPlaylistSortDescendingKey
import com.metrolist.music.constants.AddToPlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.data.playlist.CreatePlaylistErrorReason
import com.metrolist.music.data.playlist.CreatePlaylistRequest
import com.metrolist.music.data.playlist.CreatePlaylistResult
import com.metrolist.music.data.playlist.PlaylistCreationRepository
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    private val playlistCreationRepository: PlaylistCreationRepository,
) : ViewModel() {
    private val _isCreating = MutableStateFlow(false)
    val isCreating = _isCreating.asStateFlow()

    private val _playlistCreationResult = MutableStateFlow<CreatePlaylistResult?>(null)
    val playlistCreationResult = _playlistCreationResult.asStateFlow()

    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Suspend function that waits for sync to complete
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }

    fun createPlaylist(
        name: String,
        syncWithYouTube: Boolean,
        isSignedIn: Boolean,
    ) {
        if (_isCreating.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isCreating.value = true
            try {
                val result = playlistCreationRepository.createPlaylist(
                    CreatePlaylistRequest(
                        name = name,
                        syncWithYouTube = syncWithYouTube,
                        isSignedIn = isSignedIn,
                    )
                )

                when (result) {
                    is CreatePlaylistResult.Success -> {
                        database.insert(
                            PlaylistEntity(
                                name = result.normalizedName,
                                browseId = result.browseId,
                                bookmarkedAt = LocalDateTime.now(),
                                isEditable = true,
                            )
                        )
                        _playlistCreationResult.value = result
                    }

                    is CreatePlaylistResult.Error -> {
                        if (
                            result.reason == CreatePlaylistErrorReason.Unexpected &&
                            result.cause != null
                        ) {
                            reportException(result.cause)
                        }
                        _playlistCreationResult.value = result
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                _playlistCreationResult.value = CreatePlaylistResult.Error(
                    reason = CreatePlaylistErrorReason.Unexpected,
                    message = e.message ?: "Unexpected error while creating playlist.",
                    cause = e,
                )
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun clearPlaylistCreationResult() {
        _playlistCreationResult.value = null
    }
}
