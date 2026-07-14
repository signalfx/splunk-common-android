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

package com.splunk.rum.common.utils.validation.rules

import java.nio.charset.Charset

abstract class StringRule(minimalEvaluation: Boolean = false) : Rule<String>(minimalEvaluation) {

    class CharacterLength(
        private val range: IntRange
    ) : StringRule() {

        constructor(exactLength: Int) : this(IntRange(exactLength, exactLength))

        constructor(start: Int, endInclusive: Int) : this(IntRange(start, endInclusive))

        override fun validate(item: String?): Result {
            val length = item?.length ?: 0
            return if (length in range) {
                Result.Valid
            } else {
                Result.NotValid(Cause.NotInRange(item, range))
            }
        }
    }

    class ByteLength(
        private val range: IntRange,
        private val charset: Charset = Charsets.US_ASCII
    ) : StringRule() {

        constructor(exactLength: Int, charset: Charset = Charsets.US_ASCII) : this(IntRange(exactLength, exactLength), charset)

        constructor(start: Int, endInclusive: Int, charset: Charset = Charsets.US_ASCII) : this(IntRange(start, endInclusive), charset)

        override fun validate(item: String?): Result {

            /**
             * HEURISTIC:
             * In the most ideal case one character = 1B, so if the item has more symbols than
             * maximal allowed by the range it will not matter what is te charset, it will always
             * be bigger.
             */
            if ((item?.length ?: 0) > range.last) {
                return Result.NotValid(Cause.NotInRange(item, range))
            }

            val length = when (charset) {
                Charsets.US_ASCII, Charsets.ISO_8859_1 -> item?.length ?: 0
                else -> item?.toByteArray(charset)?.size ?: 0
            }

            return if (length in range) {
                Result.Valid
            } else {
                Result.NotValid(Cause.NotInRange(item, range))
            }
        }
    }

    class Match(private val regex: Regex) : StringRule() {
        constructor(regexString: String) : this(regexString.toRegex())

        override fun validate(item: String?): Result {
            return if (item?.matches(regex) == true) {
                Result.Valid
            } else {
                Result.NotValid(Cause.DoesNotMatch(item, regex))
            }
        }
    }

    /**
     * Validation failure causes.
     */
    interface Cause : Rule.Cause {
        class NotInRange(val item: String?, val range: IntRange) : Cause
        class DoesNotMatch(val item: String?, val regex: Regex) : Cause
    }
}
