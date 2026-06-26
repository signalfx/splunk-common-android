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

import com.splunk.rum.common.storage.filemanager.IFileManager
import java.io.File

class FileSimplePermanentCache(
    file: File,
    fileManager: IFileManager
) : FilePermanentCache(fileManager), ISimplePermanentCache {

    private val filePath: String = file.absolutePath

    override fun readBytes(): ByteArray? {
        return readBytes(filePath)
    }

    override fun writeBytes(bytes: ByteArray) {
        return writeBytes(filePath, bytes)
    }
}
