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

import org.junit.Assert
import org.junit.Test
import java.util.Date

class DateExtTest {

    // key ..... unix timestamp
    // value ... ISO8601 timestamp
    private val unixToISO8601Conversions = mapOf(
        // Without millis
        Pair(1647255583000L, "2022-03-14T10:59:43.000Z"),
        Pair(1647255540000L, "2022-03-14T10:59:00.000Z"),
        Pair(1641034740000L, "2022-01-01T10:59:00.000Z"),
        Pair(1640948399000L, "2021-12-31T10:59:59.000Z"),
        Pair(946638659000L, "1999-12-31T11:10:59.000Z"),

        // With millis
        Pair(1647255583128L, "2022-03-14T10:59:43.128Z"),
        Pair(1647255540456L, "2022-03-14T10:59:00.456Z"),
        Pair(1641034740298L, "2022-01-01T10:59:00.298Z"),
        Pair(1640948399199L, "2021-12-31T10:59:59.199Z"),
        Pair(946638659478L, "1999-12-31T11:10:59.478Z"),
        Pair(1647255583999L, "2022-03-14T10:59:43.999Z"),
        Pair(1647255540999L, "2022-03-14T10:59:00.999Z"),
        Pair(1641034740999L, "2022-01-01T10:59:00.999Z"),
        Pair(1640948399999L, "2021-12-31T10:59:59.999Z"),
        Pair(946638659999L, "1999-12-31T11:10:59.999Z"),
    )

    // key ..... date timestamp
    // value ... ISO8601 timestamp
    private val dateToISO8601Conversions = unixToISO8601Conversions.mapKeys { Date(it.key) }

    @Test
    fun `convert unix timestamp longs to ISO8601 timestamp Strings`() {
        unixToISO8601Conversions.forEach { (unix, iso8601) ->
            Assert.assertEquals(unix.toISO8601String(), iso8601)
        }
    }

    @Test
    fun `stress convert unix timestamp longs to ISO8601 timestamp Strings`() {
        repeat(100_000) {
            unixToISO8601Conversions.forEach { (unix, iso8601) ->
                Assert.assertEquals(unix.toISO8601String(), iso8601)
            }
        }
    }

    @Test
    fun `multi threaded stress convert unix timestamp longs to ISO8601 timestamp Strings`() {
        repeat(10) {
            Thread {
                repeat(10_000) {
                    unixToISO8601Conversions.forEach { (unix, iso8601) ->
                        Assert.assertEquals(unix.toISO8601String(), iso8601)
                    }
                }
            }.start()
        }
    }

    @Test
    fun `convert date timestamp to ISO8601 timestamp Strings`() {
        dateToISO8601Conversions.forEach { (date, iso8601) ->
            Assert.assertEquals(date.toISO8601String(), iso8601)
        }
    }

    @Test
    fun `stress convert date timestamp to ISO8601 timestamp Strings`() {
        repeat(100_000) {
            dateToISO8601Conversions.forEach { (date, iso8601) ->
                Assert.assertEquals(date.toISO8601String(), iso8601)
            }
        }
    }

    @Test
    fun `multi threaded stress convert date timestamp to ISO8601 timestamp Strings`() {
        repeat(10) {
            Thread {
                repeat(10_000) {
                    dateToISO8601Conversions.forEach { (date, iso8601) ->
                        Assert.assertEquals(date.toISO8601String(), iso8601)
                    }
                }
            }.start()
        }
    }
}
