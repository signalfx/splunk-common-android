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

package com.splunk.android.common.job

import android.content.Context
import com.splunk.android.common.storage.cache.FileSimplePermanentCache
import com.splunk.android.common.storage.extensions.noBackupFilesDirCompat
import com.splunk.android.common.storage.filemanager.FileManagerFactory
import com.splunk.android.common.storage.preferences.Preferences
import com.splunk.android.common.utils.extensions.toJSONObject
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JobIdStorage(
    context: Context,
    isEncrypted: Boolean = true
) {
    private val preferences: Preferences

    /**
     * Because the internal policy of [JobSchedulerWorker] does not allow IDs as [String] we
     * have to map each [String] to unique [Int].
     */
    private var stringIntIdMap: StringIntIdMap
        set(value) {
            preferences.putString(JOB_ID_TABLE, value.toJSONObject().toString())
        }
        get() {
            val rawString = preferences.getString(JOB_ID_TABLE) ?: return StringIntIdMap()
            return StringIntIdMap.fromJSONObject(rawString.toJSONObject())
        }

    /**
     * In order to prevent problems when creating new id. We keep track of the latest id
     * and increment it by one every time new id is requested.
     */
    private var lastId: Int
        set(value) {
            preferences.putInt(JOB_ID_TABLE_LAST_NUMBER, value)
        }
        get() = preferences.getInt(JOB_ID_TABLE_LAST_NUMBER) ?: 0

    private val readWriteLock = ReentrantLock()

    init {
        val preferencesFile = File(context.noBackupFilesDirCompat, "preferences/job.dat")
        val preferencesFileManager = FileManagerFactory.createConditionedEncryptedFileManager("Jobs-Preferences$VERSION", isEncrypted)
        preferences = Preferences(FileSimplePermanentCache(preferencesFile, preferencesFileManager))
    }

    fun getOrCreateId(stringId: String): Int {
        return readWriteLock.withLock {
            val map = stringIntIdMap
            val latestId = lastId
            val id = map[stringId] ?: run {
                val newId = if (latestId >= ID_LIMIT) {
                    0
                } else {
                    latestId + 1
                }
                this.lastId = newId
                newId
            }

            // This should never happen. But as a safety mechanism it is here.
            if (map.size > SAFETY_SIZE) {
                map.clear()
            }

            map[stringId] = id

            this.stringIntIdMap = map
            id
        }
    }

    fun get(id: String): Int? {
        return readWriteLock.withLock {
            stringIntIdMap[id]
        }
    }

    fun getAll(): Map<String, Int> = readWriteLock.withLock { stringIntIdMap }

    fun getAllWithPrefix(prefix: String): Map<String, Int> {
        return readWriteLock.withLock {
            val map = stringIntIdMap
            map.filter { it.key.startsWith(prefix) }
        }
    }

    fun deleteAllWithPrefix(prefix: String) {
        readWriteLock.withLock {
            val map = stringIntIdMap
            map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) }
            stringIntIdMap = map
        }
    }

    fun delete(key: String) {
        readWriteLock.withLock {
            val map = stringIntIdMap
            map.remove(key)
            stringIntIdMap = map
        }
    }

    /**
     * Json serializable/deserializable wrapper for [HashMap].
     */
    internal class StringIntIdMap : HashMap<String, Int>() {
        fun toJSONObject(): JSONObject {
            return JSONObject().apply {
                forEach {
                    put(it.key, it.value)
                }
            }
        }

        companion object {
            fun fromJSONObject(jsonObject: JSONObject) = StringIntIdMap().apply {
                jsonObject.keys().forEach {
                    put(it, jsonObject[it] as Int)
                }
            }
        }
    }

    companion object {
        private const val ID_LIMIT = Int.MAX_VALUE - 10000
        private const val SAFETY_SIZE = 10000

        private var instance: JobIdStorage? = null

        const val JOB_ID_TABLE = "JOB_ID_TABLE"
        const val JOB_ID_TABLE_LAST_NUMBER = "JOB_ID_TABLE_LAST_NUMBER"

        /**
         * If storage model changes this version needs to be changed. This will ensure data consistency.
         * The storage will wipe all the legacy data (older version than this one).
         */

        private const val VERSION = 6

        fun init(context: Context, isEncrypted: Boolean): JobIdStorage {
            return instance ?: JobIdStorage(context, isEncrypted).also { instance = it }
        }
    }
}
