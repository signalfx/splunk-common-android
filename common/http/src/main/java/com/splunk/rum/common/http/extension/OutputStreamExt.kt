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

package com.splunk.rum.common.http.extension

import com.splunk.rum.common.http.model.part.AbstractContent
import com.splunk.rum.common.http.model.part.ByteArrayContent
import com.splunk.rum.common.http.model.part.Content
import com.splunk.rum.common.http.model.part.FileContent
import com.splunk.rum.common.http.model.part.StringContent
import java.io.FileNotFoundException
import java.io.OutputStream

internal fun OutputStream.write(string: String) {
    write(string.toByteArray())
}

private const val LINE_END = "\r\n"

fun OutputStream.write(contents: List<Content>, boundary: String) {
    for (content in contents) {
        if (content is FileContent && !content.file.exists())
            throw FileNotFoundException("File body part '${content.file.absolutePath}' doesn't exist")

        write("--$boundary$LINE_END")
        write(content.toContentDispositionHeader() + LINE_END)

        if (content is FileContent || content is ByteArrayContent || content is AbstractContent)
            write("Content-Transfer-Encoding: binary$LINE_END")

        if (content.encoding != null)
            write("Content-Encoding: ${content.encoding}$LINE_END")

        write("Content-Type: ${content.type}$LINE_END")
        write("Content-Length: ${content.getLength()}$LINE_END")
        write(LINE_END)

        when (content) {
            is FileContent -> {
                val stream = content.file.inputStream()
                stream.copyTo(this)
                stream.close()
            }
            is StringContent ->
                write(content.string)
            is ByteArrayContent ->
                write(content.bytes)
            is AbstractContent ->
                content.copyInto(this)
        }

        write(LINE_END)
    }

    write("--$boundary--$LINE_END")
}

private fun Content.toContentDispositionHeader(): String {
    val builder = StringBuilder()
    builder.append("Content-Disposition: form-data")
    builder.append("; ").append("name=\"$dispositionName\"")
    dispositionFileName?.let { builder.append("; ").append("filename=\"$it\"") }
    return builder.toString()
}
