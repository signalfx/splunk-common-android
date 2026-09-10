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
import com.splunk.rum.common.utils.extensions.safeSubmit
import com.splunk.rum.common.utils.thread.NamedThreadFactory
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class Preferences private constructor(
    private val createPermanentCache: () -> ISimplePermanentCache
) : IPreferences {

    /**
     * Kept for existing consumers, including Session Replay and JobIdStorage.
     * The cache is still initialized by the Preferences worker, as it is for the factory form.
     */
    constructor(permanentCache: ISimplePermanentCache) : this({ permanentCache })

    private val map = hashMapOf<String, Value>()
    private val lockLoad = Lock()

    // lockLoad is a load barrier, not a mutual-exclusion lock. Any() gives synchronized() the
    // actual monitor needed to serialize async apply() and synchronous commit() writes.
    private val lockSave = Any()
    private val scheduler = ScheduledThreadPoolExecutor(1, NamedThreadFactory("SplunkPreferences"))

    /** Incremented under [map] whenever a mutation needs to be persisted. */
    private var mutationVersion = 0L

    /**
     * Created and assigned by the Preferences worker so cache construction and disk I/O do not
     * happen on the caller's thread.
     */
    @Volatile
    private var permanentCache: ISimplePermanentCache? = null

    @Volatile
    private var lastScheduledSaveTask: ScheduledFuture<*>? = null

    /** Identifies the current scheduled write so an invalidated save task cannot touch newer work. */
    private var currentScheduledSaveToken = 0L

    init {
        loadFromPermanentCache()
    }

    override fun putString(key: String, value: String): IPreferences {
        return putValue(key, StringValue(value))
    }

    override fun putInt(key: String, value: Int): IPreferences {
        return putValue(key, IntValue(value))
    }

    override fun putLong(key: String, value: Long): IPreferences {
        return putValue(key, LongValue(value))
    }

    override fun putFloat(key: String, value: Float): IPreferences {
        return putValue(key, FloatValue(value))
    }

    override fun putBoolean(key: String, value: Boolean): IPreferences {
        return putValue(key, BooleanValue(value))
    }

    override fun putStringMap(key: String, value: Map<String, String>): IPreferences {
        return putValue(key, StringMapValue(value))
    }

    /**
     * Writes the current in-memory values before returning. This is intentionally synchronous for
     * callers that must persist state before a process can terminate, such as crash handling.
     */
    override fun commit() {
        lockLoad.waitToUnlock()

        val cache = permanentCache ?: return

        // A pending debounced write is no longer needed. If it is already running, lockSave below
        // makes commit wait for it and then writes the latest map state itself.
        synchronized(scheduler) {
            lastScheduledSaveTask?.cancel(false)
            lastScheduledSaveTask = null
            currentScheduledSaveToken++
        }

        synchronized(lockSave) {
            val jsonString = synchronized(map) {
                serializeFromMap(map)
            }
            cache.writeBytes(jsonString.toByteArray())
        }
    }

    override fun remove(key: String): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map -= key
            mutationVersion++
        }

        apply()

        return this
    }

    override fun clear(): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map.clear()
            mutationVersion++
        }

        apply()

        return this
    }

    override fun getString(key: String): String? = getValue(key)

    override fun getInt(key: String): Int? = getValue(key)

    override fun getLong(key: String): Long? = getValue(key)

    override fun getFloat(key: String): Float? = getValue(key)

    override fun getBoolean(key: String): Boolean? = getValue(key)

    override fun getStringMap(key: String): Map<String, String>? = getValue(key)

    override operator fun contains(key: String): Boolean {
        lockLoad.waitToUnlock()
        return synchronized(map) { map.contains(key) }
    }

    override fun size(): Int {
        lockLoad.waitToUnlock()
        return synchronized(map) { map.size }
    }

    private fun loadFromPermanentCache() {
        lockLoad.lock()

        try {
            scheduler.safeSubmit {
                try {
                    val cache = createPermanentCache()
                    permanentCache = cache
                    val jsonString = cache.readBytes()?.toString(Charsets.UTF_8)

                    if (jsonString?.isEmpty() != false) {
                        return@safeSubmit
                    }

                    var isCorrupt = false
                    val loadedMap = try {
                        // Parse off the shared map so malformed input cannot leave a partial state visible.
                        deserializeToMap(jsonString)
                    } catch (e: Exception) {
                        isCorrupt = true
                        Logger.w(TAG, "deserializeAndFillMap(): Failed to deserialize preferences: ${e.message}")
                        emptyMap()
                    }

                    synchronized(map) {
                        map.clear()
                        map.putAll(loadedMap)
                    }

                    if (isCorrupt) {
                        // Clear corrupted data directly while the load barrier is held; normal writes
                        // can continue after the worker publishes the empty map.
                        cache.writeBytes("{}".toByteArray())
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "loadFromPermanentCache(): Failed to initialize preferences: ${e.message}")
                } finally {
                    // Every caller waits on this barrier; it must be released for all worker outcomes.
                    lockLoad.unlock()
                }
            }
        } catch (e: Throwable) {
            lockLoad.unlock()
            Logger.w(TAG, "loadFromPermanentCache(): Failed to schedule preferences load: ${e.message}")
        }
    }

    private fun apply() {
        synchronized(scheduler) {
            if (lastScheduledSaveTask?.isDone == false) return

            scheduleApplyLocked()
        }
    }

    /** Must be called while holding the scheduler monitor. */
    private fun scheduleApplyLocked() {
        val saveToken = ++currentScheduledSaveToken
        lastScheduledSaveTask = scheduler.schedule(
            { performApplyLogic(saveToken) },
            DEBOUNCE_TIME,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun performApplyLogic(saveToken: Long) {
        lockLoad.waitToUnlock()

        val cache = permanentCache ?: return
        var snapshotVersion: Long? = null

        try {
            synchronized(lockSave) {
                // Snapshot and write while holding the same lock as commit(), so a debounced write
                // cannot serialize an older map and overwrite a newer synchronous commit.
                val snapshot = synchronized(map) {
                    serializeFromMap(map) to mutationVersion
                }
                snapshotVersion = snapshot.second
                cache.writeBytes(snapshot.first.toByteArray())
            }
        } finally {
            synchronized(scheduler) {
                // A commit may have invalidated this task while it was running. Do not clear or
                // replace a newer task that was scheduled after that commit.
                if (saveToken != currentScheduledSaveToken) return@synchronized

                lastScheduledSaveTask = null

                val mapChangedDuringWrite = snapshotVersion?.let { version ->
                    synchronized(map) { mutationVersion != version }
                } == true

                if (mapChangedDuringWrite) {
                    scheduleApplyLocked()
                }
            }
        }
    }

    private fun putValue(key: String, value: Value): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map[key] = value
            mutationVersion++
        }

        apply()

        return this
    }

    private inline fun <reified T> getValue(key: String): T? {
        lockLoad.waitToUnlock()

        val wrappedValue = synchronized(map) { map[key] } ?: return null

        val value: Any = when (wrappedValue) {
            is StringValue -> wrappedValue.value
            is IntValue -> wrappedValue.value
            is LongValue -> wrappedValue.value
            is FloatValue -> wrappedValue.value
            is BooleanValue -> wrappedValue.value
            is StringMapValue -> wrappedValue.value
        }

        return value as? T
    }

    companion object {
        private const val TAG = "Preferences"
        private const val DEBOUNCE_TIME = 500L // 500ms

        @JvmStatic
        fun create(factory: () -> ISimplePermanentCache): Preferences = Preferences(factory)
    }
}
