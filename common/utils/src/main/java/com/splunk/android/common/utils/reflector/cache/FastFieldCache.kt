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

package com.splunk.android.common.utils.reflector.cache

import java.lang.reflect.Field
import kotlin.math.max

internal class FastFieldCache(initialCapacity: Int = 8) {

    private var classes = arrayOfNulls<Class<*>>(initialCapacity)
    private var names = arrayOfNulls<String>(initialCapacity)
    private var fields = arrayOfNulls<Field>(initialCapacity)

    private var size = 0

    @Suppress("EmptyRange")
    fun get(clazz: Class<*>, name: String): Field? {
        for (i in 0 until size)
            if (classes[i] === clazz && names[i] == name)
                return fields[i]

        return null
    }

    fun add(clazz: Class<*>, name: String, field: Field) {
        if (size >= classes.size) {
            val newCapacity = max(size * 2, 1)
            classes = classes.copyOf(newCapacity)
            names = names.copyOf(newCapacity)
            fields = fields.copyOf(newCapacity)
        }

        classes[size] = clazz
        names[size] = name
        fields[size] = field

        size++
    }
}
