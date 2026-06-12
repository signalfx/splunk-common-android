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

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.splunk.android.common.logger.Logger
import com.splunk.android.common.utils.R
import com.splunk.android.common.utils.RootViewObserver
import com.splunk.android.common.utils.adapters.ActivityLifecycleCallbacksAdapter
import com.splunk.android.common.utils.extensions.activity
import com.splunk.android.common.utils.extensions.findCallbackField
import com.splunk.android.common.utils.extensions.forEachFast
import com.splunk.android.common.utils.extensions.getWindowCallbackChain
import com.splunk.android.common.utils.extensions.rootView
import com.splunk.android.common.utils.extensions.set
import com.splunk.android.common.utils.extensions.toClass
import kotlin.math.min

/**
 * This component ensures that [Window.Callback] provided by [Callback] interface will be in every [Activity].
 * Component also handles [PopupWindow] and [View] added by [WindowManager]. Dispatched are only [Window.Callback.dispatchTouchEvent] and [Window.Callback.dispatchTouchEvent].
 */
object WindowCallbackManager { // FIXME Activity dialog on Android 6

    private const val TAG = "WindowCallbackManager"

    private val PHONE_WINDOW_DECOR_VIEW_21 = "com.android.internal.policy.impl.PhoneWindow\$DecorView".toClass()
    private val PHONE_WINDOW_DECOR_VIEW_23 = "com.android.internal.policy.PhoneWindow\$DecorView".toClass()
    private val DECOR_VIEW_CLASS = "com.android.internal.policy.DecorView".toClass()
    private val POPUP_DECOR_VIEW = "android.widget.PopupWindow\$PopupDecorView".toClass()

    private val SAFE_TO_WRAP_CALLBACKS = listOfNotNull(
        Activity::class.java, AlertDialog::class.java,
        "androidx.appcompat.view.WindowCallbackWrapper".toClass(),
    )

    private enum class ActivityState {
        CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED
    }

    private val choreographer = Choreographer.getInstance()
    private val windowCallbacksCache = ArrayList<Window.Callback>()

    private var isAttached = false

    val filters: MutableList<ViewFilter> = ArrayList()

    val callbacks: MutableList<Callback> = ArrayList()

    fun attach(application: Application) {
        if (isAttached)
            return

        isAttached = true

        RootViewObserver.listeners += rootViewObserverListener
        RootViewObserver.attach(application)

        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    private fun updateWindowCallbacks(rootView: View): Boolean {
        return when (rootView::class.java) {
            PHONE_WINDOW_DECOR_VIEW_21, PHONE_WINDOW_DECOR_VIEW_23 ->
                updateWindowCallbacksActivity21(rootView)
            DECOR_VIEW_CLASS ->
                updateWindowCallbacksActivity24(rootView)
            POPUP_DECOR_VIEW ->
                updateWindowCallbacksPopupWindow(rootView)
            else ->
                updateWindowCallbacksWindowManager(rootView)
        }
    }

    private fun updateWindowCallbacksActivity21(rootView: View): Boolean {
        val window = try {
            CallbackWindow.Impl21(rootView)
        } catch (e: Exception) {
            Logger.e1(TAG, "updateWindowCallbacksActivity21", e)
            return false
        }

        updateWindowCallbacks(rootView, window)
        return true
    }

    private fun updateWindowCallbacksActivity24(rootView: View): Boolean {
        val window = try {
            CallbackWindow.Impl24(rootView)
        } catch (e: Exception) {
            Logger.e1(TAG, "updateWindowCallbacksActivity24", e)
            return false
        }

        updateWindowCallbacks(rootView, window)
        return true
    }

    private fun updateWindowCallbacksPopupWindow(rootView: View): Boolean {
        if (rootView !is ViewGroup)
            return false

        var observerView = rootView.getChildAt(0) as? ObserverView

        if (observerView == null) {
            observerView = ObserverView(rootView.context)

            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i)

                rootView.removeViewAt(i)
                observerView.addView(child)
            }

            rootView.addView(observerView)
        }

        updateWindowCallback(rootView, observerView)
        return true
    }

    private fun updateWindowCallbacksWindowManager(rootView: View): Boolean {
        /* FIXME This is not supported at the moment
         *  - Crashes when remove View (WindowManagerGlobal, line 329) -> Observe mRoots in WindowManagerGlobal and set original root View back
         *  - Keyboard is not shown when tap on EditText -> ???
         */

        /*val newRootView = object : FrameLayout(rootView.context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                println("dispatchTouchEvent $event")
                return super.dispatchTouchEvent(event)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                println("dispatchKeyEvent $event")
                return super.dispatchKeyEvent(event)
            }
        }
        newRootView.layoutParams = rootView.layoutParams

        val layoutParams = FrameLayout.LayoutParams(rootView.layoutParams.width, rootView.layoutParams.height)
        val rootViewImpl = rootView.parent

        rootView.invoke<Unit>("assignParent", null to ViewParent::class)
        newRootView.addView(rootView, layoutParams)

        newRootView.invoke<Unit>("assignParent", rootViewImpl to ViewParent::class)
        rootViewImpl.set("mView", newRootView)

        val fallbackEventHandler = rootViewImpl.get<Any>("mFallbackEventHandler")
        fallbackEventHandler?.set("mView", newRootView)

        newRootView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES*/

        return false
    }

    private fun updateWindowCallback(rootView: View, observerView: ObserverView) {
        val wrappedCallbacks = observerView.callback.getWindowCallbackChain(windowCallbacksCache)
        var windowCallbackToWrap: Window.Callback = observerView.callback
        var expectedWindowCallbacks = rootView.expectedWindowCallbacks

        if (expectedWindowCallbacks == null) {
            expectedWindowCallbacks = ArrayList()
            rootView.expectedWindowCallbacks = expectedWindowCallbacks
        }

        for (callback in callbacks)
            if (expectedWindowCallbacks.none { it.first == callback }) {
                windowCallbackToWrap = callback.wrapWindowCallback(rootView, windowCallbackToWrap)
                expectedWindowCallbacks += callback to windowCallbackToWrap
                wrappedCallbacks += windowCallbackToWrap
            }

        for ((callback, windowCallback) in expectedWindowCallbacks)
            if (windowCallback !in wrappedCallbacks)
                windowCallbackToWrap = callback.wrapWindowCallback(rootView, windowCallbackToWrap)

        observerView.callback = windowCallbackToWrap
        windowCallbacksCache.clear()
    }

    private fun updateWindowCallbacks(rootView: View, window: CallbackWindow) {
        var expectedWindowCallbacks = rootView.expectedWindowCallbacks

        if (expectedWindowCallbacks == null) {
            expectedWindowCallbacks = ArrayList()
            rootView.expectedWindowCallbacks = expectedWindowCallbacks
        }

        val wrappedCallbacks = window.callback?.getWindowCallbackChain(windowCallbacksCache) ?: windowCallbacksCache
        val windowCallbackHead = wrappedCallbacks.getOrNull(0)
        val isSafeToWrapHead = windowCallbackHead == null || SAFE_TO_WRAP_CALLBACKS.any { it.isAssignableFrom(windowCallbackHead::class.java) }
        var windowCallbackToWrap = if (isSafeToWrapHead) windowCallbackHead else wrappedCallbacks.getOrNull(1) ?: windowCallbackHead
        var isWrapperChainChanged = false

        // Check if callback was created
        for (callback in callbacks)
            if (expectedWindowCallbacks.none { it.first === callback }) {
                windowCallbackToWrap = callback.wrapWindowCallback(rootView, windowCallbackToWrap)
                wrappedCallbacks.add(min(wrappedCallbacks.size, 1), windowCallbackToWrap)
                expectedWindowCallbacks += callback to windowCallbackToWrap
                isWrapperChainChanged = true
            }

        // Check if expected window callbacks are still there
        for ((callback, windowCallback) in expectedWindowCallbacks)
            if (windowCallback !in wrappedCallbacks) {
                windowCallbackToWrap = callback.wrapWindowCallback(rootView, windowCallback)
                isWrapperChainChanged = true
            }

        if (isWrapperChainChanged)
            if (!isSafeToWrapHead && windowCallbackHead != null) {
                val callbackWrapperField = windowCallbackHead.findCallbackField()

                if (callbackWrapperField != null)
                    windowCallbackHead.set(callbackWrapperField, windowCallbackToWrap)
                else
                    window.callback = windowCallbackToWrap
            } else
                window.callback = windowCallbackToWrap

        windowCallbacksCache.clear()
    }

    private fun removeWindowCallbacks(rootView: View) {
        when (rootView::class.java) {
            PHONE_WINDOW_DECOR_VIEW_21, PHONE_WINDOW_DECOR_VIEW_23 ->
                removeWindowCallbacksActivity21(rootView)
            DECOR_VIEW_CLASS ->
                removeWindowCallbacksActivity24(rootView)
            POPUP_DECOR_VIEW ->
                removeWindowCallbacksPopupWindow(rootView)
            else ->
                removeWindowCallbacksWindowManager(rootView)
        }
    }

    private fun removeWindowCallbacksActivity21(rootView: View) {
        val window = try {
            CallbackWindow.Impl21(rootView)
        } catch (e: Exception) {
            Logger.e1(TAG, "removeWindowCallbacksActivity21", e)
            return
        }

        removeWindowCallbacks(rootView, window)
    }

    private fun removeWindowCallbacksActivity24(rootView: View) {
        val window = try {
            CallbackWindow.Impl24(rootView)
        } catch (e: Exception) {
            Logger.e1(TAG, "removeWindowCallbacksActivity24", e)
            return
        }

        removeWindowCallbacks(rootView, window)
    }

    private fun removeWindowCallbacks(rootView: View, window: CallbackWindow) {
        val expectedWindowCallbacks = rootView.expectedWindowCallbacks ?: return
        val wrappedCallbacks = window.callback?.getWindowCallbackChain(windowCallbacksCache) ?: return

        for (i in wrappedCallbacks.indices.reversed()) {
            val callback = wrappedCallbacks[i]

            for (j in expectedWindowCallbacks.indices) {
                val expectedWindowCallback = expectedWindowCallbacks[j]

                if (expectedWindowCallback.second !== callback)
                    continue

                if (i == 0)
                    window.callback = wrappedCallbacks.getOrNull(1)
                else {
                    val previousCallback = wrappedCallbacks[i - 1]
                    val nextCallback = wrappedCallbacks.getOrNull(i + 1)

                    if (previousCallback is WindowCallbackWrapper)
                        previousCallback.callback = nextCallback
                    else {
                        val callbackField = previousCallback.findCallbackField() ?: continue
                        previousCallback.set(callbackField, nextCallback)
                    }
                }

                expectedWindowCallbacks.removeAt(j)
                break
            }
        }

        windowCallbacksCache.clear()
    }

    private fun removeWindowCallbacksPopupWindow(rootView: View) {}

    private fun removeWindowCallbacksWindowManager(rootView: View) {}

    @Suppress("UNCHECKED_CAST")
    private var View.expectedWindowCallbacks: MutableList<Pair<Callback, Window.Callback>>?
        get() = getTag(R.id.sl_tag_window_callback_expected_callbacks) as? MutableList<Pair<Callback, Window.Callback>>
        set(value) = run { setTag(R.id.sl_tag_window_callback_expected_callbacks, value) }

    private var View.frameCallback: FrameCallback?
        get() = getTag(R.id.sl_tag_window_callback_frame_callback) as? FrameCallback
        set(value) = setTag(R.id.sl_tag_window_callback_frame_callback, value)

    private var View.expectedActivity: Activity?
        get() = getTag(R.id.sl_tag_window_callback_expected_activity) as? Activity
        set(value) = setTag(R.id.sl_tag_window_callback_expected_activity, value)

    private var Activity.state: ActivityState?
        get() = rootView?.getTag(R.id.sl_tag_window_callback_expected_activity) as? ActivityState
        set(value) = rootView.run { this?.setTag(R.id.sl_tag_window_callback_expected_activity, value) }

    private val rootViewObserverListener = object : RootViewObserver.Listener {
        override fun onAdded(view: View) {
            callbacks.forEachFast { it.onRootViewAdded(view) }

            if (filters.any { it.isRejected(view) })
                return

            view.expectedActivity = view.activity

            val isProcessed = updateWindowCallbacks(view)

            if (isProcessed) {
                val frameCallback = FrameCallback(view)
                view.frameCallback = frameCallback

                choreographer.postFrameCallback(frameCallback)
            }
        }

        override fun onRemoved(view: View) {
            callbacks.forEachFast { it.onRootViewRemoved(view) }

            view.expectedActivity = null

            val frameCallback = view.frameCallback ?: return
            choreographer.removeFrameCallback(frameCallback)
            view.frameCallback = null

            val expectedWindowCallbacks = view.expectedWindowCallbacks ?: return
            removeWindowCallbacks(view)
            expectedWindowCallbacks.clear()
            view.expectedWindowCallbacks = null
        }
    }

    private val activityLifecycleCallbacks = object : ActivityLifecycleCallbacksAdapter {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activity.state = ActivityState.CREATED
        }

        override fun onActivityStarted(activity: Activity) {
            activity.state = ActivityState.STARTED
        }

        override fun onActivityResumed(activity: Activity) {
            activity.state = ActivityState.RESUMED
        }

        override fun onActivityPaused(activity: Activity) {
            activity.state = ActivityState.PAUSED
        }

        override fun onActivityStopped(activity: Activity) {
            activity.state = ActivityState.STOPPED
        }

        override fun onActivityDestroyed(activity: Activity) {
            activity.state = ActivityState.DESTROYED
        }
    }

    private class ObserverView(context: Context) : FrameLayout(context) {

        var callback: Window.Callback = SuperWindowCallbackWrapper()

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return callback.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return callback.dispatchKeyEvent(event)
        }

        private fun superDispatchTouchEvent(event: MotionEvent): Boolean {
            return super.dispatchTouchEvent(event)
        }

        private fun superDispatchKeyEvent(event: KeyEvent): Boolean {
            return super.dispatchKeyEvent(event)
        }

        private inner class SuperWindowCallbackWrapper : WindowCallbackWrapper(null) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                return superDispatchTouchEvent(event)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean { // FIXME back button is missing, look at the ViewRootImpl
                return superDispatchKeyEvent(event)
            }
        }
    }

    private class FrameCallback(private val rootView: View) : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (rootView.parent == null)
                return

            val activity = rootView.activity

            if (rootView.expectedActivity !== activity) {
                val expectedWindowCallbacks = rootView.expectedWindowCallbacks

                rootView.expectedActivity = activity
                rootView.expectedWindowCallbacks = null

                if (expectedWindowCallbacks != null) {
                    removeWindowCallbacks(rootView)
                    expectedWindowCallbacks.clear()
                }
            }

            choreographer.postFrameCallback(this)

            if (activity?.state == ActivityState.RESUMED)
                updateWindowCallbacks(rootView)
        }
    }

    interface Callback {
        fun onRootViewAdded(rootView: View) {}
        fun onRootViewRemoved(rootView: View) {}
        fun wrapWindowCallback(rootView: View, callback: Window.Callback?): Window.Callback
    }

    interface ViewFilter {
        fun isRejected(rootView: View): Boolean
    }
}
