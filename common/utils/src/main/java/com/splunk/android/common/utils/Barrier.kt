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

class Barrier(
    @Volatile private var lockCount: Int = 0
) {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = Object()

    fun increase() {
        synchronized(lock) {
            lockCount++
        }
    }

    operator fun plusAssign(value: Int) {
        synchronized(lock) {
            lockCount += value
        }
    }

    fun decrease() {
        synchronized(lock) {
            lockCount--

            if (lockCount < 0)
                lockCount = 0

            checkIfCompleted()
        }
    }

    fun set(count: Int) {
        synchronized(lock) {
            lockCount = count.coerceAtLeast(0)
            checkIfCompleted()
        }
    }

    fun waitToComplete() {
        synchronized(lock) {
            if (!checkIfCompleted())
                runCatching { lock.wait() }
        }
    }

    fun getLockCount(): Int {
        synchronized(lock) {
            return lockCount
        }
    }

    private fun checkIfCompleted(): Boolean {
        if (lockCount == 0) {
            lock.notifyAll()
            return true
        }

        return false
    }

    override fun toString(): String {
        return "Barrier(lockCount: $lockCount)"
    }
}
