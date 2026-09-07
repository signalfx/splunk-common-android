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

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import com.splunk.rum.common.logger.Logger
import com.splunk.rum.common.utils.extensions.DifferencesConsumer
import com.splunk.rum.common.utils.extensions.emitDifferences
import com.splunk.rum.common.utils.extensions.findField
import com.splunk.rum.common.utils.extensions.forEachFast
import com.splunk.rum.common.utils.extensions.get
import com.splunk.rum.common.utils.extensions.set
import com.splunk.rum.common.utils.extensions.toClass
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArrayList

object RootViewObserver {

    private const val TAG = "RootViewObserver"

    private val choreographer = Choreographer.getInstance()

    private var windowManager: Any? = null
    private var viewsField: Field? = null
    private var rootsField: Field? = null

    private var rootViewsObserver: ArrayListObserver<View?>? = null

    val listeners: MutableList<Listener> = CopyOnWriteArrayList()

    val views: List<View>
        get() = rootViewsObserver?.filterNotNull() ?: emptyList()

    @SuppressLint("PrivateApi")
    fun attach(application: Application) {
        if (windowManager != null)
            return

        try {
            val windowManagerClass = "android.view.WindowManagerGlobal".toClass() ?: return
            val getInstanceMethod = windowManagerClass.getMethod("getInstance")
            val windowManager = getInstanceMethod.invoke(null) ?: return

            viewsField = windowManager.javaClass.findField("mViews")
            rootsField = windowManager.javaClass.findField("mRoots")

            this.windowManager = windowManager
        } catch (e: Exception) {
            Logger.e1(TAG, "attach", e)
            return
        }

        AppStateObserver.listeners += appStateObserverListener
        AppStateObserver.attach(application)

        injectObserverIfNeeded()
    }

    /**
     * Root View can not be removed from WindowManager when is doing draw, measure or layout. For example when a user click on button that opens Dialog or Activity.
     * In this case, it will be removed in the next frame. Due to a bug in the WindowManagerGlobal class (index collision), any other root View can not be removed until next frame.
     *
     * Call the function to check if is safe to remove root View from WindowManager. When false, try it in the next frame.
     */
    fun isSafeToRemoveRootView(): Boolean {
        val windowManager = windowManager
        val viewsField = viewsField
        val rootsField = rootsField

        if (windowManager == null || viewsField == null || rootsField == null)
            throw IllegalStateException("Call attach() first")

        val views = windowManager.get<List<Any>>(viewsField)
        val roots = windowManager.get<List<Any>>(rootsField)

        if (views == null || roots == null)
            throw IllegalStateException("Cast to List failed")

        // mViews item is removed immediately, mRoots item is removed in the next frame
        return views.size == roots.size
    }

    private fun injectObserverIfNeeded() {
        val windowManager = windowManager ?: return
        val viewsField = viewsField ?: return

        val rootViews = windowManager.get<ArrayList<View?>>(viewsField) ?: return

        if (rootViews === rootViewsObserver)
            return

        var rootViewsObserver = rootViewsObserver

        if (rootViewsObserver == null) {
            rootViewsObserver = ArrayListObserver(rootViews, observer)
            this.rootViewsObserver = rootViewsObserver
        } else {
            rootViewsObserver.emitDifferences(rootViews, differencesConsumer)
            rootViewsObserver.wrappedList = rootViews
        }

        windowManager.set(viewsField, rootViewsObserver)
    }

    private val differencesConsumer = object : DifferencesConsumer<View?> {
        override fun onNew(element: View?) {
            if (element != null)
                listeners.forEachFast { it.onAdded(element) }
        }

        override fun onMiss(element: View?) {
            if (element != null)
                listeners.forEachFast { it.onRemoved(element) }
        }
    }

    private val appStateObserverListener = object : AppStateObserver.Listener {
        override fun onAppBackgrounded() {
            choreographer.removeFrameCallback(frameCallback)
        }

        override fun onAppForegrounded() {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            injectObserverIfNeeded()
        }
    }

    private val observer = object : ArrayListObserver.Observer<View?> {

        private val handler = Handler(Looper.getMainLooper())

        override fun onAdded(element: View?) {
            if (element != null)
                emitOnMainThread(element, Listener::onAdded)
        }

        override fun onRemoved(element: View?) {
            if (element != null)
                emitOnMainThread(element, Listener::onRemoved)
        }

        private fun emitOnMainThread(view: View, consumer: Listener.(View) -> Unit) {
            if (Looper.getMainLooper() != Looper.myLooper())
                handler.post { emitOnMainThread(view, consumer) }
            else
                listeners.forEachFast { consumer(it, view) }
        }
    }

    interface Listener {
        fun onAdded(view: View) {}
        fun onRemoved(view: View) {}
    }
}
