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

package com.splunk.rum.common.utils.validation.extensions

import com.splunk.rum.common.utils.validation.ValidationUtil
import com.splunk.rum.common.utils.validation.rulesets.Ruleset

inline fun <T> runIfValid(item: T?, ruleset: Ruleset<T>, crossinline operation: () -> Unit) {
    if (ValidationUtil.validate(item, ruleset)) {
        operation()
    }
}

inline fun <T> runIfValid(vararg itemAndRulesetPairs: Pair<T?, Ruleset<T>>, crossinline operation: () -> Unit) {
    if (itemAndRulesetPairs.all { ValidationUtil.validate(it.first, it.second) }) {
        operation()
    }
}

fun <T> validate(item: T?, ruleset: Ruleset<T>): Boolean = ValidationUtil.validate(item, ruleset)

fun <T> validate(vararg itemAndRulesetPairs: Pair<T?, Ruleset<T>>): Boolean =
    itemAndRulesetPairs.all { ValidationUtil.validate(it.first, it.second) }
