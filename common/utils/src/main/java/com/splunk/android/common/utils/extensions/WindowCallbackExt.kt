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

import android.app.Activity
import android.app.AlertDialog
import android.view.Window
import com.splunk.android.common.utils.window.WindowCallbackWrapper
import java.lang.reflect.Field

internal fun Window.Callback.getWindowCallbackChain(result: MutableList<Window.Callback> = ArrayList()): MutableList<Window.Callback> {
    var localCallback: Window.Callback? = this

    while (localCallback != null) {
        result += localCallback

        localCallback = if (localCallback is Activity || localCallback is AlertDialog)
            break
        else if (localCallback is WindowCallbackWrapper)
            localCallback.callback
        else {
            val callbackWrapperField = localCallback.findCallbackField() ?: break
            localCallback.get(callbackWrapperField)
        }
    }

    return result
}

internal fun Window.Callback.findCallbackField(): Field? {
    var windowCallbackClass: Class<*>? = this::class.java

    while (windowCallbackClass != null && windowCallbackClass != Object::class.java) {
        for (field in windowCallbackClass.declaredFields)
            if (field.type == Window.Callback::class.java)
                return field // Assume this is wrapped callback

        windowCallbackClass = windowCallbackClass.superclass
    }

    return null
}
