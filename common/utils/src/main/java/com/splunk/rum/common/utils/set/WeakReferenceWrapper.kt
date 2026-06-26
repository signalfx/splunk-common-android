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

import java.lang.ref.WeakReference

class WeakReferenceWrapper<E>(
    private val list: MutableList<WeakReference<E>>
) : MutableSetWrapper.Wrapper<E> {

    override val size: Int
        get() = list.size

    override fun add(element: E): Boolean {
        return if (!contains(element))
            list.add(WeakReference(element))
        else
            false
    }

    override fun remove(element: E): Boolean {
        val iterator = iterator()

        for (e in iterator)
            if (e == element) {
                iterator.remove()
                return true
            }

        return false
    }

    override fun contains(element: E): Boolean {
        for (e in iterator())
            if (e == element)
                return true

        return false
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            var index = 0

            override fun hasNext(): Boolean {
                if (index < list.lastIndex) {
                    if (list[index].get() == null) {
                        list.removeAt(index)
                        index++

                        return hasNext()
                    }

                    return true
                }

                return false
            }

            override fun next(): E {
                return list[index++].get() ?: throw IllegalStateException(":(")
            }

            override fun remove() {
                list.removeAt(index)
            }
        }
    }

    override fun clear() {
        list.clear()
    }
}
