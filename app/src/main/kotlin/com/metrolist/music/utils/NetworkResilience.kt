package com.metrolist.music.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/**
 * Executes a network [block] with a bounded timeout and exponential backoff retries.
 *
 * - Retry count is bounded (maxRetries = 2 by default).
 * - Timeout is per attempt.
 * - Returns a [Result] instead of throwing, so callers can render fallback UI safely.
 */
suspend inline fun <T> runResultWithRetry(
    timeoutMillis: Long = 15_000L,
    maxRetries: Int = 2,
    initialDelayMillis: Long = 400L,
    maxDelayMillis: Long = 1_600L,
    crossinline block: suspend () -> Result<T>,
): Result<T> {
    require(maxRetries >= 0) { "maxRetries must be >= 0" }

    var currentDelayMillis = initialDelayMillis.coerceAtLeast(0L)
    var lastError: Throwable? = null

    repeat(maxRetries + 1) { attempt ->
        val result = try {
            withTimeoutOrNull(timeoutMillis) { block() }
                ?: Result.failure(
                    TimeoutException("Request timed out after ${timeoutMillis}ms.")
                )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }

        if (result.isSuccess) return result

        lastError = result.exceptionOrNull()
        if (attempt < maxRetries) {
            delay(currentDelayMillis)
            currentDelayMillis = (currentDelayMillis * 2).coerceAtMost(maxDelayMillis)
        }
    }

    return Result.failure(lastError ?: IllegalStateException("Unknown network failure."))
}
