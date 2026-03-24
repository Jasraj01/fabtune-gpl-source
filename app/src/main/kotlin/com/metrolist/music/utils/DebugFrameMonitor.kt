package com.metrolist.music.utils

import android.view.Choreographer
import com.metrolist.music.BuildConfig
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight frame-time logger for debug builds.
 * Disabled in release and safe to leave in production code paths.
 */
object DebugFrameMonitor {
    private const val JANK_THRESHOLD_MS = 32.0
    private const val STUTTER_THRESHOLD_MS = 50.0
    private const val SUMMARY_INTERVAL_MS = 2_500L
    private val isRunning = AtomicBoolean(false)
    private var lastFrameNanos: Long = 0L
    private var summaryWindowStartNanos: Long = 0L
    private var sampledFrames: Int = 0
    private var jankFrames: Int = 0
    private var stutterFrames: Int = 0
    private var worstFrameMs: Double = 0.0
    private var callback: Choreographer.FrameCallback? = null

    fun start() {
        if (!BuildConfig.DEBUG) return
        if (!isRunning.compareAndSet(false, true)) return

        resetWindow()
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isRunning.get()) return
                if (lastFrameNanos != 0L) {
                    val frameMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
                    sampledFrames += 1
                    if (frameMs > worstFrameMs) worstFrameMs = frameMs
                    when {
                        frameMs >= STUTTER_THRESHOLD_MS -> stutterFrames += 1
                        frameMs >= JANK_THRESHOLD_MS -> jankFrames += 1
                    }
                }
                if (summaryWindowStartNanos == 0L) summaryWindowStartNanos = frameTimeNanos

                val elapsedMs = (frameTimeNanos - summaryWindowStartNanos) / 1_000_000L
                if (elapsedMs >= SUMMARY_INTERVAL_MS && sampledFrames > 0) {
                    if (jankFrames > 0 || stutterFrames > 0) {
                        Timber.tag("FrameMonitor").d(
                            "Frame summary: %d frames, %d jank, %d stutter, worst %.1f ms in %d ms",
                            sampledFrames,
                            jankFrames,
                            stutterFrames,
                            worstFrameMs,
                            elapsedMs
                        )
                    }
                    summaryWindowStartNanos = frameTimeNanos
                    sampledFrames = 0
                    jankFrames = 0
                    stutterFrames = 0
                    worstFrameMs = 0.0
                }
                lastFrameNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        callback = frameCallback
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        callback = null
        resetWindow()
    }

    private fun resetWindow() {
        lastFrameNanos = 0L
        summaryWindowStartNanos = 0L
        sampledFrames = 0
        jankFrames = 0
        stutterFrames = 0
        worstFrameMs = 0.0
    }
}
