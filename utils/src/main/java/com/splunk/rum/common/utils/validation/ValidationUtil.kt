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

package com.splunk.rum.common.utils.validation

import com.splunk.rum.common.utils.validation.rules.Rule
import com.splunk.rum.common.utils.validation.rulesets.Ruleset

object ValidationUtil {

    fun <T> validate(item: T?, ruleset: Ruleset<T>): Boolean {
        var isValid = true

        ruleset.rules.sortedByDescending { it.minimalEvaluation }.forEach { rule ->
            val result = rule.validate(item)
            if (result is Rule.Result.NotValid) {
                ruleset.onRuleFailure(result.cause)
                if (rule.minimalEvaluation) { return false }
                isValid = false
            }
        }

        return isValid
    }

    fun <T> validate(item: T?, rules: List<Rule<T>>): ValidationResult {
        val causes: MutableList<Rule.Cause> = mutableListOf()

        rules.sortedByDescending { it.minimalEvaluation }.forEach { rule ->
            val result = rule.validate(item)
            if (result is Rule.Result.NotValid) {
                causes += result.cause
                if (rule.minimalEvaluation) { return ValidationResult.NotValid(causes) }
            }
        }

        return if (causes.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.NotValid(causes)
        }
    }
}
