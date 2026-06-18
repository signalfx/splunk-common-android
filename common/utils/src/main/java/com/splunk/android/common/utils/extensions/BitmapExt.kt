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

import android.graphics.Bitmap
import android.graphics.Bitmap.Config

operator fun Bitmap.get(x: Int, y: Int): Int {
    return getPixel(x, y)
}

fun Bitmap.copyOrNull(config: Config = this.config ?: Config.ARGB_8888, isMutable: Boolean = true): Bitmap? {
    return try {
        copy(config, isMutable)
    } catch (_: OutOfMemoryError) {
        null
    }
}
