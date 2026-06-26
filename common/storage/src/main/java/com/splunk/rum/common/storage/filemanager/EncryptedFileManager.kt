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

package com.splunk.rum.common.storage.filemanager

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class EncryptedFileManager(
    private val key: SecretKey
) : IFileManager {

    override fun writeBytes(file: File, bytes: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Keystore generates IV
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        require(iv.size == IV_SIZE_BYTES) { "Unexpected GCM IV size=${iv.size}" }

        val encWithTag = cipher.doFinal(bytes)

        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tmp = File(file.absolutePath + ".tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(iv)
            fos.write(encWithTag)
            fos.flush()
            fos.fd.sync()
        }

        if (!tmp.renameTo(file)) {
            // Initial atomic rename failed (e.g., target file locked, permission issue, or FS limitation).
            // Attempt fallback: explicitly delete existing target file and retry rename.

            if (file.exists() && !file.delete()) {
                // Target file exists but could not be deleted. Cannot proceed safely.
                Log.d(TAG, "writeBytes(file: ${file.absolutePath}) failed to delete existing target file.")
                tmp.delete() // Clean up temp file
                throw IOException("Failed to delete existing target file: ${file.absolutePath}")
            }

            // Target path is now clear (either didn't exist or was deleted). Retry rename.
            if (!tmp.renameTo(file)) {
                // Rename failed even after clearing the target path (severe FS issue).
                Log.d(TAG, "writeBytes(file: ${file.absolutePath}) failed to rename tmp file to target after clearing path.")
                tmp.delete() // Clean up temp file
                throw IOException("Failed to rename temporary file to ${file.absolutePath} after clearing target.")
            }
        }
    }

    override fun readBytes(file: File): ByteArray {
        if (!file.exists()) {
            Log.d(TAG, "readBytes(file: ${file.absolutePath}) file does not exist.")

            throw FileNotFoundException("File not found: ${file.absolutePath}")
        }

        RandomAccessFile(file, READ_ONLY_MODE).use { raf ->
            val len = raf.length()
            if (len < IV_SIZE_BYTES + MIN_GCM_TAG_BYTES) {
                Log.d(TAG, "readBytes(file: ${file.absolutePath}) len is too small.")

                throw IOException("File content too short or malformed: expected at least ${IV_SIZE_BYTES + MIN_GCM_TAG_BYTES} bytes, but got $len")
            }

            val iv = ByteArray(IV_SIZE_BYTES)
            try {
                raf.readFullyCompat(iv)
            } catch (e: Exception) {
                Log.d(TAG, "readBytes(file: ${file.absolutePath}) Failed to read IV.", e)

                throw IOException("Failed to read IV from file: ${file.absolutePath}", e)
            }

            val encLen = (len - IV_SIZE_BYTES).toInt()
            val encWithTag = ByteArray(encLen)

            try {
                raf.readFullyCompat(encWithTag)
            } catch (e: Exception) {
                Log.d(TAG, "readBytes(file: ${file.absolutePath}) Failed to read encrypted data with tag.", e)

                throw IOException("Failed to read encrypted data with tag from file: ${file.absolutePath}", e)
            }

            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(encWithTag)
            } catch (e: AEADBadTagException) {
                file.delete()
                Log.d(TAG, "readBytes(file: ${file.absolutePath}) failed with AEADBadTagException (invalid tag). File deleted.", e)

                throw e
            } catch (e: Exception) {
                Log.d(TAG, "readBytes(file: ${file.absolutePath}) failed during decryption.", e)

                throw GeneralSecurityException("Decryption failed for file: ${file.absolutePath}", e)
            }
        }
    }

    internal companion object {
        const val TAG = "EncryptedFileManager"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MIN_GCM_TAG_BYTES = 16
        const val READ_ONLY_MODE = "r"
    }
}

private fun RandomAccessFile.readFullyCompat(dst: ByteArray) {
    var off = 0
    while (off < dst.size) {
        val n = read(dst, off, dst.size - off)
        if (n < 0) throw EOFException("Unexpected EOF")
        off += n
    }
}
