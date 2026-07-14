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

import com.splunk.rum.common.http.model.Query
import java.net.URL
import java.net.URLEncoder

internal fun String.toURL(queries: List<Query> = emptyList()): URL {
    val builder = StringBuilder(this)

    if (queries.isNotEmpty())
        builder.append('?')

    for (i in queries.indices) {
        val query = queries[i]

        builder.append(URLEncoder.encode(query.name)).append('=').append(URLEncoder.encode(query.value))

        if (i != queries.lastIndex)
            builder.append('&')
    }

    return URL(builder.toString())
}
