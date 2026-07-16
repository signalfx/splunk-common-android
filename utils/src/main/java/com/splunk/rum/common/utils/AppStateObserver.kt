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

package com.splunk.rum.common.utils

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionManager
import android.util.ArrayMap
import android.view.Choreographer
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.splunk.rum.common.logger.Logger
import com.splunk.rum.common.utils.adapters.ActivityLifecycleCallbacksAdapter
import com.splunk.rum.common.utils.extensions.forEachFast
import com.splunk.rum.common.utils.extensions.getStatic
import com.splunk.rum.common.utils.extensions.toClass
import java.lang.ref.WeakReference

// MARK Required Proguard rules

object AppStateObserver {

    private const val TAG = "AppStateObserver"

    private val FRAGMENT_ACTIVITY_CLASS = "androidx.fragment.app.FragmentActivity".toClass()

    private val choreographer = Choreographer.getInstance()

    private var application: Application? = null
    private var runningActivities = 0
    private var aliveActivities = 0

    val listeners: MutableList<Listener> = mutableListOf()

    fun attach(application: Application) {
        if (this.application != null)
            return

        application.registerActivityLifecycleCallbacks(activityLifecycleCallback)
        choreographer.postFrameCallback(frameCallback)

        this.application = application
    }

    fun detach() {
        application?.unregisterActivityLifecycleCallbacks(activityLifecycleCallback)
        choreographer.removeFrameCallback(frameCallback)

        application = null
    }

    private val frameCallback = object : Choreographer.FrameCallback {

        private var isViewTransitionRunning = false

        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            checkViewTransitions()
        }

        private fun checkViewTransitions() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
                return

            val runningTransitions = try {
                TransitionManager::class.getStatic<ThreadLocal<WeakReference<ArrayMap<ViewGroup, ArrayList<Transition>>>>>("sRunningTransitions")
            } catch (e: NoSuchFieldException) {
                Logger.e1(TAG, "checkViewTransitions", e)
                return
            }

            val isRunning = runningTransitions?.get()?.get()?.any { it.value.isNotEmpty() } ?: false

            if (isRunning != isViewTransitionRunning) {
                isViewTransitionRunning = isRunning

                if (isRunning)
                    listeners.forEachFast { it.onViewTransitionStarted() }
                else
                    listeners.forEachFast { it.onViewTransitionEnded() }
            }
        }
    }

    private val activityLifecycleCallback = object : ActivityLifecycleCallbacksAdapter {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (++aliveActivities == 1)
                listeners.forEachFast { it.onAppStarted() }

            if (FRAGMENT_ACTIVITY_CLASS?.isInstance(activity) == true) {
                activity as FragmentActivity
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true)
            }
        }

        override fun onActivityStarted(activity: Activity) {
            if (++runningActivities == 1)
                listeners.forEachFast { it.onAppForegrounded() }
        }

        override fun onActivityStopped(activity: Activity) {
            if (--runningActivities == 0)
                listeners.forEachFast { it.onAppBackgrounded() }
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (--aliveActivities == 0)
                listeners.forEachFast { it.onAppClosed() }

            if (FRAGMENT_ACTIVITY_CLASS?.isInstance(activity) == true) {
                activity as FragmentActivity
                activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks)
            }
        }
    }

    private val fragmentLifecycleCallbacks by lazy { // Lazy because of dependency
        object : FragmentManager.FragmentLifecycleCallbacks() {

            private val fragmentsInTransaction = HashSet<Fragment>()

            override fun onFragmentStarted(manager: FragmentManager, fragment: Fragment) {
                FragmentTransactionObserver.observe(fragment, ::processEvent)
            }

            override fun onFragmentResumed(manager: FragmentManager, fragment: Fragment) {
                FragmentTransactionObserver.observe(fragment, ::processEvent)
            }

            override fun onFragmentStopped(fm: FragmentManager, fragment: Fragment) {
                FragmentTransactionObserver.observe(fragment, ::processEvent)
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, fragment: Fragment) {
                FragmentTransactionObserver.observe(fragment, ::processEvent)
            }

            private fun processEvent(event: FragmentTransactionObserver.Event, fragment: Fragment) {
                when (event) {
                    FragmentTransactionObserver.Event.START -> {
                        fragmentsInTransaction += fragment

                        if (fragmentsInTransaction.size == 1)
                            listeners.forEachFast { it.onFragmentTransactionStarted() }
                    }
                    FragmentTransactionObserver.Event.END -> {
                        fragmentsInTransaction -= fragment

                        if (fragmentsInTransaction.isEmpty())
                            listeners.forEachFast { it.onFragmentTransactionEnded() }
                    }
                }
            }
        }
    }

    interface Listener {
        fun onAppStarted() {}
        fun onAppClosed() {}

        fun onAppBackgrounded() {}
        fun onAppForegrounded() {}

        fun onFragmentTransactionStarted() {}
        fun onFragmentTransactionEnded() {}

        fun onViewTransitionStarted() {}
        fun onViewTransitionEnded() {}
    }
}
