package com.metrolist.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.metrolist.music.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.properties.ReadOnlyProperty
import java.util.WeakHashMap

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private const val DATASTORE_SYNC_READ_TIMEOUT_MS = 250L

private val dataStoreScopeCache = WeakHashMap<DataStore<Preferences>, CoroutineScope>()
private val dataStoreSnapshotCache = WeakHashMap<DataStore<Preferences>, MutableStateFlow<Preferences?>>()
private val dataStoreCollectorsStarted = WeakHashMap<DataStore<Preferences>, Boolean>()
private val dataStoreCacheLock = Any()

private fun DataStore<Preferences>.snapshotState(): MutableStateFlow<Preferences?> =
    synchronized(dataStoreCacheLock) {
        dataStoreSnapshotCache.getOrPut(this) { MutableStateFlow(null) }
    }

private fun DataStore<Preferences>.snapshotScope(): CoroutineScope =
    synchronized(dataStoreCacheLock) {
        dataStoreScopeCache.getOrPut(this) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
    }

fun DataStore<Preferences>.warmupCache() {
    val shouldStart =
        synchronized(dataStoreCacheLock) {
            if (dataStoreCollectorsStarted[this] == true) {
                false
            } else {
                dataStoreCollectorsStarted[this] = true
                true
            }
        }
    if (!shouldStart) return

    val snapshotState = snapshotState()
    snapshotScope().launch {
        runCatching {
            data.collect { snapshot ->
                snapshotState.value = snapshot
            }
        }.onFailure {
            // Keep sync getters bounded even if cache prewarm fails.
            reportException(it)
            synchronized(dataStoreCacheLock) {
                dataStoreCollectorsStarted[this@warmupCache] = false
            }
        }
    }
}

fun <T> DataStore<Preferences>.peek(key: Preferences.Key<T>): T? {
    warmupCache()
    return snapshotState().value?.get(key)
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    peek(key) ?: runBlocking(Dispatchers.IO) {
        // Bound sync fallback to reduce ANR risk when called from main-thread callers.
        withTimeoutOrNull(DATASTORE_SYNC_READ_TIMEOUT_MS) {
            data.first()[key]
        }
    }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    get(key) ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    context.dataStore.warmupCache()
    val initialValue = context.dataStore.peek(key) ?: defaultValue

    val state =
        remember {
            context.dataStore.data
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(initialValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    context.dataStore.warmupCache()

    val initialValue = context.dataStore.peek(key).toEnum(defaultValue = defaultValue)
    val state =
        remember {
            context.dataStore.data
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(initialValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    coroutineScope.launch {
                        context.dataStore.edit {
                            it[key] = value.name
                        }
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
