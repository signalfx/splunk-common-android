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

package com.splunk.android.common.http

import android.net.TrafficStats
import com.splunk.android.common.http.extension.toHeaders
import com.splunk.android.common.http.extension.toURL
import com.splunk.android.common.http.extension.write
import com.splunk.android.common.http.model.Header
import com.splunk.android.common.http.model.Query
import com.splunk.android.common.http.model.Response
import com.splunk.android.common.http.model.part.Content
import com.splunk.android.common.utils.extensions.safeSubmit
import com.splunk.android.common.utils.thread.NamedThreadFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.Executors

class HttpClient {

    private val executor = Executors.newSingleThreadExecutor(NamedThreadFactory("HttpClient"))

    fun makeGetRequest(url: String, queries: List<Query>, headers: List<Header>, callback: Callback) {
        executor.safeSubmit {
            makeRequest(url, "GET", queries, headers, callback)
        }
    }

    fun makePostRequest(url: String, queries: List<Query>, headers: List<Header>, body: String, callback: Callback) {
        makePostRequest(url, queries, headers, body.toByteArray(), callback)
    }

    fun makePostRequest(url: String, queries: List<Query>, headers: List<Header>, body: ByteArray, callback: Callback) {
        executor.safeSubmit {
            val finalHeaders = headers + Header("Content-Length", body.size.toString())

            makeRequest(url, "POST", queries, finalHeaders, callback) {
                it.write(body)
            }
        }
    }

    fun makePostRequest(url: String, queries: List<Query>, headers: List<Header>, body: File, callback: Callback) {
        executor.safeSubmit {
            val finalHeaders = headers + Header("Content-Length", body.length().toString())

            makeRequest(url, "POST", queries, finalHeaders, callback) { os ->
                body.inputStream().buffered().use { it.copyTo(os) }
            }
        }
    }

    fun makePostRequest(url: String, queries: List<Query>, headers: List<Header>, contents: List<Content>, callback: Callback) {
        executor.safeSubmit {
            val boundary = UUID.randomUUID().toString()
            val stream = ByteArrayOutputStream()

            try {
                stream.write(contents, boundary)
            } catch (e: FileNotFoundException) {
                callback.onFailed(e)
                return@safeSubmit
            }

            val finalHeaders = headers + listOf(
                Header("Content-Type", "multipart/form-data; boundary=$boundary"),
                Header("Content-Length", stream.size().toString())
            )

            makeRequest(url, "POST", queries, finalHeaders, callback) {
                stream.writeTo(it)
                stream.close()
            }
        }
    }

    private fun makeRequest(url: String, method: String, queries: List<Query>, headers: List<Header>, callback: Callback, onOutput: ((OutputStream) -> Unit)? = null) {
        TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG)

        try {
            val connection = url.toURL(queries).openConnection() as HttpURLConnection

            for (header in headers)
                connection.setRequestProperty(header.name, header.value)

            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.requestMethod = method
            connection.doOutput = onOutput != null
            connection.doInput = true

            val response = try {
                if (onOutput != null)
                    connection.outputStream.buffered().use {
                        onOutput(it)
                        it.flush()
                    }

                val body = when (connection.responseCode) {
                    204 ->
                        byteArrayOf()
                    in 0..399 ->
                        connection.inputStream.use { it.readBytes() }
                    else ->
                        connection.errorStream?.use { it.readBytes() } ?: byteArrayOf()
                }

                Response(
                    code = connection.responseCode,
                    headers = connection.headerFields.toHeaders(),
                    body = body
                )
            } catch (e: Exception) {
                callback.onFailed(e)
                return
            }

            callback.onSuccess(response)
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    interface Callback {
        fun onSuccess(response: Response)
        fun onFailed(e: Exception)
    }

    private companion object {
        const val TIMEOUT = 15_000
        const val TRAFFIC_STATS_TAG = 0xDA7A
    }
}
