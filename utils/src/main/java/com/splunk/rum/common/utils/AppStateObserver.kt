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
import androidx.annotation.MainThread
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

    private val choreographer = SafeChoreographer()

    @Volatile
    private var application: Application? = null

    @Volatile
    private var runningActivities = 0
    private val startedActivities = WeakActivitySet()
    private val aliveActivities = WeakActivitySet()

    val listeners: MutableList<Listener> = mutableListOf()

    /**
     * Whether an Activity observed by this instance has started. Reading this does not notify listeners.
     * If the SDK's lifecycle provider was removed and this observer attaches after the Activity
     * started, the value remains false until the next observed start.
     */
    val hasStartedActivity: Boolean
        get() = application != null && runningActivities > 0

    @MainThread
    fun attach(application: Application) {
        if (this.application != null)
            return

        application.registerActivityLifecycleCallbacks(activityLifecycleCallback)
        choreographer.postFrameCallback(frameCallback)

        this.application = application
    }

    @MainThread
    fun detach() {
        application?.unregisterActivityLifecycleCallbacks(activityLifecycleCallback)
        choreographer.removeFrameCallback(frameCallback)

        application = null
        startedActivities.clear()
        aliveActivities.clear()
        runningActivities = 0
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
            val isNewActivity = aliveActivities.add(activity)
            if (isNewActivity && aliveActivities.size == 1)
                listeners.forEachFast { it.onAppStarted() }

            if (isNewActivity && FRAGMENT_ACTIVITY_CLASS?.isInstance(activity) == true) {
                activity as FragmentActivity
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true)
            }
        }

        override fun onActivityStarted(activity: Activity) {
            if (!startedActivities.add(activity)) return
            runningActivities = startedActivities.size
            if (runningActivities == 1)
                listeners.forEachFast { it.onAppForegrounded() }
        }

        override fun onActivityStopped(activity: Activity) {
            if (!startedActivities.remove(activity)) return
            runningActivities = startedActivities.size
            if (runningActivities == 0)
                listeners.forEachFast { it.onAppBackgrounded() }
        }

        override fun onActivityDestroyed(activity: Activity) {
            val wasObserved = aliveActivities.remove(activity)
            if (wasObserved && aliveActivities.isEmpty())
                listeners.forEachFast { it.onAppClosed() }

            if (wasObserved && FRAGMENT_ACTIVITY_CLASS?.isInstance(activity) == true) {
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

    /** Tracks Activity instances without keeping them alive or using their equals/hashCode. */
    private class WeakActivitySet {
        private val activities = mutableListOf<WeakReference<Activity>>()

        val size: Int
            get() {
                activities.removeAll { it.get() == null }
                return activities.size
            }

        fun isEmpty(): Boolean = size == 0

        fun clear() = activities.clear()

        fun add(activity: Activity): Boolean {
            activities.removeAll { it.get() == null }
            if (activities.any { it.get() === activity }) return false
            activities.add(WeakReference(activity))
            return true
        }

        fun remove(activity: Activity): Boolean {
            var removed = false
            activities.removeAll {
                val existing = it.get()
                if (existing === activity) removed = true
                existing == null || existing === activity
            }
            return removed
        }
    }
}
