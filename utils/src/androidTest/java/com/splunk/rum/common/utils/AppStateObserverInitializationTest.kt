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

import android.app.Application
import android.view.Choreographer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces a crash, where obtaining a [Choreographer] fails while the agent is being initialized from a
 * ContentProvider and takes the whole host app down with ExceptionInInitializerError.
 *
 * A device can not be forced into the reported native failure (the display event receiver fails to initialize when the
 * process runs out of file descriptors or can not reach SurfaceFlinger), so the tests trigger the very same call on a
 * thread without a Looper, where [Choreographer.getInstance] throws deterministically.
 *
 * Every object is initialized in its own class loader, otherwise another test could have initialized it already and
 * the initialization under test would never run.
 */
@RunWith(AndroidJUnit4::class)
class AppStateObserverInitializationTest {

    @After
    fun tearDown() {
        runCatching { AppStateObserver.detach() }
    }

    @Test
    fun appStateObserverInitializesWithoutChoreographer() {
        assertNull(initializeWithoutLooper("com.splunk.rum.common.utils.AppStateObserver"))
    }

    @Test
    fun rootViewObserverInitializesWithoutChoreographer() {
        assertNull(initializeWithoutLooper("com.splunk.rum.common.utils.RootViewObserver"))
    }

    @Test
    fun windowCallbackManagerInitializesWithoutChoreographer() {
        assertNull(initializeWithoutLooper("com.splunk.rum.common.utils.window.WindowCallbackManager"))
    }

    @Test
    fun attachSurvivesWithoutChoreographer() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        assertNull(runWithoutLooper { AppStateObserver.attach(application) })
    }

    @Test
    fun detachSurvivesWithoutChoreographer() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        assertNull(runWithoutLooper { AppStateObserver.attach(application) })
        assertNull(runWithoutLooper { AppStateObserver.detach() })
    }

    private fun initializeWithoutLooper(className: String): Throwable? = runWithoutLooper {
        val apkPath = InstrumentationRegistry.getInstrumentation().context.packageCodePath
        val classLoader = PathClassLoader(apkPath, ClassLoader.getSystemClassLoader())

        Class.forName(className, true, classLoader)
    }

    private fun runWithoutLooper(action: () -> Unit): Throwable? {
        var failure: Throwable? = null

        val thread = Thread {
            try {
                action()
            } catch (e: Throwable) {
                failure = e
            }
        }

        thread.start()
        thread.join()

        return failure
    }
}
