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

package com.splunk.android.common.utils.legacy

internal object Constants {

    // When view doesn't have id set or service method, we are falling back to this constant
    const val UNKNOWN_VIEW_IDENTIFIER = "-"

    // TabLayout items usually don't have id so we are identifying them using complex identifier
    // defined by this template
    const val TAB_VIEW_IDENTIFIER_TEMPLATE = "%s position=[%s] tag=[%s]"
}
