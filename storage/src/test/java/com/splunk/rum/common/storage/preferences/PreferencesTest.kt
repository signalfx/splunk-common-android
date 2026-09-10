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

package com.splunk.rum.common.storage.preferences

import com.splunk.rum.common.storage.cache.ISimplePermanentCache
import com.splunk.rum.common.utils.extensions.toJSONObject
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.text.contains

internal class PreferencesTest {

    private var testCache = TestSimplePermanentCache()
    private var preferences = Preferences(testCache)

    @Test
    fun `check values existence in a map`() {
        println("Test started: check value existence")

        whenWrittenToPreferences()
        thenValuesExistsInPreferences(true)
        whenRemovedFromPreferences()
        thenValuesExistsInPreferences(false)
    }

    @Test
    fun `check values existence in a file`() {
        println("Test started: check value existence in a file")

        whenWrittenToPreferences()
        preferences.commit()
        thenExistsInFile(true)
        whenRemovedFromPreferences()
        preferences.commit()
        thenExistsInFile(false)
    }

    @Test
    fun `check number of values`() {
        println("Test started: check number of values")

        val key = "Int key"

        whenIntWrittenToPreferences(key)
        thenNumberOfValuesIsCorrect(1)
        whenIntDeletedFromPreferences("differentKey")
        thenNumberOfValuesIsCorrect(1)
        whenIntDeletedFromPreferences(key)
        thenNumberOfValuesIsCorrect(0)
    }

    @Test
    fun `contains a value`() {
        println("Test started: contains a value")

        whenWrittenToPreferences()
        thenContainsValue()
    }

    @Test
    fun `clear all the values in preferences`() {
        println("Test started: clear all the values in preferences")

        whenWrittenToPreferences()
        whenClearPreferences()
        preferences.commit()
        thenIsEmpty()
    }

    @Test
    fun `commit persists values before returning`() {
        preferences.putString("key", "value")

        preferences.commit()

        Assert.assertEquals(
            "value",
            testCache.readBytes().toString(Charsets.UTF_8).toJSONObject().getJSONObject("key").getString("value"),
        )
    }

    @Test
    fun `commit persists the loaded values and pending changes`() {
        testCache.writeBytes(
            "{\"existing\":{\"type\":\"String\",\"value\":\"old\"}}".toByteArray(),
        )
        preferences = Preferences(testCache)

        preferences.putString("new", "value")
        preferences.commit()

        val persisted = testCache.readBytes().toString(Charsets.UTF_8).toJSONObject()
        Assert.assertEquals("old", persisted.getJSONObject("existing").getString("value"))
        Assert.assertEquals("value", persisted.getJSONObject("new").getString("value"))
    }

    @Test
    fun `apply schedules another write when the map changes during a write`() {
        val cache = BlockingWriteCache()
        val preferences = Preferences(cache)

        preferences.putString("first", "value")
        Assert.assertTrue(cache.firstWriteStarted.await(5, TimeUnit.SECONDS))

        preferences.putString("second", "value")
        cache.allowFirstWrite.countDown()

        Assert.assertTrue(cache.secondWriteCompleted.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(cache.readBytes().toString(Charsets.UTF_8).contains("second"))
    }

    @Test
    fun `factory creates cache on the Preferences worker`() {
        val callerThread = Thread.currentThread()
        val factoryThread = AtomicReference<Thread>()
        val cacheCreated = CountDownLatch(1)

        val factoryPreferences = Preferences.create {
            factoryThread.set(Thread.currentThread())
            cacheCreated.countDown()
            TestSimplePermanentCache()
        }

        Assert.assertTrue(cacheCreated.await(5, TimeUnit.SECONDS))
        Assert.assertNotSame(callerThread, factoryThread.get())

        factoryPreferences.putString("key", "value")
        Assert.assertEquals("value", factoryPreferences.getString("key"))
    }

    @Test
    fun `all supported types survive serialization and reload`() {
        val cache = TestSimplePermanentCache()
        val firstPreferences = Preferences(cache)
        val expectedMap = mapOf("one" to "1", "two" to "2")

        firstPreferences.putString("string", "value")
        firstPreferences.putInt("int", 1)
        firstPreferences.putLong("long", 2L)
        firstPreferences.putFloat("float", 3.5f)
        firstPreferences.putBoolean("boolean", true)
        firstPreferences.putStringMap("map", expectedMap)
        firstPreferences.commit()

        val reloadedPreferences = Preferences(cache)

        Assert.assertEquals("value", reloadedPreferences.getString("string"))
        Assert.assertEquals(1, reloadedPreferences.getInt("int"))
        Assert.assertEquals(2L, reloadedPreferences.getLong("long"))
        Assert.assertEquals(3.5f, reloadedPreferences.getFloat("float"))
        Assert.assertEquals(true, reloadedPreferences.getBoolean("boolean"))
        Assert.assertEquals(expectedMap, reloadedPreferences.getStringMap("map"))
        Assert.assertEquals(6, reloadedPreferences.size())
    }

    @Test
    fun `reads wait for asynchronous load and do not read the file repeatedly`() {
        val cache = BlockingReadCache(
            "{\"key\":{\"type\":\"String\",\"value\":\"value\"}}".toByteArray(),
        )
        val preferences = Preferences(cache)
        val readResult = AtomicReference<String>()
        val readFinished = CountDownLatch(1)

        Thread {
            readResult.set(preferences.getString("key"))
            readFinished.countDown()
        }.start()

        Assert.assertTrue(cache.readStarted.await(5, TimeUnit.SECONDS))
        Assert.assertFalse(readFinished.await(100, TimeUnit.MILLISECONDS))

        cache.allowRead.countDown()

        Assert.assertTrue(readFinished.await(5, TimeUnit.SECONDS))
        Assert.assertEquals("value", readResult.get())
        Assert.assertEquals(1, cache.readCount.get())
        Assert.assertEquals("value", preferences.getString("key"))
        Assert.assertEquals(1, cache.readCount.get())
    }

    @Test
    fun `commit waits for load before writing`() {
        val cache = BlockingReadCache(
            "{\"existing\":{\"type\":\"String\",\"value\":\"value\"}}".toByteArray(),
        )
        val preferences = Preferences(cache)
        val commitFinished = CountDownLatch(1)

        Thread {
            preferences.commit()
            commitFinished.countDown()
        }.start()

        Assert.assertTrue(cache.readStarted.await(5, TimeUnit.SECONDS))
        Assert.assertFalse(commitFinished.await(100, TimeUnit.MILLISECONDS))

        cache.allowRead.countDown()

        Assert.assertTrue(commitFinished.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(cache.readBytes().toString(Charsets.UTF_8).contains("existing"))
    }

    @Test
    fun `mutations wait for load and preserve loaded values`() {
        val cache = BlockingReadCache(
            "{\"existing\":{\"type\":\"String\",\"value\":\"value\"}}".toByteArray(),
        )
        val preferences = Preferences(cache)
        val mutationFinished = CountDownLatch(1)

        Thread {
            preferences.putString("new", "value")
            mutationFinished.countDown()
        }.start()

        Assert.assertTrue(cache.readStarted.await(5, TimeUnit.SECONDS))
        Assert.assertFalse(mutationFinished.await(100, TimeUnit.MILLISECONDS))

        cache.allowRead.countDown()

        Assert.assertTrue(mutationFinished.await(5, TimeUnit.SECONDS))
        preferences.commit()

        val persisted = cache.readBytes().toString(Charsets.UTF_8).toJSONObject()
        Assert.assertEquals("value", persisted.getJSONObject("existing").getString("value"))
        Assert.assertEquals("value", persisted.getJSONObject("new").getString("value"))
    }

    @Test
    fun `load failure releases the load barrier`() {
        val preferences = Preferences(FailingReadCache())

        Assert.assertNull(preferences.getString("key"))
    }

    @Test
    fun `missing preferences file starts empty`() {
        val preferences = Preferences(MissingReadCache())

        Assert.assertNull(preferences.getString("key"))
        Assert.assertEquals(0, preferences.size())
    }

    @Test
    fun `corrupt preferences are cleared without deadlocking`() {
        val cache = TestSimplePermanentCache()
        cache.writeBytes("not-json".toByteArray())
        val preferences = Preferences(cache)

        Assert.assertNull(preferences.getString("key"))
        Assert.assertEquals("{}", cache.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `partially corrupt preferences do not expose partially loaded values`() {
        val cache = TestSimplePermanentCache()
        cache.writeBytes(
            (
                "{\"valid\":{\"type\":\"String\",\"value\":\"value\"}," +
                    "\"invalid\":{\"type\":\"Unsupported\",\"value\":true}}"
                ).toByteArray(),
        )
        val preferences = Preferences(cache)

        Assert.assertNull(preferences.getString("valid"))
        Assert.assertNull(preferences.getString("invalid"))
        Assert.assertEquals("{}", cache.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `factory failure releases the load barrier`() {
        val preferences = Preferences.create {
            throw IllegalStateException("cache creation failed")
        }

        Assert.assertNull(preferences.getString("key"))
        preferences.putString("key", "value")
        Assert.assertEquals("value", preferences.getString("key"))
    }

    @Test
    fun `apply coalesces mutations and schedules a new task after completion`() {
        val cache = CountingWriteCache()
        val preferences = Preferences(cache)

        preferences.putString("one", "1")
        preferences.putString("two", "2")
        preferences.putString("three", "3")

        Assert.assertTrue(cache.firstWriteCompleted.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(1, cache.writeCount.get())

        preferences.putString("four", "4")

        Assert.assertTrue(cache.secondWriteCompleted.await(5, TimeUnit.SECONDS))
        Assert.assertEquals(2, cache.writeCount.get())
        Assert.assertTrue(cache.readBytes().toString(Charsets.UTF_8).contains("four"))
    }

    @Test
    fun `apply can recover after a write failure`() {
        val cache = FailFirstWriteCache()
        val preferences = Preferences(cache)

        preferences.putString("first", "value")
        Assert.assertTrue(cache.firstWriteAttempted.await(5, TimeUnit.SECONDS))

        preferences.putString("second", "value")

        Assert.assertTrue(cache.secondWriteCompleted.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(cache.readBytes().toString(Charsets.UTF_8).contains("second"))
    }

    @Test
    fun `concurrent mutations are retained in the in-memory map`() {
        val cache = TestSimplePermanentCache()
        val preferences = Preferences(cache)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(8)
        val failure = AtomicReference<Throwable>()

        repeat(8) { index ->
            Thread {
                try {
                    start.await()
                    preferences.putInt("key-$index", index)
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                } finally {
                    finished.countDown()
                }
            }.start()
        }

        start.countDown()
        Assert.assertTrue(finished.await(5, TimeUnit.SECONDS))
        Assert.assertNull(failure.get())
        Assert.assertEquals(8, preferences.size())
        repeat(8) { index ->
            Assert.assertEquals(index, preferences.getInt("key-$index"))
        }

        preferences.commit()
        val persisted = cache.readBytes().toString(Charsets.UTF_8).toJSONObject()
        repeat(8) { index ->
            Assert.assertEquals(index, persisted.getJSONObject("key-$index").getInt("value"))
        }
    }

    @Test
    fun `commit racing with an active apply preserves the latest mutation`() {
        val cache = BlockingWriteCache()
        val preferences = Preferences(cache)
        val commitFinished = CountDownLatch(1)

        preferences.putString("first", "value")
        Assert.assertTrue(cache.firstWriteStarted.await(5, TimeUnit.SECONDS))

        Thread {
            preferences.commit()
            commitFinished.countDown()
        }.start()
        preferences.putString("second", "value")

        cache.allowFirstWrite.countDown()

        Assert.assertTrue(commitFinished.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(cache.secondWriteCompleted.await(5, TimeUnit.SECONDS))
        Assert.assertTrue(cache.readBytes().toString(Charsets.UTF_8).contains("second"))
    }

    @Test
    fun `reading a value with a different type returns null`() {
        val preferences = Preferences(TestSimplePermanentCache())

        preferences.putString("key", "value")

        Assert.assertNull(preferences.getInt("key"))
    }

    private fun whenWrittenToPreferences() {
        preferences.putString("key for String", "String value")
        preferences.putString("key for String to be deleted", "String value to be deleted")
        preferences.putInt("key for Int", 10)
        preferences.putInt("key for Int to be deleted", 10)
        preferences.putLong("key for Long", 20L)
        preferences.putLong("key for Long to be deleted", 20L)
        preferences.putFloat("key for Float", 30.5f)
        preferences.putFloat("key for Float to be deleted", 30.5f)
        preferences.putBoolean("key for Boolean", true)
        preferences.putBoolean("key for Boolean to be deleted", true)
    }

    private fun whenRemovedFromPreferences() {
        preferences.remove("key for String to be deleted")
        preferences.remove("key for Int to be deleted")
        preferences.remove("key for Long to be deleted")
        preferences.remove("key for Float to be deleted")
        preferences.remove("key for Boolean to be deleted")
    }

    private fun whenIntWrittenToPreferences(key: String) {
        preferences.putInt(key, 10)
    }

    private fun whenIntDeletedFromPreferences(key: String) {
        preferences.remove(key)
    }

    private fun whenClearPreferences() {
        preferences.clear()
    }

    private fun thenValuesExistsInPreferences(exists: Boolean) {
        val stringValue = preferences.getString("key for String to be deleted")
        val intValue = preferences.getInt("key for Int to be deleted")
        val longValue = preferences.getLong("key for Long to be deleted")
        val floatValue = preferences.getFloat("key for Float to be deleted")
        val booleanValue = preferences.getBoolean("key for Boolean to be deleted")

        Assert.assertTrue((stringValue != null) == exists)
        Assert.assertTrue((intValue != null) == exists)
        Assert.assertTrue((longValue != null) == exists)
        Assert.assertTrue((floatValue != null) == exists)
        Assert.assertTrue((booleanValue != null) == exists)
    }

    private fun thenNumberOfValuesIsCorrect(size: Int) {
        Assert.assertTrue(preferences.size() == size)
    }

    private fun thenExistsInFile(exists: Boolean) {
        val persisted = testCache.readBytes().toString(Charsets.UTF_8).toJSONObject()
        Assert.assertEquals(exists, persisted.has("key for String to be deleted"))
    }

    private fun thenContainsValue() {
        val contains = preferences.contains("key for String to be deleted")
        Assert.assertTrue(contains)
    }

    private fun thenIsEmpty() {
        Assert.assertEquals("{}", testCache.readBytes().toString(Charsets.UTF_8))
    }

    private class TestSimplePermanentCache : ISimplePermanentCache {
        @Volatile
        private var bytesMockFile = ByteArray(0)

        override fun readBytes(): ByteArray {
            return bytesMockFile
        }

        override fun writeBytes(bytes: ByteArray) {
            bytesMockFile = bytes
        }
    }

    private class FailingReadCache : ISimplePermanentCache {
        override fun readBytes(): ByteArray? = error("read failed")

        override fun writeBytes(bytes: ByteArray) = Unit
    }

    private class MissingReadCache : ISimplePermanentCache {
        override fun readBytes(): ByteArray? = null

        override fun writeBytes(bytes: ByteArray) = Unit
    }

    private class BlockingWriteCache : ISimplePermanentCache {
        private val firstWrite = AtomicBoolean(true)

        @Volatile
        private var bytes = ByteArray(0)

        val firstWriteStarted = CountDownLatch(1)
        val allowFirstWrite = CountDownLatch(1)
        val secondWriteCompleted = CountDownLatch(1)

        override fun readBytes(): ByteArray = bytes

        override fun writeBytes(bytes: ByteArray) {
            val isFirstWrite = firstWrite.compareAndSet(true, false)
            if (isFirstWrite) {
                firstWriteStarted.countDown()
                allowFirstWrite.await(5, TimeUnit.SECONDS)
            }

            this.bytes = bytes
            if (!isFirstWrite) {
                secondWriteCompleted.countDown()
            }
        }
    }

    private class BlockingReadCache(
        initialBytes: ByteArray,
    ) : ISimplePermanentCache {
        @Volatile
        private var persistedBytes = initialBytes

        val readStarted = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val readCount = AtomicInteger()

        override fun readBytes(): ByteArray {
            readCount.incrementAndGet()
            readStarted.countDown()
            allowRead.await(5, TimeUnit.SECONDS)
            return persistedBytes
        }

        override fun writeBytes(bytes: ByteArray) {
            persistedBytes = bytes
        }
    }

    private class CountingWriteCache : ISimplePermanentCache {
        @Volatile
        private var bytes = ByteArray(0)

        val firstWriteCompleted = CountDownLatch(1)
        val secondWriteCompleted = CountDownLatch(1)
        val writeCount = AtomicInteger()

        override fun readBytes(): ByteArray = bytes

        override fun writeBytes(bytes: ByteArray) {
            this.bytes = bytes
            when (writeCount.incrementAndGet()) {
                1 -> firstWriteCompleted.countDown()
                2 -> secondWriteCompleted.countDown()
            }
        }
    }

    private class FailFirstWriteCache : ISimplePermanentCache {
        private val firstWrite = AtomicBoolean(true)

        @Volatile
        private var bytes = ByteArray(0)

        val firstWriteAttempted = CountDownLatch(1)
        val secondWriteCompleted = CountDownLatch(1)

        override fun readBytes(): ByteArray = bytes

        override fun writeBytes(bytes: ByteArray) {
            if (firstWrite.compareAndSet(true, false)) {
                firstWriteAttempted.countDown()
                throw IllegalStateException("write failed")
            }

            this.bytes = bytes
            secondWriteCompleted.countDown()
        }
    }
}
