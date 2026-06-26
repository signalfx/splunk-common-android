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

import com.splunk.rum.common.utils.extensions.getFloat
import org.json.JSONObject
import kotlin.collections.component1
import kotlin.collections.component2

internal sealed interface Value

@JvmInline
internal value class StringValue(val value: String) : Value

@JvmInline
internal value class IntValue(val value: Int) : Value

@JvmInline
internal value class LongValue(val value: Long) : Value

@JvmInline
internal value class FloatValue(val value: Float) : Value

@JvmInline
internal value class BooleanValue(val value: Boolean) : Value

@JvmInline
internal value class StringMapValue(val value: Map<String, String>) : Value

internal fun serializeFromMap(map: HashMap<String, Value>): String {
    val json = JSONObject()
    for ((key, value) in map) {
        val entry = JSONObject()

        when (value) {
            is StringValue -> {
                entry.put("type", "String")
                entry.put("value", value.value)
            }
            is IntValue -> {
                entry.put("type", "Int")
                entry.put("value", value.value)
            }
            is LongValue -> {
                entry.put("type", "Long")
                entry.put("value", value.value)
            }
            is FloatValue -> {
                entry.put("type", "Float")
                entry.put("value", value.value)
            }
            is BooleanValue -> {
                entry.put("type", "Boolean")
                entry.put("value", value.value)
            }
            is StringMapValue -> {
                entry.put("type", "StringMap")

                val valueJson = JSONObject()

                for ((mapKey, mapValue) in value.value)
                    valueJson.put(mapKey, mapValue)

                entry.put("value", valueJson)
            }
        }

        json.put(key, entry)
    }

    return json.toString(2)
}

internal fun deserializeToMap(jsonString: String, map: MutableMap<String, Value> = LinkedHashMap()): Map<String, Value> {
    val json = JSONObject(jsonString)

    for (key in json.keys()) {
        val entry = json.getJSONObject(key)

        map[key] = when (val entryType = entry.getString("type")) {
            "String" -> {
                StringValue(entry.getString("value"))
            }
            "Int" -> {
                IntValue(entry.getInt("value"))
            }
            "Long" -> {
                LongValue(entry.getLong("value"))
            }
            "Float" -> {
                FloatValue(entry.getFloat("value"))
            }
            "Boolean" -> {
                BooleanValue(entry.getBoolean("value"))
            }
            "StringMap" -> {
                val stringMap = LinkedHashMap<String, String>()
                val value = entry.getJSONObject("value")

                for (mapKey in value.keys())
                    stringMap[mapKey] = value.getString(mapKey)

                StringMapValue(stringMap)
            }
            else -> {
                throw IllegalArgumentException("Unsupported value type $entryType")
            }
        }
    }

    return map
}
