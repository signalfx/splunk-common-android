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

package com.splunk.android.common.utils.extensions

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

fun ByteArray.compress(): ByteArray {
    val outputStream = ByteArrayOutputStream(size)

    val deflaterStream = DeflaterOutputStream(outputStream, Deflater(Deflater.BEST_COMPRESSION))
    deflaterStream.write(this)
    deflaterStream.close()

    return outputStream.toByteArray()
}

fun ByteArray.decompress(): ByteArray? {
    return runCatching { InflaterInputStream(ByteArrayInputStream(this)).readBytes() }.getOrNull()
}
