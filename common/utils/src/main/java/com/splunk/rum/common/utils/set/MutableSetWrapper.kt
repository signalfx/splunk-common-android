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

package com.splunk.rum.common.utils.set

class MutableSetWrapper<E>(
    private val wrapper: Wrapper<E>
) : MutableSet<E> {

    override val size: Int
        get() = wrapper.size

    override fun contains(element: E): Boolean {
        return wrapper.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        for (element in elements)
            if (!contains(element))
                return false

        return true
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun add(element: E): Boolean {
        return wrapper.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        var added = false

        for (element in elements)
            added = add(element) || added

        return added
    }

    override fun clear() {
        wrapper.clear()
    }

    override fun iterator(): MutableIterator<E> {
        return wrapper.iterator()
    }

    override fun remove(element: E): Boolean {
        return wrapper.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        var removed = false

        for (element in elements)
            removed = remove(element) || removed

        return removed
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        var removed = false

        for (element in elements)
            if (!contains(element))
                removed = remove(element) || removed

        return removed
    }

    interface Wrapper<E> {

        val size: Int

        fun add(element: E): Boolean

        fun remove(element: E): Boolean

        fun contains(element: E): Boolean

        fun iterator(): MutableIterator<E>

        fun clear()
    }
}
