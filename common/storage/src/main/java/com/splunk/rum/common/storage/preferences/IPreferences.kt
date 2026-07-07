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

interface IPreferences {
    fun putString(key: String, value: String): IPreferences

    fun putInt(key: String, value: Int): IPreferences

    fun putLong(key: String, value: Long): IPreferences

    fun putFloat(key: String, value: Float): IPreferences

    fun putBoolean(key: String, value: Boolean): IPreferences

    fun putStringMap(key: String, value: Map<String, String>): IPreferences

    fun commit()

    fun remove(key: String): IPreferences

    fun clear(): IPreferences

    fun getString(key: String): String?

    fun getInt(key: String): Int?

    fun getLong(key: String): Long?

    fun getFloat(key: String): Float?

    fun getBoolean(key: String): Boolean?

    fun getStringMap(key: String): Map<String, String>?

    operator fun contains(key: String): Boolean

    fun size(): Int
}
