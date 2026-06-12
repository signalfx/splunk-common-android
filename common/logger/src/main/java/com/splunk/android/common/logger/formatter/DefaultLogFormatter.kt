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

package com.splunk.android.common.logger.formatter

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import com.splunk.android.common.logger.Log
import com.splunk.android.common.logger.Log.Level.ERROR
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Default implementation of [LogFormatter].
 * Output format without throwable is "time(yyyy-MM-dd HH:mm:ss.SSS) level/tag: message".
 * Format with throwable is "time(yyyy-MM-dd HH:mm:ss.SSS) level/tag: message\n\tthrowable".
 */
class DefaultLogFormatter(
    private val isDateTimeEnabled: Boolean = true
) : LogFormatter {

    override fun format(log: Log): CharSequence {
        val builder = SpannableStringBuilder()

        if (isDateTimeEnabled)
            builder.append(TIME_FORMATTER.format(log.time)).append(' ')

        builder.append(log.level.toString()).append('/').append(log.tag).append(": ").append(log.message)

        if (log.throwable != null) {
            val writer = StringWriter()
            log.throwable.printStackTrace(PrintWriter(writer))
            var throwable = writer.toString()
            throwable = throwable.substring(0, throwable.length - 2) // remove last new line
            throwable = throwable.replace("\n", "\n\t\t")

            builder.append("\n\t$throwable")
        }

        if (log.level == ERROR)
            builder.setSpan(ForegroundColorSpan(Color.RED), 0, builder.length, 0)

        return builder
    }

    private companion object {
        val TIME_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)
    }
}
