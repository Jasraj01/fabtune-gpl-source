package com.metrolist.music.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Process-wide CastContext manager.
 * Provides idempotent, thread-safe lazy initialization without blocking app startup.
 */
object CastManager {

    private val initializationLock = Any()

    @Volatile
    private var castContext: CastContext? = null

    @Volatile
    private var initializationDeferred: CompletableDeferred<CastContext?>? = null

    private val initializationExecutor: ExecutorService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CastContextInit").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
    }

    fun peek(): CastContext? = castContext

    suspend fun getOrInitialize(context: Context): CastContext? {
        castContext?.let { return it }

        val (deferred, shouldInitialize) = synchronized(initializationLock) {
            castContext?.let { return it }
            initializationDeferred?.let { existing ->
                existing to false
            } ?: run {
                val created = CompletableDeferred<CastContext?>()
                initializationDeferred = created
                created to true
            }
        }

        if (shouldInitialize) {
            val resolved = runCatching {
                requestCastContext(context.applicationContext)
            }.onFailure { error ->
                Timber.e(error, "CastContext async initialization failed")
            }.getOrNull()

            synchronized(initializationLock) {
                if (resolved != null) {
                    castContext = resolved
                }
                if (!deferred.isCompleted) {
                    deferred.complete(resolved)
                }
                if (initializationDeferred === deferred) {
                    initializationDeferred = null
                }
            }
        }

        return deferred.await()
    }

    private suspend fun requestCastContext(context: Context): CastContext {
        return awaitTask(CastContext.getSharedInstance(context, initializationExecutor))
    }

    private suspend fun awaitTask(task: Task<CastContext>): CastContext {
        return suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { value ->
                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }.addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }.addOnCanceledListener {
                continuation.cancel()
            }
        }
    }
}
