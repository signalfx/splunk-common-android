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
import java.lang.reflect.Method

@Throws(NoSuchFieldException::class)
fun Class<*>.findField(name: String): Field {
    var superclass: Class<*> = this

    do {
        try {
            val field = superclass.getDeclaredField(name)
            field.isAccessible = true

            return field
        } catch (_: NoSuchFieldException) {
            superclass = superclass.superclass ?: break
        }
    } while (true)

    throw NoSuchFieldException("Property '${this.name}.$name' not found")
}

fun Class<*>.hasField(name: String): Boolean {
    return try {
        findField(name)
        true
    } catch (_: NoSuchFieldException) {
        false
    }
}

fun Class<*>.findMethod(methodName: String, returnType: Class<*>, vararg types: Class<*>): Method {
    var currentClass: Class<*> = this

    do {
        try {
            val method = currentClass.getDeclaredMethod(methodName, *types)

            if (!isReturnTypeCompatible(returnType, method.returnType))
                throw NoSuchMethodException("Method '${method.returnType.name} ${currentClass.name}.$methodName(${types.joinToString(", ") { it.name }})' found with different return type. Expected '${returnType.name}'.")

            method.isAccessible = true
            return method
        } catch (_: NoSuchMethodException) {
            currentClass = currentClass.superclass ?: break
        }
    } while (true)

    throw NoSuchMethodException("Unable to find method '${returnType.name} ${this.name}.$methodName(${types.joinToString(", ") { it.name }})'")
}

private fun isReturnTypeCompatible(expected: Class<*>, actual: Class<*>): Boolean {
    if (expected.isAssignableFrom(actual))
        return true

    if (expected == Void::class.java || expected == Void.TYPE || expected == Unit::class.java)
        return actual == Void::class.java || actual == Void.TYPE || actual == Unit::class.java

    return expected.primitiveEquivalent == actual.primitiveEquivalent
}

@Suppress("RemoveRedundantQualifierName", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private val Class<*>.primitiveEquivalent: Class<*>
    get() = if (isPrimitive)
        this
    else
        when (this) {
            java.lang.Integer::class.java -> Int::class.javaPrimitiveType!!
            java.lang.Boolean::class.java -> Boolean::class.javaPrimitiveType!!
            java.lang.Long::class.java -> Long::class.javaPrimitiveType!!
            java.lang.Float::class.java -> Float::class.javaPrimitiveType!!
            java.lang.Double::class.java -> Double::class.javaPrimitiveType!!
            java.lang.Byte::class.java -> Byte::class.javaPrimitiveType!!
            java.lang.Short::class.java -> Short::class.javaPrimitiveType!!
            java.lang.Character::class.java -> Char::class.javaPrimitiveType!!
            else -> this
        }
