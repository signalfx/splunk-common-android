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

package com.splunk.android.common.logger.extensions

import android.text.SpannableStringBuilder
import com.splunk.android.common.logger.Log
import com.splunk.android.common.logger.formatter.DefaultLogFormatter
import com.splunk.android.common.logger.formatter.LogFormatter

/**
 * Format list of [Log] into user readable message.
 *
 * @param logLevel all logs on lower level will be ignored
 */
fun List<Log>.toUserMessage(
    logLevel: Log.Level = Log.Level.VERBOSE,
    formatter: LogFormatter = DefaultLogFormatter()
): CharSequence {
    return filter { it.level <= logLevel }
        .joinTo(
            buffer = SpannableStringBuilder(),
            separator = "\n"
        ) { formatter.format(it) }
}
