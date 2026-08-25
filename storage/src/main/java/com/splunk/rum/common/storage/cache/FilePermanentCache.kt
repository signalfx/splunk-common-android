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

package com.splunk.rum.common.storage.cache

import com.splunk.rum.common.logger.Logger
import com.splunk.rum.common.storage.extensions.createNewFileOnPath
import com.splunk.rum.common.storage.extensions.toFile
import com.splunk.rum.common.storage.filemanager.IFileManager

/**
 * File-backed cache whose writes are not atomic: the configured [IFileManager] writes directly to
 * the destination. It therefore does not guarantee recovery from process death during a write or
 * coordination between multiple cache instances or Android processes.
 */
open class FilePermanentCache(
    private val fileManager: IFileManager
) : IPermanentCache {

    override fun readBytes(key: String): ByteArray? {
        val file = key.toFile()

        if (!file.exists()) {
            return null
        }

        return try {
            fileManager.readBytes(file)
        } catch (e: Exception) {
            Logger.w(TAG, "loadBytes(): Failed to load bytes from a file due to ${e.message}!")
            null
        }
    }

    override fun writeBytes(key: String, bytes: ByteArray) {
        val file = key.toFile()

        try {
            file.createNewFileOnPath()
            fileManager.writeBytes(file, bytes)
        } catch (e: Exception) {
            Logger.w(TAG, "saveBytes(): Failed to save bytes due to ${e.message}!")
        }
    }

    private companion object {
        const val TAG = "FilePermanentCache"
    }
}
