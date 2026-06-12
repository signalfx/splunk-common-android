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

package com.splunk.android.common.storage

import com.splunk.android.common.logger.Logger
import com.splunk.android.common.storage.cache.IPermanentCache
import java.io.File

class Storage(private val permanentCache: IPermanentCache) : IStorage {

    override fun readBytes(file: File): ByteArray? {
        val bytes = try {
            permanentCache.readBytes(file.absolutePath)
        } catch (e: Exception) {
            Logger.d(TAG, "readBytes(): file = ${file.name} - failed with Exception: ${e.message}")
            return null
        }

        Logger.d(TAG, "readBytes(): file = ${file.name}")
        return bytes
    }

    override fun writeBytes(file: File, bytes: ByteArray): Boolean {
        try {
            permanentCache.writeBytes(file.absolutePath, bytes)
        } catch (e: Exception) {
            Logger.d(TAG, "writeBytes(): file = ${file.name}, bytes = ${bytes.size} - failed with Exception: ${e.message}")
            return false
        }

        Logger.d(TAG, "writeBytes(): file = ${file.name}, bytes = ${bytes.size}")
        return true
    }

    override fun readText(file: File): String? {
        return readBytes(file)?.toString(Charsets.UTF_8)
    }

    override fun writeText(file: File, text: String, append: Boolean): Boolean {
        val bytes = if (append) {
            val actualText = readText(file) ?: ""
            (actualText + text).toByteArray()
        } else
            text.toByteArray()

        return writeBytes(file, bytes)
    }

    private companion object {
        const val TAG = "Storage"
    }
}
