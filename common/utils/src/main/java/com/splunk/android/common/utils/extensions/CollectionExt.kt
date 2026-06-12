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

/**
 * Function is using indices instead of iterator if possible.
 */
inline fun <T> Collection<T>.forEachFast(crossinline consumer: (T) -> Unit) {
    if (this is List)
        forEachFast(consumer)
    else
        forEach(consumer)
}

inline fun <T> Collection<T>.findFast(crossinline predicate: (T) -> Boolean): T? {
    if (this is List)
        for (i in indices) {
            val item = get(i)

            if (predicate(item))
                return item
        }
    else
        for (element in this)
            if (predicate(element))
                return element

    return null
}

inline fun <T> Collection<T>.noneFast(predicate: (T) -> Boolean): Boolean {
    if (isEmpty())
        return true

    if (this is List) {
        for (i in indices)
            if (predicate(get(i)))
                return false
    } else
        for (element in this)
            if (predicate(element))
                return false

    return true
}

operator fun <T> Collection<T>.contains(elements: Collection<T>): Boolean {
    return containsAll(elements)
}

inline fun <T : Any, reified R : T> Collection<T>.findInstance(): R? {
    return find { it is R } as? R
}
