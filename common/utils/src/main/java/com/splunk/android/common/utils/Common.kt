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

package com.splunk.android.common.utils

import android.content.res.Resources
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

private val density: Float = Resources.getSystem().displayMetrics.density

/**
 * Perform [block] when Android version is at least [versionCode].
 *
 * @param versionCode Android SDK version code from [Build.VERSION_CODES]
 */
@ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
inline fun <T> runOnAndroidAtLeast(versionCode: Int, crossinline block: () -> T): T? {
    return if (Build.VERSION.SDK_INT >= versionCode) block() else null
}

fun dpToPx(dp: Float): Int = (dp * density + 0.5f).toInt()

fun dpToPxF(dp: Float): Float = dp * density

fun pxToDp(px: Int): Float = px / density
