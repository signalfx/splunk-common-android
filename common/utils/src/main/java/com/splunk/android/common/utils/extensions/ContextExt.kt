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

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.splunk.android.common.utils.runOnAndroidAtLeast

val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

val Context.isUiContextCompat: Boolean
    get() = runOnAndroidAtLeast(Build.VERSION_CODES.S) { isUiContext } ?: true

private var fragmentSpecialEffectsControllerViewTag: Int? = 0

internal fun Context.getFragmentSpecialEffectsControllerViewTag(): Int? { // androidx.fragment.R.id.special_effects_controller_view_tag
    if (fragmentSpecialEffectsControllerViewTag == 0) {
        val name = "$packageName:id/special_effects_controller_view_tag"
        fragmentSpecialEffectsControllerViewTag = runCatching { resources.getValue(name).resourceId }.getOrNull()
    }

    return fragmentSpecialEffectsControllerViewTag
}
