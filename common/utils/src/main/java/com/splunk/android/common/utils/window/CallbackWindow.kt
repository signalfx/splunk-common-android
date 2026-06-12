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

package com.splunk.android.common.utils.window

import android.view.View
import android.view.Window
import com.splunk.android.common.logger.Logger
import com.splunk.android.common.utils.extensions.get
import com.splunk.android.common.utils.extensions.set

internal sealed interface CallbackWindow {

    var callback: Window.Callback?

    class Impl21(rootView: View) : CallbackWindow {

        private val window: Any = rootView.get("this\$0")!!

        override var callback: Window.Callback?
            get() {
                return try {
                    window.get("mCallback")
                } catch (e: NoSuchFieldException) {
                    Logger.e1(TAG, "getCallback", e)
                    null
                }
            }
            set(value) {
                try {
                    window.set("mCallback", value)
                } catch (e: NoSuchFieldException) {
                    Logger.e1(TAG, "setCallback", e)
                }
            }

        private companion object {
            const val TAG = "Impl21"
        }
    }

    class Impl24(rootView: View) : CallbackWindow {

        private val window: Window = rootView.get("mWindow")!!

        override var callback: Window.Callback?
            get() {
                return window.callback
            }
            set(value) {
                window.callback = value
            }
    }
}
