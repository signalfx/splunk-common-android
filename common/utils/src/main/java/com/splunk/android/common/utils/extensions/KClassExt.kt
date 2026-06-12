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

package com.splunk.android.common.utils.extensions

import java.lang.reflect.Field
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
fun <T> KClass<*>.getStatic(fieldName: String): T? {
    val field = findField(fieldName)
    field.makeReadable()

    return field.get(null) as? T
}

fun <T : Any> KClass<*>.setStatic(fieldName: String, value: T?) {
    val field = findField(fieldName)
    field.makeWritable()

    field.set(null, value)
}

@Throws(NoSuchFieldException::class)
fun KClass<*>.findField(name: String): Field {
    return java.findField(name)
}
