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

import java.lang.reflect.Field

val Any.simpleClassName: String
    get() = javaClass.simpleName

@Throws(NoSuchFieldException::class)
inline fun <reified T : Any> Any.invoke(methodName: String, vararg paramsPairs: Pair<Any?, Class<*>>): T? {
    val classes = Array(paramsPairs.size) { paramsPairs[it].second }
    val method = javaClass.findMethod(methodName, T::class.java, *classes)
    val params = Array(paramsPairs.size) { paramsPairs[it].first }

    return method.invoke(this, *params) as? T
}

fun <T : Any> Any.get(fieldName: String): T? {
    val field = javaClass.findField(fieldName)
    return get(field)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> Any.get(field: Field): T? {
    field.makeReadable()
    return field.get(this) as? T
}

fun <T : Any> Any.set(fieldName: String, value: T?) {
    val field = javaClass.findField(fieldName)
    set(field, value)
}

fun <T : Any> Any.set(field: Field, value: T?) {
    field.makeWritable()
    field.set(this, value)
}

val Any.identity: String
    get() = "${this::class.java.name}@${Integer.toHexString(System.identityHashCode(this))}"
