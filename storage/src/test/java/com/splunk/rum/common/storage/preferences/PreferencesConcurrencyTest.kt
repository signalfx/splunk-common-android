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
import com.splunk.rum.common.utils.thread.NamedThreadFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class PreferencesConcurrencyTest {

    @Test
    fun `existing constructor automatically loads`() {
        val preferences = Preferences(FakeCache(valueJson("loaded")))
        try {
            assertEquals("loaded", preferences.getString(KEY))
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `cache is created once on preferences worker`() {
        val callerThread = Thread.currentThread()
        val cacheCreationThread = AtomicReference<Thread>()
        val invocationCount = AtomicInteger()
        val preferences = Preferences.create {
            invocationCount.incrementAndGet()
            cacheCreationThread.set(Thread.currentThread())
            FakeCache()
        }

        try {
            preferences.size()
            assertEquals(1, invocationCount.get())
            assertTrue(cacheCreationThread.get().name.startsWith("SplunkPreferences"))
            assertTrue(cacheCreationThread.get() !== callerThread)
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `successful load publishes complete map`() {
        val cache = FakeCache(
            """{"first":{"type":"String","value":"one"},"second":{"type":"Int","value":2}}"""
        )
        val preferences = Preferences(cache)
        try {
            assertEquals("one", preferences.getString("first"))
            assertEquals(2, preferences.getInt("second"))
            assertEquals(2, preferences.size())
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `failed deserialization publishes no partial values and does not write`() {
        val cache = FakeCache(
            """{"first":{"type":"String","value":"one"},"second":{"type":"Unknown","value":2}}"""
        )
        val preferences = Preferences(cache)
        try {
            assertEquals(0, preferences.size())
            assertNull(preferences.getString("first"))
            assertEquals(0, cache.writes.size)
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `corrupted json releases getters without rewriting cache`() {
        val cache = FakeCache("""{"$KEY":{"type":"String","value":"unfinished"""")
        val preferences = Preferences(cache)
        try {
            assertEquals(0, preferences.size())
            assertNull(preferences.getString(KEY))
            assertEquals(0, cache.writes.size)
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `read failure releases waiting getter`() {
        val preferences = Preferences(object : ISimplePermanentCache {
            override fun readBytes(): ByteArray = throw IllegalStateException("read failed")
            override fun writeBytes(bytes: ByteArray) = Unit
        })
        val reader = Executors.newSingleThreadExecutor()
        try {
            assertNull(reader.submit<String?> { preferences.getString(KEY) }.get(1, TimeUnit.SECONDS))
        } finally {
            preferences.close()
            reader.shutdownNow()
        }
    }

    @Test
    fun `submission rejection releases waiting getter`() {
        val scheduler = newScheduler().apply { shutdownNow() }
        val preferences = Preferences({ FakeCache() }, scheduler)

        assertEquals(0, preferences.size())
        preferences.close()
    }

    @Test
    fun `mutation after cache creation failure does not schedule save without cache`() {
        val scheduler = newScheduler()
        val cacheCreationAttempts = AtomicInteger()
        val preferences = Preferences({
            cacheCreationAttempts.incrementAndGet()
            throw IllegalStateException("cache creation failed")
        }, scheduler)

        try {
            assertEquals(0, preferences.size())

            preferences.putString(KEY, "memory only")

            assertEquals("memory only", preferences.getString(KEY))
            assertEquals(1, cacheCreationAttempts.get())
            assertTrue(scheduler.queue.isEmpty())
        } finally {
            preferences.close()
        }
    }

    @Test
    fun `close before loading begins prevents cache creation`() {
        val scheduler = newScheduler()
        val workerOccupied = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        scheduler.submit {
            workerOccupied.countDown()
            releaseWorker.await()
        }
        assertTrue(workerOccupied.await(1, TimeUnit.SECONDS))

        val invocations = AtomicInteger()
        val preferences = Preferences({
            invocations.incrementAndGet()
            FakeCache()
        }, scheduler)

        preferences.close()
        releaseWorker.countDown()

        assertTrue(scheduler.awaitTermination(1, TimeUnit.SECONDS))
        assertEquals(0, invocations.get())
    }

    @Test
    fun `close during blocked read returns and completed read does not publish`() {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val cache = object : ISimplePermanentCache {
            override fun readBytes(): ByteArray {
                readStarted.countDown()
                while (true) {
                    try {
                        releaseRead.await()
                        return valueJson("too late").toByteArray()
                    } catch (_: InterruptedException) {
                        // Model a filesystem operation that cannot be cancelled.
                    }
                }
            }

            override fun writeBytes(bytes: ByteArray) = error("closed loader must not write")
        }
        val preferences = Preferences(cache)
        assertTrue(readStarted.await(1, TimeUnit.SECONDS))

        preferences.close()
        assertNull(preferences.getString(KEY))
        assertEquals(0, preferences.size())
        releaseRead.countDown()

        assertNull(preferences.getString(KEY))
        preferences.close()
    }

    @Test
    fun `closed instance is inert`() {
        val cache = FakeCache()
        val preferences = Preferences(cache)
        preferences.size()
        preferences.close()

        preferences.putString(KEY, "ignored").remove(KEY).clear().commit()

        assertNull(preferences.getString(KEY))
        assertFalse(KEY in preferences)
        assertEquals(0, preferences.size())
        assertEquals(0, cache.writes.size)
    }

    @Test
    fun `close cancels a pending debounced write`() {
        val scheduler = newScheduler()
        val cache = FakeCache()
        val preferences = Preferences({ cache }, scheduler)
        preferences.size()
        preferences.putString(KEY, "pending")

        preferences.close()

        assertTrue(scheduler.awaitTermination(1, TimeUnit.SECONDS))
        assertEquals(0, cache.writes.size)
    }

    @Test
    fun `closing one instance does not affect another cache owner`() {
        val cache = FakeCache()
        val losingPreferences = Preferences(cache)
        val winningPreferences = Preferences(cache)
        losingPreferences.size()
        winningPreferences.size()

        losingPreferences.close()
        winningPreferences.putString(KEY, "winner").commit()

        assertEquals("winner", winningPreferences.getString(KEY))
        assertEquals("winner", Preferences(cache).use { it.getString(KEY) })
        winningPreferences.close()
    }

    @Test
    fun `mutation during active save is persisted by a following save`() {
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val secondWriteCompleted = CountDownLatch(1)
        val writeCount = AtomicInteger()
        val cache = object : ISimplePermanentCache {
            @Volatile
            var bytes = ByteArray(0)

            override fun readBytes(): ByteArray = bytes

            override fun writeBytes(bytes: ByteArray) {
                if (writeCount.incrementAndGet() == 1) {
                    firstWriteStarted.countDown()
                    releaseFirstWrite.await()
                }
                this.bytes = bytes
                if (writeCount.get() >= 2) secondWriteCompleted.countDown()
            }
        }
        val preferences = Preferences(cache)
        preferences.size()
        preferences.putString(KEY, "old")
        assertTrue(firstWriteStarted.await(2, TimeUnit.SECONDS))

        preferences.putString(KEY, "new")
        releaseFirstWrite.countDown()

        assertTrue(secondWriteCompleted.await(2, TimeUnit.SECONDS))
        assertTrue(cache.bytes.toString(Charsets.UTF_8).contains("new"))
        preferences.close()
    }

    @Test
    fun `commit waits for loading and completes without deadlock`() {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val cache = object : ISimplePermanentCache {
            override fun readBytes(): ByteArray {
                readStarted.countDown()
                releaseRead.await()
                return ByteArray(0)
            }

            override fun writeBytes(bytes: ByteArray) = Unit
        }
        val preferences = Preferences(cache)
        val committer = Executors.newSingleThreadExecutor()
        assertTrue(readStarted.await(1, TimeUnit.SECONDS))
        val commit = committer.submit { preferences.commit() }

        releaseRead.countDown()
        commit.get(1, TimeUnit.SECONDS)

        preferences.close()
        committer.shutdownNow()
    }

    private class FakeCache(initialJson: String = "") : ISimplePermanentCache {
        @Volatile
        private var bytes = initialJson.toByteArray()
        val writes = Collections.synchronizedList(mutableListOf<ByteArray>())

        override fun readBytes(): ByteArray = bytes

        override fun writeBytes(bytes: ByteArray) {
            this.bytes = bytes
            writes += bytes
        }
    }

    private companion object {
        const val KEY = "key"

        fun valueJson(value: String) = """{"$KEY":{"type":"String","value":"$value"}}"""

        fun newScheduler() = ScheduledThreadPoolExecutor(1, NamedThreadFactory("Preferences-test"))
    }
}
