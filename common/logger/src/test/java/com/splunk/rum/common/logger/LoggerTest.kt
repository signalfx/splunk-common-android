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

package com.splunk.rum.common.logger

import org.junit.Before
import org.junit.Test

class LoggerTest {

    @Before
    fun setup() {
        Logger.clearLogs()
        Logger.capacity = 100
        Logger.consumers.clear()
        Logger.logLevel = Log.Level.VERBOSE
        Logger.clearCallers()
    }

    @Test
    fun `check logs presence`() {
        Logger.d("d", "0")
        Logger.d("d", "1", ClassNotFoundException("class not found"))

        Logger.e("e", "2")
        Logger.e("e", "3", NullPointerException("null"))

        Logger.i("i", "4")
        Logger.i("i", "5", IllegalArgumentException("illegal argument"))

        Logger.v("v", "6")
        Logger.v("v", "7", ConcurrentModificationException("concurrent modification"))

        Logger.w("w", "8")
        Logger.w("w", "9", ClassCastException("class cast"))

        assert(Logger.logs[0].let { it.tag == "d" && it.message == "0" && it.throwable == null }) { "Wrong argument at index 0" }
        assert(Logger.logs[1].let { it.tag == "d" && it.message == "1" && it.throwable is ClassNotFoundException }) { "Wrong argument at index 1" }

        assert(Logger.logs[2].let { it.tag == "e" && it.message == "2" && it.throwable == null }) { "Wrong argument at index 2" }
        assert(Logger.logs[3].let { it.tag == "e" && it.message == "3" && it.throwable is NullPointerException }) { "Wrong argument at index 3" }

        assert(Logger.logs[4].let { it.tag == "i" && it.message == "4" && it.throwable == null }) { "Wrong argument at index 4" }
        assert(Logger.logs[5].let { it.tag == "i" && it.message == "5" && it.throwable is IllegalArgumentException }) { "Wrong argument at index 5" }

        assert(Logger.logs[6].let { it.tag == "v" && it.message == "6" && it.throwable == null }) { "Wrong argument at index 6" }
        assert(Logger.logs[7].let { it.tag == "v" && it.message == "7" && it.throwable is ConcurrentModificationException }) { "Wrong argument at index 7" }

        assert(Logger.logs[8].let { it.tag == "w" && it.message == "8" && it.throwable == null }) { "Wrong argument at index 8" }
        assert(Logger.logs[9].let { it.tag == "w" && it.message == "9" && it.throwable is ClassCastException }) { "Wrong argument at index 9" }
    }

    @Test
    fun `check one time call`() {
        fun logVerboseOneTime() {
            Logger.v1("test", "verbose")
        }

        logVerboseOneTime()
        assert(Logger.logs.size == 1) { "Wrong logs size" }

        logVerboseOneTime()
        assert(Logger.logs.size == 1) { "Wrong logs size" }

        fun logDebugOneTime() {
            Logger.d1("test", "debug")
        }

        logDebugOneTime()
        assert(Logger.logs.size == 2) { "Wrong logs size" }

        logDebugOneTime()
        assert(Logger.logs.size == 2) { "Wrong logs size" }

        fun logInfoOneTime() {
            Logger.i1("test", "info")
        }

        logInfoOneTime()
        assert(Logger.logs.size == 3) { "Wrong logs size" }

        logInfoOneTime()
        assert(Logger.logs.size == 3) { "Wrong logs size" }

        fun logWarnOneTime() {
            Logger.w1("test", "warn")
        }

        logWarnOneTime()
        assert(Logger.logs.size == 4) { "Wrong logs size" }

        logWarnOneTime()
        assert(Logger.logs.size == 4) { "Wrong logs size" }

        fun logErrorOneTime() {
            Logger.e1("test", "error")
        }

        logErrorOneTime()
        assert(Logger.logs.size == 5) { "Wrong logs size" }

        logErrorOneTime()
        assert(Logger.logs.size == 5) { "Wrong logs size" }
    }

    @Test
    fun `check capacity`() {
        Logger.capacity = 3

        Logger.i("i", "0")
        Logger.i("i", "1")
        Logger.i("i", "2")

        Logger.i("i", "3")

        assert(Logger.logs.size == 3) { "Wrong logs size" }

        assert(Logger.logs[0].message == "1") { "Wrong log content on index 0" }
        assert(Logger.logs[1].message == "2") { "Wrong log content on index 1" }
        assert(Logger.logs[2].message == "3") { "Wrong log content on index 2" }
    }

    @Test
    fun `check consumer`() {
        Logger.consumers += object : Logger.Consumer {
            override fun onNewLog(log: Log) {
                assert(log.tag == "i" && log.message == "message" && log.throwable is NullPointerException) { "Wrong Log content" }
            }
        }

        Logger.i("i", "message", NullPointerException("null pointer"))
    }

    @Test
    fun `check log level`() {
        Logger.logLevel = Log.Level.INFO

        Logger.v("v", "message")
        Logger.d("d", "message")
        Logger.i("i", "message")
        Logger.w("w", "message")
        Logger.e("e", "message")

        assert(Logger.logs.size == 3) { "Wrong logs size" }
        assert(Logger.logs[0].level == Log.Level.INFO)
        assert(Logger.logs[1].level == Log.Level.WARN)
        assert(Logger.logs[2].level == Log.Level.ERROR)

        Logger.clearLogs()
        Logger.logLevel = Log.Level.WARN

        Logger.v("v", "message")
        Logger.d("d", "message")
        Logger.i("i", "message")
        Logger.w("w", "message")
        Logger.e("e", "message")

        assert(Logger.logs.size == 2)
        assert(Logger.logs[0].level == Log.Level.WARN) { "Wrong log level" }
        assert(Logger.logs[1].level == Log.Level.ERROR) { "Wrong log level" }
    }
}
