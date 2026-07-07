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

package com.splunk.rum.common.utils.reflector

import androidx.annotation.Keep
import com.splunk.rum.common.utils.extensions.findField
import com.splunk.rum.common.utils.extensions.findMethod
import com.splunk.rum.common.utils.extensions.makeReadable
import com.splunk.rum.common.utils.extensions.makeWritable
import com.splunk.rum.common.utils.reflector.cache.FastFieldCache
import com.splunk.rum.common.utils.reflector.cache.FastMethodCache
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import com.splunk.rum.common.utils.extensions.get as getDirectly
import com.splunk.rum.common.utils.extensions.invoke as invokeDirectly
import com.splunk.rum.common.utils.extensions.set as setDirectly

// MARK Obfuscation-director can't parse @PublishedApi annotation. Manual @Keep annotation is necessary.

@Suppress("UNCHECKED_CAST")
class Reflector(
    initialReadableFieldSize: Int = 8,
    initialWritableFieldSize: Int = 8,
    initialMethodSize: Int = 8
) {

    private val readableFieldCache = FastFieldCache(initialReadableFieldSize)
    private val writableFieldCache = FastFieldCache(initialWritableFieldSize)
    private val methodCache = FastMethodCache(initialMethodSize)

    private val scope = Scope()

    @OptIn(ExperimentalContracts::class)
    fun <R> reflect(block: Scope.() -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return scope.block()
    }

    inner class Scope internal constructor() {

        inline fun <reified T> Any.get(fieldName: String): T? {
            return if (USE_CACHE)
                getWithCache(fieldName)
            else
                getDirectly(fieldName)
        }

        fun Any.set(fieldName: String, value: Any?) {
            if (USE_CACHE)
                setWithCache(fieldName, value)
            else
                setDirectly(fieldName, value)
        }

        inline fun <reified T : Any> Any.invoke(methodName: String, vararg paramsPairs: Pair<Any?, Class<*>>): T? {
            return if (USE_CACHE)
                invokeWithCache(methodName, T::class.java, paramsPairs)
            else
                invokeDirectly(methodName, *paramsPairs)
        }
    }

    @Keep
    @PublishedApi
    internal fun <T> Any.getWithCache(fieldName: String): T? {
        var field = readableFieldCache.get(javaClass, fieldName)

        if (field == null) {
            field = javaClass.findField(fieldName).apply { makeReadable() }
            readableFieldCache.add(javaClass, fieldName, field)
        }

        return field.get(this) as? T
    }

    @Keep
    @PublishedApi
    internal fun Any.setWithCache(fieldName: String, value: Any?) {
        var field = writableFieldCache.get(javaClass, fieldName)

        if (field == null) {
            field = javaClass.findField(fieldName).apply { makeWritable() }
            writableFieldCache.add(javaClass, fieldName, field)
        }

        field.set(this, value)
    }

    @Keep
    @PublishedApi
    internal fun <T> Any.invokeWithCache(methodName: String, returnType: Class<T>, paramsPairs: Array<out Pair<Any?, Class<*>>>): T? {
        val paramTypes = Array(paramsPairs.size) { paramsPairs[it].second }
        var method = methodCache.get(javaClass, methodName, returnType, paramTypes)

        if (method == null) {
            method = javaClass.findMethod(methodName, returnType, *paramTypes)
            methodCache.add(javaClass, methodName, returnType, paramTypes, method)
        }

        val params = Array(paramsPairs.size) { paramsPairs[it].first }
        return method.invoke(this, *params) as? T
    }

    companion object {
        @Keep
        @PublishedApi
        internal const val USE_CACHE = true
    }
}
