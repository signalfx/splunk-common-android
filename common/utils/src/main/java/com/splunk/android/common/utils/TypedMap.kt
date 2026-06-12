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

package com.splunk.android.common.utils

import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Version 2.0.0 supports only String, other types are prepared for properties with data type suffix.
 * NOTE: Serialization and Deserialization heavily depends on this.
 */
class TypedMap(private val keepRemovedEntries: Boolean = false) {

    constructor(typedMap: TypedMap, keepRemovedEntries: Boolean = false) : this(keepRemovedEntries) {
        this.internalMap.putAll(typedMap.internalMap)
    }

    val internalMap: ConcurrentHashMap<String, Entry> = ConcurrentHashMap()
    var observers: MutableList<Observer> = Collections.synchronizedList(mutableListOf())

    //region put()

    fun putString(name: String, value: String?): Entry.StringType? = put(name, value) { Entry.StringType(it) }

    private inline fun <VALUE, reified ENTRY> put(
        name: String,
        value: VALUE?,
        createEntry: (value: VALUE) -> Entry
    ): ENTRY? {
        var entry: Entry? = null
        if (value == null) {
            remove(name)
        } else {
            entry = createEntry(value)
            internalMap[name] = entry
            observers.forEach { it.onPut(name, entry) }
        }

        return entry as ENTRY
    }

    //endregion

    //region get()

    fun getString(name: String): Result<String> {
        return when (val entry = internalMap[name]) {
            is Entry.StringType -> Result.Success(entry.value)
            is Entry.RemovedType -> Result.Success(null)
            null -> Result.Success(null)
        }
    }

    //endregion

    fun remove(name: String) {
        val entry = internalMap[name]
        if (internalMap.containsKey(name) && entry != null) {
            internalMap.remove(name)
            observers.forEach { it.onRemove(name, entry) }
            if (keepRemovedEntries) {
                internalMap[name] = Entry.RemovedType
            }
        }
    }

    fun clear() {
        if (keepRemovedEntries) {
            internalMap.map { Entry.RemovedType }
        } else {
            internalMap.clear()
        }
        observers.forEach { it.onClear() }
    }

    fun mergeWith(toMerge: TypedMap?): TypedMap {
        val merged = TypedMap(this)

        toMerge?.internalMap?.forEach {
            if (it.value !is Entry.RemovedType || keepRemovedEntries) {
                merged.internalMap[it.key] = it.value
            }
        }

        return merged
    }

    fun clearRemovedEntities() {
        if (keepRemovedEntries) {
            internalMap.entries.removeAll { it.value is Entry.RemovedType }
        }
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()

        internalMap.forEach {
            when (val entry = it.value) {
                is Entry.RemovedType -> json.put(it.key, JSONObject.NULL)
                is Entry.StringType -> json.put(it.key, entry.value)
            }
        }

        return json
    }

    sealed interface Entry {
        data class StringType(val value: String) : Entry
        object RemovedType : Entry
    }

    sealed class Result<out T> {
        data class Success<out T>(val value: T?) : Result<T>()
        // data class WrongType(val requested: String, val stored: String) : Result<Nothing>()
    }

    interface Observer {
        fun onPut(name: String, entry: Entry)
        fun onRemove(name: String, entry: Entry)
        fun onClear()
    }

    companion object {
        fun fromJson(json: JSONObject?, keepRemovedEntries: Boolean): TypedMap {
            val typedMap = TypedMap()
            json?.keys()?.forEach { name ->
                if (json.isNull(name) && keepRemovedEntries) {
                    typedMap.internalMap[name] = Entry.RemovedType
                } else {
                    typedMap.putString(name, json.getString(name))
                }
            }
            return typedMap
        }
    }
}
