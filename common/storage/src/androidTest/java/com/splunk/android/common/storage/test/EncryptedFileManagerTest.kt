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

package com.splunk.android.common.storage.test

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.splunk.android.common.storage.filemanager.EncryptedFileManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.SecretKey
import com.splunk.android.common.storage.encryption.KeyManager
import com.splunk.android.common.storage.filemanager.EncryptedFileManager.Companion.IV_SIZE_BYTES
import com.splunk.android.common.storage.filemanager.EncryptedFileManager.Companion.MIN_GCM_TAG_BYTES
import org.junit.Assert.fail
import java.io.IOException
import javax.crypto.AEADBadTagException

@SdkSuppress(minSdkVersion = 23)
@RunWith(AndroidJUnit4::class)
internal class EncryptedFileManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val keyManager: KeyManager = KeyManager.instance

    private fun genKey(alias: String): SecretKey {
        return keyManager.getKey(alias)!!
    }

    private fun deleteAlias(alias: String) {
        keyManager.deleteKey(keyAlias = alias)
    }

    private fun newFile(name: String): File {
        val dir = File(context.filesDir, "enc_test").apply { mkdirs() }
        return File(dir, name).apply { if (exists()) delete() }
    }

    @Test
    fun roundTrip_smallPayload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_rt"
        deleteAlias(alias)
        val key = genKey(alias)

        val mgr = EncryptedFileManager(key)
        val file = newFile("a.bin")

        val plain = "hello-ča-世界".toByteArray(Charsets.UTF_8)
        mgr.writeBytes(file, plain)

        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            assertTrue(len >= IV_SIZE_BYTES + MIN_GCM_TAG_BYTES) // Using constants defined in EncryptedFileManager
            val iv = ByteArray(IV_SIZE_BYTES)
            raf.readFully(iv)
            assertEquals(IV_SIZE_BYTES, iv.size)
        }

        val out = mgr.readBytes(file)

        assertArrayEquals(plain, out)
    }

    @Test
    fun overwrite_existingFile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_over"
        deleteAlias(alias)
        val key = genKey(alias)
        val mgr = EncryptedFileManager(key)
        val file = newFile("b.bin")

        mgr.writeBytes(file, ByteArray(32) { 1 })
        mgr.writeBytes(file, ByteArray(64) { 2 })

        val out = mgr.readBytes(file)

        assertEquals(64, out.size)
        assertTrue(out.all { it == 2.toByte() })
    }

    @Test
    fun read_emptyOrTooShort_throwsIOException() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_empty"
        deleteAlias(alias)
        val key = genKey(alias)
        val mgr = EncryptedFileManager(key)

        // Test with an empty file (exists, but length is 0)
        val empty = newFile("c_empty.bin").apply { createNewFile() }
        try {
            mgr.readBytes(empty)
            fail("Expected IOException for empty file, but no exception was thrown.")
        } catch (e: IOException) {
            // Expected: "File content too short or malformed"
            assertTrue(e.message?.contains("File content too short") == true)
        } catch (e: Exception) {
            fail("Expected IOException, but got ${e.javaClass.simpleName}")
        }

        // Test with a file too short to contain IV + GCM tag
        val short = newFile("c_short.bin").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03)) // less than 12B IV + 16B tag
        }
        try {
            mgr.readBytes(short)
            fail("Expected IOException for too short file, but no exception was thrown.")
        } catch (e: IOException) {
            // Expected: "File content too short or malformed"
            assertTrue(e.message?.contains("File content too short") == true)
        } catch (e: Exception) {
            fail("Expected IOException, but got ${e.javaClass.simpleName}")
        }
    }

    @Test
    fun corruptedFile_throwsAEADBadTagException_andIsDeleted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_corrupt"
        deleteAlias(alias)
        val key = genKey(alias)
        val mgr = EncryptedFileManager(key)
        val file = newFile("d.bin")

        mgr.writeBytes(file, ByteArray(128) { 7 })

        // shorten file (corrupt tag)
        RandomAccessFile(file, "rw").use { raf ->
            val newLen = (raf.length() - 8).coerceAtLeast(0) // Corrupt the end of the file
            raf.setLength(newLen)
        }

        try {
            mgr.readBytes(file)
            fail("Expected AEADBadTagException for corrupted file, but no exception was thrown.")
        } catch (_: AEADBadTagException) {
            // Expected
        } catch (e: Exception) {
            fail("Expected AEADBadTagException, but got ${e.javaClass.simpleName}")
        }

        assertTrue("File should be deleted after AEADBadTagException", !file.exists())
    }

    @Test
    fun keyRotation_oldFileThrowsAEADBadTagException_andIsDeleted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_rotate"
        deleteAlias(alias)
        val key1 = genKey(alias)
        val mgr1 = EncryptedFileManager(key1)
        val file = newFile("e.bin")

        mgr1.writeBytes(file, ByteArray(10) { 9 })

        // delete alias -> simulate "key rotation" / reinstall
        deleteAlias(alias)
        val key2 = genKey(alias) // Generates a new key for the same alias
        val mgr2 = EncryptedFileManager(key2)

        try {
            mgr2.readBytes(file)
            fail("Expected AEADBadTagException due to key mismatch, but no exception was thrown.")
        } catch (_: AEADBadTagException) {
            // Expected: The new key won't decrypt the data encrypted with the old key
        } catch (e: Exception) {
            fail("Expected AEADBadTagException, but got ${e.javaClass.simpleName}")
        }

        assertTrue("File should be deleted after failed decryption due to key mismatch", !file.exists())
    }

    @Test
    fun largePayload_mbScale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw AssumptionViolatedException("Test requires Android M (API 23) or higher to run.")
        }

        val alias = "enc_test_key_large"
        deleteAlias(alias)
        val key = genKey(alias)
        val mgr = EncryptedFileManager(key)
        val file = newFile("f.bin")

        val mb = 1_048_576
        val plain = ByteArray(3 * mb) { (it % 251).toByte() } // ~3 MB

        val t0 = SystemClock.elapsedRealtimeNanos()
        mgr.writeBytes(file, plain)
        val t1 = SystemClock.elapsedRealtimeNanos()
        val out = mgr.readBytes(file) // This should succeed
        val t2 = SystemClock.elapsedRealtimeNanos()

        val writeNs = t1 - t0
        val readNs = t2 - t1
        val writeMs = writeNs / 1_000_000.0
        val readMs = readNs / 1_000_000.0
        val sizeBytes = plain.size.toDouble()
        val writeMBps = (sizeBytes / mb) / (writeMs / 1000.0)
        val readMBps = (sizeBytes / mb) / (readMs / 1000.0)

        Log.i(
            "EncryptedFileManagerTest",
            "largePayload: size=%.2f MB, write=%.2f ms (%.2f MB/s), read=%.2f ms (%.2f MB/s)".format(
                sizeBytes / mb,
                writeMs,
                writeMBps,
                readMs,
                readMBps
            )
        )

        assertArrayEquals(plain, out)
    }
}
