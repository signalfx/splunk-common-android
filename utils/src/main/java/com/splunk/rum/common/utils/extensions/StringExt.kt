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

package com.splunk.rum.common.utils.extensions

import com.splunk.rum.common.utils.Barrier
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KClass

private val CLASS_LOADER: ClassLoader = Barrier::class.java.classLoader ?: ClassLoader.getSystemClassLoader()

fun String.toKClass(): KClass<*>? {
    return toClass()?.kotlin
}

fun String.toJSONObject(): JSONObject {
    return JSONObject(this)
}

fun String.toJSONArray(): JSONArray {
    return JSONArray(this)
}

fun String.toClass(): Class<*>? {
    return try {
        Class.forName(this, false, CLASS_LOADER)
    } catch (_: Throwable) {
        null
    }
}
