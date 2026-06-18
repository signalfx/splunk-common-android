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

package com.splunk.android.common.utils

import com.splunk.android.common.utils.extensions.forEachFast

open class MutableListObserver<E>(
    private val list: MutableList<E>,
    val observer: Observer<E>
) : MutableList<E> {

    override val size: Int
        get() = list.size

    override fun contains(element: E): Boolean {
        return list.contains(element)
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return list.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private var index = 0

            override fun hasNext(): Boolean {
                return index != size
            }

            override fun next(): E {
                return get(index++)
            }

            override fun remove() {
                index--
                removeAt(index)
            }
        }
    }

    override fun add(element: E): Boolean {
        val added = list.add(element)
        observer.onAdded(element)
        return added
    }

    override fun add(index: Int, element: E) {
        list.add(index, element)
        observer.onAdded(element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        elements.reversed().forEachFast { add(index, it) }
        return elements.isNotEmpty()
    }

    override fun addAll(elements: Collection<E>): Boolean {
        elements.forEachFast { add(it) }
        return elements.isNotEmpty()
    }

    override fun clear() {
        val copy = ArrayList(this)
        list.clear()
        copy.forEachFast { observer.onRemoved(it) }
    }

    override fun remove(element: E): Boolean {
        return if (list.remove(element)) {
            observer.onRemoved(element)
            true
        } else
            false
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        var removed = false

        elements.forEachFast {
            if (remove(it)) {
                observer.onRemoved(it)
                removed = true
            }
        }

        return removed
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        var removed = false
        val iterator = iterator()

        while (iterator.hasNext()) {
            val element = iterator.next()

            if (element !in elements) {
                iterator.remove()
                observer.onRemoved(element)
                removed = true
            }
        }

        return removed
    }

    override fun get(index: Int): E {
        return list[index]
    }

    override fun indexOf(element: E): Int {
        return list.indexOf(element)
    }

    override fun lastIndexOf(element: E): Int {
        return list.lastIndexOf(element)
    }

    override fun listIterator(): MutableListIterator<E> {
        return list.listIterator() // FIXME
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return list.listIterator(index) // FIXME
    }

    override fun removeAt(index: Int): E {
        val removed = list.removeAt(index)
        observer.onRemoved(removed)
        return removed
    }

    override fun set(index: Int, element: E): E {
        val removed = list.set(index, element)
        observer.onRemoved(removed)
        observer.onAdded(element)
        return removed
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        return list.subList(fromIndex, toIndex) // FIXME
    }

    override fun toString(): String {
        return list.toString()
    }

    interface Observer<E> {
        fun onAdded(element: E) {}
        fun onRemoved(element: E) {}
    }
}
