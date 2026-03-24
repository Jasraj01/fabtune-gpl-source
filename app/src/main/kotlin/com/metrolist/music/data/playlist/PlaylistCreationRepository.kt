package com.metrolist.music.data.playlist

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.body.CreatePlaylistBody
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class CreatePlaylistRequest(
    val name: String,
    val syncWithYouTube: Boolean,
    val isSignedIn: Boolean,
    val privacyStatus: String = CreatePlaylistBody.PrivacyStatus.PRIVATE,
)

enum class CreatePlaylistErrorReason {
    InvalidInput,
    Authentication,
    Network,
    Unexpected,
}

sealed interface CreatePlaylistResult {
    data class Success(
        val normalizedName: String,
        val browseId: String?,
    ) : CreatePlaylistResult

    data class Error(
        val reason: CreatePlaylistErrorReason,
        val message: String,
        val cause: Throwable? = null,
    ) : CreatePlaylistResult
}

class PlaylistCreationRepository @Inject constructor() {
    companion object {
        const val MAX_PLAYLIST_NAME_LENGTH = 150

        private val VALID_PRIVACY_STATUSES = setOf(
            CreatePlaylistBody.PrivacyStatus.PRIVATE,
            CreatePlaylistBody.PrivacyStatus.PUBLIC,
            CreatePlaylistBody.PrivacyStatus.UNLISTED,
        )
    }

    suspend fun createPlaylist(request: CreatePlaylistRequest): CreatePlaylistResult {
        val normalizedName = request.name.trim()

        if (normalizedName.isBlank()) {
            return CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.InvalidInput,
                message = "Playlist name cannot be empty.",
            )
        }

        if (normalizedName.length > MAX_PLAYLIST_NAME_LENGTH) {
            return CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.InvalidInput,
                message = "Playlist name must be $MAX_PLAYLIST_NAME_LENGTH characters or fewer.",
            )
        }

        if (request.privacyStatus !in VALID_PRIVACY_STATUSES) {
            return CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.InvalidInput,
                message = "Invalid playlist privacy status.",
            )
        }

        if (request.syncWithYouTube && !request.isSignedIn) {
            return CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.Authentication,
                message = "You are not signed in.",
            )
        }

        if (!request.syncWithYouTube) {
            return CreatePlaylistResult.Success(
                normalizedName = normalizedName,
                browseId = null,
            )
        }

        return try {
            val browseId = YouTube.createPlaylist(normalizedName)
            CreatePlaylistResult.Success(
                normalizedName = normalizedName,
                browseId = browseId,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.BadRequest -> CreatePlaylistResult.Error(
                    reason = CreatePlaylistErrorReason.InvalidInput,
                    message = "Invalid playlist request.",
                    cause = e,
                )
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> CreatePlaylistResult.Error(
                    reason = CreatePlaylistErrorReason.Authentication,
                    message = "Authentication failed.",
                    cause = e,
                )
                else -> CreatePlaylistResult.Error(
                    reason = CreatePlaylistErrorReason.Unexpected,
                    message = "Playlist creation failed with HTTP ${e.response.status.value}.",
                    cause = e,
                )
            }
        } catch (e: ResponseException) {
            CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.Unexpected,
                message = "Playlist creation failed with HTTP ${e.response.status.value}.",
                cause = e,
            )
        } catch (e: IOException) {
            CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.Network,
                message = "Network error while creating playlist.",
                cause = e,
            )
        } catch (e: Exception) {
            CreatePlaylistResult.Error(
                reason = CreatePlaylistErrorReason.Unexpected,
                message = e.message ?: "Unexpected error while creating playlist.",
                cause = e,
            )
        }
    }
}
