/*
Copyright 2026 Splunk Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.splunk.rum.common.storage.preferences

import com.splunk.rum.common.logger.Logger
import com.splunk.rum.common.storage.cache.ISimplePermanentCache
import com.splunk.rum.common.utils.Lock
import com.splunk.rum.common.utils.thread.NamedThreadFactory
import java.io.Closeable
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * An asynchronously loaded preference store.
 *
 * Values live in [map] and are persisted as one JSON document. Initial loading and delayed saves
 * run on [scheduler], while [commit] writes the latest snapshot on the calling thread.
 *
 * A closed instance is inert: reads return empty values and mutations and commits are ignored.
 */
class Preferences internal constructor(
    private val createPermanentCache: () -> ISimplePermanentCache,
    private val scheduler: ScheduledThreadPoolExecutor
) : IPreferences, Closeable {

    constructor(permanentCache: ISimplePermanentCache) :
        this(createPermanentCache = { permanentCache }, scheduler = createScheduler())

    /**
     * A FAILED store releases waiting callers and continues with an empty in-memory map. When the
     * cache was created before reading or parsing failed, a later mutation can replace the bad
     * persisted content; when cache creation itself failed, mutations remain memory-only.
     */
    private enum class State {
        LOADING,
        READY,
        FAILED,
        CLOSED
    }

    private val map = hashMapOf<String, Value>()

    // lockLoad is a startup barrier, lifecycleLock protects state and save bookkeeping, and
    // saveLock ensures the worker and a caller executing commit() never write simultaneously.
    private val lockLoad = Lock()
    private val saveLock = ReentrantLock()
    private val lifecycleLock = Any()

    @Volatile
    private var state = State.LOADING

    @Volatile
    private var loadTask: Future<*>? = null

    private var permanentCache: ISimplePermanentCache? = null
    private var lastScheduledTask: ScheduledFuture<*>? = null

    // Tokens invalidate canceled tasks that may still start. Versions detect mutations made while
    // a snapshot is being written so that a follow-up save can be scheduled.
    private var scheduledTaskToken = 0L
    private var mutationVersion = 0L
    private var persistedVersion = 0L

    init {
        startLoading()
    }

    override fun putString(key: String, value: String): IPreferences = putValue(key, StringValue(value))

    override fun putInt(key: String, value: Int): IPreferences = putValue(key, IntValue(value))

    override fun putLong(key: String, value: Long): IPreferences = putValue(key, LongValue(value))

    override fun putFloat(key: String, value: Float): IPreferences = putValue(key, FloatValue(value))

    override fun putBoolean(key: String, value: Boolean): IPreferences = putValue(key, BooleanValue(value))

    override fun putStringMap(key: String, value: Map<String, String>): IPreferences =
        putValue(key, StringMapValue(value))

    /** Cancels a pending delayed save and writes the current snapshot before returning. */
    override fun commit() {
        if (state == State.CLOSED) return
        lockLoad.waitToUnlock()

        val snapshot = synchronized(lifecycleLock) {
            if (state == State.CLOSED) return

            cancelScheduledSave()
            createSnapshot() ?: return
        }

        saveSnapshot(snapshot)
    }

    override fun remove(key: String): IPreferences {
        mutate { map.remove(key) }
        return this
    }

    override fun clear(): IPreferences {
        mutate { map.clear() }
        return this
    }

    override fun getString(key: String): String? = getValue(key)

    override fun getInt(key: String): Int? = getValue(key)

    override fun getLong(key: String): Long? = getValue(key)

    override fun getFloat(key: String): Float? = getValue(key)

    override fun getBoolean(key: String): Boolean? = getValue(key)

    override fun getStringMap(key: String): Map<String, String>? = getValue(key)

    override operator fun contains(key: String): Boolean {
        if (state == State.CLOSED) return false
        lockLoad.waitToUnlock()

        return synchronized(lifecycleLock) {
            state != State.CLOSED && synchronized(map) { map.contains(key) }
        }
    }

    override fun size(): Int {
        if (state == State.CLOSED) return 0
        lockLoad.waitToUnlock()

        return synchronized(lifecycleLock) {
            if (state == State.CLOSED) 0 else synchronized(map) { map.size }
        }
    }

    /**
     * Discards this owner without flushing or otherwise modifying its cache.
     *
     * Unlocking [lockLoad] also releases readers when the underlying read ignores interruption.
     */
    override fun close() {
        synchronized(lifecycleLock) {
            if (state == State.CLOSED) return

            state = State.CLOSED
            loadTask?.cancel(true)
            loadTask = null
            cancelScheduledSave()
            lockLoad.unlock()
            scheduler.shutdownNow()
        }
    }

    /** Starts initial loading and blocks public operations through [lockLoad] until it finishes. */
    private fun startLoading() {
        lockLoad.lock()

        synchronized(lifecycleLock) {
            if (state == State.CLOSED) {
                lockLoad.unlock()
                return
            }

            try {
                loadTask = scheduler.submit(::performLoad)
            } catch (exception: RejectedExecutionException) {
                state = State.FAILED
                lockLoad.unlock()
                Logger.w(TAG, "startLoading(): Failed to submit the loading task due to ${exception.message}!")
            }
        }
    }

    /**
     * Creates the cache and loads its JSON on the worker thread.
     *
     * Deserialization uses a temporary map so malformed data is never partially published. The
     * lockLoad is always released, including after cache, read, or parsing failures.
     */
    private fun performLoad() {
        try {
            if (state == State.CLOSED) return
            val cache = createPermanentCache()
            if (state == State.CLOSED) return

            synchronized(lifecycleLock) {
                if (state == State.CLOSED) return
                permanentCache = cache
            }

            val bytes = cache.readBytes()
            if (state == State.CLOSED) return

            val loadedMap = hashMapOf<String, Value>()
            if (bytes != null && bytes.isNotEmpty()) {
                deserializeToMap(bytes.toString(Charsets.UTF_8), loadedMap)
            }
            if (state == State.CLOSED) return

            synchronized(lifecycleLock) {
                if (state == State.CLOSED) return
                synchronized(map) {
                    map.clear()
                    map.putAll(loadedMap)
                }
                state = State.READY
            }
        } catch (exception: Exception) {
            synchronized(lifecycleLock) {
                if (state != State.CLOSED) state = State.FAILED
            }
            if (state != State.CLOSED) {
                Logger.w(TAG, "performLoad(): Failed to load preferences due to ${exception.message}!")
            }
        } finally {
            lockLoad.unlock()
        }
    }

    /** Applies an in-memory change and records that a newer persistent snapshot is required. */
    private fun mutate(operation: () -> Unit) {
        if (state == State.CLOSED) return
        lockLoad.waitToUnlock()

        synchronized(lifecycleLock) {
            if (state == State.CLOSED) return
            synchronized(map) {
                operation()
                mutationVersion++
            }
            scheduleSave()
        }
    }

    /**
     * Schedules one delayed save when data is dirty and a cache is available.
     *
     * Must be called while holding [lifecycleLock]. An active task is allowed to finish; if a
     * mutation arrives during its write, version tracking schedules the next task afterward.
     */
    private fun scheduleSave() {
        if (state == State.CLOSED || permanentCache == null || mutationVersion <= persistedVersion) return
        if (lastScheduledTask?.isDone == false) return

        val token = ++scheduledTaskToken
        try {
            lastScheduledTask = scheduler.schedule(
                { executeScheduledSaveTask(token) },
                DEBOUNCE_TIME,
                TimeUnit.MILLISECONDS
            )
        } catch (exception: RejectedExecutionException) {
            if (state != State.CLOSED) {
                Logger.w(TAG, "scheduleSaveLocked(): Failed to submit a save task due to ${exception.message}!")
            }
        }
    }

    /** Writes the snapshot owned by [token], then schedules another save if data changed meanwhile. */
    private fun executeScheduledSaveTask(token: Long) {
        if (state == State.CLOSED) return
        lockLoad.waitToUnlock()

        val snapshot = synchronized(lifecycleLock) {
            if (state == State.CLOSED || token != scheduledTaskToken) return
            createSnapshot()
        }

        if (snapshot != null) saveSnapshot(snapshot)

        synchronized(lifecycleLock) {
            if (token == scheduledTaskToken) {
                lastScheduledTask = null
                scheduleSave()
            }
        }
    }

    /** Captures serialized bytes and their mutation version while [lifecycleLock] is held. */
    private fun createSnapshot(): Snapshot? {
        val cache = permanentCache ?: return null
        return synchronized(map) {
            Snapshot(cache, serializeFromMap(map).toByteArray(), mutationVersion)
        }
    }

    /** Serializes access to the cache and marks the completed snapshot version as persisted. */
    private fun saveSnapshot(snapshot: Snapshot) {
        saveLock.lock()
        try {
            if (state == State.CLOSED) return
            snapshot.cache.writeBytes(snapshot.bytes)
            synchronized(lifecycleLock) {
                if (state != State.CLOSED) {
                    persistedVersion = maxOf(persistedVersion, snapshot.version)
                    scheduleSave()
                }
            }
        } finally {
            saveLock.unlock()
        }
    }

    /** Cancels the pending task and invalidates it in case cancellation races with task startup. */
    private fun cancelScheduledSave() {
        scheduledTaskToken++
        lastScheduledTask?.cancel(true)
        lastScheduledTask = null
    }

    private fun putValue(key: String, value: Value): IPreferences {
        mutate { map[key] = value }
        return this
    }

    private inline fun <reified T> getValue(key: String): T? {
        if (state == State.CLOSED) return null
        lockLoad.waitToUnlock()

        val wrappedValue = getWrappedValue(key) ?: return null

        val value: Any = when (wrappedValue) {
            is StringValue -> wrappedValue.value
            is IntValue -> wrappedValue.value
            is LongValue -> wrappedValue.value
            is FloatValue -> wrappedValue.value
            is BooleanValue -> wrappedValue.value
            is StringMapValue -> wrappedValue.value
        }

        return value as? T
            ?: throw IllegalArgumentException("Expected a value of type ${T::class}, but got ${value::class}!")
    }

    private fun getWrappedValue(key: String): Value? =
        synchronized(lifecycleLock) {
            if (state == State.CLOSED) null else synchronized(map) { map[key] }
        }

    private data class Snapshot(
        val cache: ISimplePermanentCache,
        val bytes: ByteArray,
        val version: Long
    )

    companion object {
        private const val TAG = "Preferences"
        private const val DEBOUNCE_TIME = 500L

        @JvmStatic
        fun create(createPermanentCache: () -> ISimplePermanentCache): Preferences =
            Preferences(createPermanentCache, createScheduler())

        private fun createScheduler() = ScheduledThreadPoolExecutor(1, NamedThreadFactory("SplunkPreferences"))
    }
}
