package com.metrolist.music.utils.potoken

import android.webkit.CookieManager
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class PoTokenGenerator {
    private val tag = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            return null
        }

        return try {
            runBlocking { getWebClientPoToken(videoId, sessionId, forceRecreate = false) }
        } catch (e: Exception) {
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(tag).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    null
                }

                else -> throw e
            }
        }
    }

    private suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoTokenResult {
        val (poTokenGenerator, streamingPot, hasBeenRecreated) = webPoTokenGenLock.withLock {
            val shouldRecreate =
                forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId

            if (shouldRecreate) {
                webPoTokenSessionId = sessionId

                withContext(Dispatchers.Main) {
                    webPoTokenGenerator?.close()
                }

                webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)
                webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
            }

            Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
        }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                throw throwable
            } else {
                Timber.tag(tag).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        return PoTokenResult(playerPot, streamingPot)
    }
}
