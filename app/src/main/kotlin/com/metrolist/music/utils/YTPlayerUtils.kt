package com.metrolist.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.PlaybackException
import com.metrolist.music.constants.AudioQuality
import com.metrolist.innertube.pages.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.potoken.PoTokenGenerator
import com.metrolist.music.utils.potoken.PoTokenResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.services.youtube.NTransformSolver
import timber.log.Timber
import kotlin.math.abs

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    // ADVANCED FIX – Adaptive client success/failure tracking
    private data class ClientStats(
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var blacklistUntilMs: Long = 0L,
    )

    // This state is in-memory only and resets on app restart.
    private val clientStats: MutableMap<YouTubeClient, ClientStats> = mutableMapOf()
    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.metrolist.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32
    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        IOS,
        WEB,
        WEB_CREATOR
    )

    private fun isAgeRestricted(playerResponse: PlayerResponse?): Boolean {
        val reason = playerResponse?.playabilityStatus?.reason.orEmpty()
        return playerResponse?.playabilityStatus?.status != "OK" &&
            reason.contains("confirm your age", ignoreCase = true)
    }

    private fun buildOrderedFallbackClients(
        isLoggedIn: Boolean,
        preferAgeGateClients: Boolean,
    ): List<YouTubeClient> {
        val preferredClients =
            if (preferAgeGateClients) {
                if (isLoggedIn) {
                    setOf(WEB_CREATOR, TVHTML5, WEB_REMIX, TVHTML5_SIMPLY_EMBEDDED_PLAYER)
                } else {
                    setOf(WEB_REMIX, TVHTML5_SIMPLY_EMBEDDED_PLAYER)
                }
            } else {
                emptySet()
            }

        return STREAM_FALLBACK_CLIENTS.sortedWith(
            compareByDescending<YouTubeClient> { client ->
                if (client in preferredClients) 1 else 0
            }.thenByDescending { client ->
                val stats = clientStats[client]
                if (stats == null) 0 else stats.successCount - stats.failureCount
            }
        )
    }

    enum class StreamTier {
        PRIMARY_AUDIO,
        LOW_AUDIO,
        VIDEO_360,
        REFRESH_AUDIO,
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        streamTier: StreamTier = StreamTier.PRIMARY_AUDIO,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        /**
         * This is required for some clients to get working streams however
         * it should not be forced for the [MAIN_CLIENT] because the response of the [MAIN_CLIENT]
         * is required even if the streams won't work from this client.
         * This is why it is allowed to be null.
         */
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: $signatureTimestamp")

        val isLoggedIn = YouTube.cookie != null
        val sessionId =
            if (isLoggedIn) {
                // signed in sessions use dataSyncId as identifier
                YouTube.dataSyncId
            } else {
                // signed out sessions use visitorData as identifier
                YouTube.visitorData ?: runCatching { YouTube.visitorData().getOrThrow() }
                    .onSuccess { visitorData -> YouTube.visitorData = visitorData }
                    .onFailure { Timber.tag(logTag).e(it, "Failed to refresh visitorData for anonymous playback") }
                    .getOrNull()
            }
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        val hasWebPoTokenClient = MAIN_CLIENT.useWebPoTokens || STREAM_FALLBACK_CLIENTS.any { it.useWebPoTokens }
        val poToken: PoTokenResult? =
            if (hasWebPoTokenClient && sessionId != null) {
                runCatching { poTokenGenerator.getWebClientPoToken(videoId, sessionId) }
                    .onFailure { Timber.tag(logTag).e(it, "PoToken generation failed") }
                    .getOrNull()
            } else {
                null
            }

        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        val mainPlayerResponse =
            YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = MAIN_CLIENT,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken?.playerRequestPoToken,
            ).getOrThrow()
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null

        // ADVANCED FIX – Adaptive client rotation and temporary blacklist
        val orderedFallbackClients = buildOrderedFallbackClients(
            isLoggedIn = isLoggedIn,
            preferAgeGateClients = isAgeRestricted(mainPlayerResponse),
        )

        for (clientIndex in (-1 until orderedFallbackClients.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = orderedFallbackClients[clientIndex]

                val now = System.currentTimeMillis()
                val stats = clientStats.getOrPut(client) { ClientStats() }
                if (stats.blacklistUntilMs > now) {
                    Timber.tag(logTag).d("Skipping blacklisted client: ${client.clientName}")
                    continue
                }

                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${orderedFallbackClients.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                streamPlayerResponse =
                    YouTube.player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = client,
                        signatureTimestamp = signatureTimestamp,
                        poToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null,
                    ).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else client.clientName}")

                format =
                    findFormat(
                        streamPlayerResponse,
                        audioQuality,
                        connectivityManager,
                        streamTier,
                    )

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else client.clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl != null && client.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    streamUrl = appendPoTokenParam(streamUrl, poToken.streamingDataPoToken)
                }
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    clientStats[client]?.let { it.failureCount++ }
                    continue
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    clientStats[client]?.let { it.failureCount++ }
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                // ADVANCED FIX – Always validate stream before handing to ExoPlayer
                if (validateStatus(streamUrl, format)) {
                    // working stream found
                    Timber.tag(logTag).d(
                        "Stream validated successfully with client: ${
                            if (clientIndex == -1) MAIN_CLIENT.clientName else client.clientName
                        }"
                    )
                    clientStats[client]?.let { it.successCount++ }
                    break
                } else {
                    Timber.tag(logTag).d(
                        "Stream validation failed for client: ${
                            if (clientIndex == -1) MAIN_CLIENT.clientName else client.clientName
                        }"
                    )
                    clientStats[client]?.let {
                        it.failureCount++
                        // ADVANCED FIX – Temporarily blacklist noisy clients
                        if (it.failureCount - it.successCount >= 3 && it.blacklistUntilMs <= 0L) {
                            it.blacklistUntilMs = System.currentTimeMillis() + 5 * 60_000L
                            Timber.tag(logTag).d("Blacklisting client for session: ${client.clientName}")
                        }
                    }
                }
            } else {
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
                clientStats[client]?.let { it.failureCount++ }
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            throw PlaybackException(
                "Bad stream player response - all clients failed",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason =
                if (!isLoggedIn && isAgeRestricted(streamPlayerResponse)) {
                    "Age-restricted playback now requires a signed-in, age-verified YouTube session"
                } else {
                    streamPlayerResponse.playabilityStatus.reason
                }
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw PlaybackException(
                "Missing stream expire time",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw PlaybackException(
                "Could not find format",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw PlaybackException(
                "Could not find stream url",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        streamTier: StreamTier,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d(
            "Finding format with streamTier=$streamTier, audioQuality=$audioQuality, metered=${connectivityManager.isActiveNetworkMetered}"
        )

        val format = when (streamTier) {
            StreamTier.PRIMARY_AUDIO,
            StreamTier.REFRESH_AUDIO -> selectPrimaryAudioFormat(
                playerResponse = playerResponse,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            )

            StreamTier.LOW_AUDIO -> selectLowAudioFormat(playerResponse)
            StreamTier.VIDEO_360 -> selectVideo360Format(playerResponse)
        }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}, quality=${format.qualityLabel ?: format.quality}")
        } else {
            Timber.tag(logTag).d("No suitable format found for streamTier=$streamTier")
        }
        return format
    }

    private fun selectPrimaryAudioFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        return playerResponse.streamingData
            ?.adaptiveFormats
            ?.asSequence()
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }
    }

    private fun selectLowAudioFormat(
        playerResponse: PlayerResponse,
    ): PlayerResponse.StreamingData.Format? {
        return playerResponse.streamingData
            ?.adaptiveFormats
            ?.asSequence()
            ?.filter { it.isAudio && it.isOriginal }
            ?.minByOrNull { it.bitrate }
    }

    private fun selectVideo360Format(
        playerResponse: PlayerResponse,
    ): PlayerResponse.StreamingData.Format? {
        val muxedMp4 = playerResponse.streamingData
            ?.formats
            .orEmpty()
            .asSequence()
            .filter { !it.isAudio }
            .filter { it.mimeType.startsWith("video/mp4") }
            .filter { it.mimeType.contains("mp4a", ignoreCase = true) }
            .toList()

        if (muxedMp4.isNotEmpty()) {
            return muxedMp4.minByOrNull { format ->
                val resolution = format.qualityLabel
                    ?.substringBefore("p")
                    ?.toIntOrNull()
                    ?: format.height
                    ?: Int.MAX_VALUE
                abs(resolution - 360)
            }
        }

        // Last resort: pick the lowest non-audio MP4 stream available.
        return playerResponse.streamingData
            ?.adaptiveFormats
            ?.asSequence()
            ?.filter { !it.isAudio }
            ?.filter { it.mimeType.startsWith("video/mp4") }
            ?.minByOrNull { it.bitrate }
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(
        url: String,
        format: PlayerResponse.StreamingData.Format,
    ): Boolean {
        // ADVANCED FIX – Stream HEAD validation (status, type, length)
        Timber.tag(logTag).d("Validating stream URL status and headers")

        val request: Request =
            Request.Builder()
                .head()
                .url(url)
                .build()

        val call = httpClient.newCall(request)
        try {
            val response = call.execute()
            response.use {
                val codeOk = it.code == 200
                val contentType = it.header("Content-Type") ?: ""
                val contentLength = it.header("Content-Length")?.toLongOrNull() ?: -1L
                val hasLength = contentLength > 0L
                val typeMatches =
                    if (format.isAudio) {
                        contentType.startsWith("audio/") || contentType.startsWith("application/octet-stream")
                    } else {
                        contentType.startsWith("video/") || contentType.startsWith("application/octet-stream")
                    }

                val valid = codeOk && typeMatches && hasLength
                Timber.tag(logTag).d(
                    "Stream URL validation result: ${if (valid) "Success" else "Failed"} " +
                        "(code=${it.code}, type=$contentType, length=$contentLength)"
                )
                return valid
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
            // Cancel call in case this was triggered by coroutine cancellation.
            call.cancel()
        }
        return false
    }

    private fun appendPoTokenParam(
        streamUrl: String,
        streamingDataPoToken: String,
    ): String {
        val uri = Uri.parse(streamUrl)
        if (uri.getQueryParameter("pot") != null) {
            return streamUrl
        }
        val separator = if (streamUrl.contains("?")) "&" else "?"
        return streamUrl + separator + "pot=" + Uri.encode(streamingDataPoToken)
    }

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).d("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }
            .getOrNull()
    }
    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        val rawUrl = NewPipeUtils.getStreamUrl(format, videoId)
            .onSuccess { Timber.tag(logTag).d("Stream URL obtained successfully") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get stream URL")
                reportException(it)
            }
            .getOrNull()
            ?: return null

        return runCatching { NTransformSolver.transformNParamInUrl(videoId, rawUrl) }
            .onSuccess { Timber.tag(logTag).d("Local n transform applied successfully") }
            .onFailure {
                Timber.tag(logTag).w(it, "Local n transform failed, falling back to NewPipe")
            }
            .getOrElse {
                NewPipeUtils.applyFallbackNTransform(rawUrl, videoId)
                    .onSuccess {
                        Timber.tag(logTag).d("Fallback n transform applied successfully")
                    }
                    .onFailure { fallbackError ->
                        Timber.tag(logTag).e(fallbackError, "Failed to transform n parameter")
                        reportException(fallbackError)
                    }
                    .getOrDefault(rawUrl)
            }
    }
}
