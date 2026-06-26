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
import org.json.JSONException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class Preferences(private val permanentCache: ISimplePermanentCache) : IPreferences {

    private val map = hashMapOf<String, Value>()
    private val lockLoad = Lock()
    private val lockSave = Lock()
    private val scheduler = ScheduledThreadPoolExecutor(1, NamedThreadFactory("Preferences"))

    @Volatile
    private var lastScheduledTask: ScheduledFuture<*>? = null

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

    override fun commit() {
        lockLoad.waitToUnlock()

        synchronized(scheduler) {
            lastScheduledTask?.cancel(true)
            lastScheduledTask = null
        }

        val jsonString = synchronized(map) {
            serializeFromMap(map)
        }

        lockSave.waitToUnlock()
        permanentCache.writeBytes(jsonString.toByteArray())
    }

    override fun remove(key: String): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map -= key
        }

        apply()

        return this
    }

    override fun clear(): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map.clear()
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

        scheduler.safeSubmit {
            val jsonString = permanentCache.readBytes()?.toString(Charsets.UTF_8)

            if (jsonString?.isEmpty() != false) {
                lockLoad.unlock()
                return@safeSubmit
            }

            synchronized(map) {
                try {
                    deserializeToMap(jsonString, map)
                } catch (e: JSONException) {
                    // If cache gets corrupted because of sudden crash it needs to be cleared
                    commit()
                    Logger.w(TAG, "deserializeAndFillMap(): Failed to deserialize a String due to ${e.message}!")
                }
            }

            lockLoad.unlock()
        }
    }

    private fun apply() {
        lockSave.waitToUnlock()

        synchronized(scheduler) {
            if (lastScheduledTask?.isDone != false)
                lastScheduledTask = scheduler.schedule(::performApplyLogic, DEBOUNCE_TIME, TimeUnit.MILLISECONDS)
        }
    }

    private fun performApplyLogic() {
        lockLoad.waitToUnlock()

        val jsonString = synchronized(map) {
            serializeFromMap(map)
        }

        lockSave.lock()
        try {
            permanentCache.writeBytes(jsonString.toByteArray())
        } finally {
            lockSave.unlock()
        }
    }

    private fun putValue(key: String, value: Value): IPreferences {
        lockLoad.waitToUnlock()

        synchronized(map) {
            map[key] = value
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

        return value as? T ?: throw IllegalArgumentException("Expected a value of type ${T::class}, but got ${value::class}!")
    }

    private companion object {
        const val TAG = "Preferences"
        const val DEBOUNCE_TIME = 500L // 500ms
    }
}
