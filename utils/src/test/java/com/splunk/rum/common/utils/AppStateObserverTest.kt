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
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class AppStateObserverTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    @After
    fun detachObserver() {
        AppStateObserver.detach()
    }

    @Test
    fun `background-only attachment has no started Activity`() {
        AppStateObserver.attach(application)

        val service = Robolectric.buildService(HeadlessService::class.java).create()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = Unit
        }
        receiver.onReceive(application, Intent("test.headless"))

        assertFalse(AppStateObserver.hasStartedActivity)
        service.destroy()
    }

    @Test
    fun `Activity must start before the query returns true`() {
        AppStateObserver.attach(application)
        val activity = Robolectric.buildActivity(Activity::class.java).create()

        assertFalse(AppStateObserver.hasStartedActivity)

        activity.start().resume()
        assertTrue(AppStateObserver.hasStartedActivity)

        activity.pause().stop()
        assertFalse(AppStateObserver.hasStartedActivity)
        activity.destroy()
    }

    @Test
    fun `one remaining started Activity keeps the query true`() {
        AppStateObserver.attach(application)
        val first = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        val second = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        first.pause().stop().destroy()
        assertTrue(AppStateObserver.hasStartedActivity)

        second.pause().stop().destroy()
        assertFalse(AppStateObserver.hasStartedActivity)
    }

    @Test
    fun `query returns false while detached`() {
        AppStateObserver.attach(application)
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        AppStateObserver.detach()
        assertFalse(AppStateObserver.hasStartedActivity)

        AppStateObserver.attach(application)
        assertFalse(AppStateObserver.hasStartedActivity)
        activity.pause().stop().destroy()
    }

    @Test
    fun `late attach ignores an unobserved stop until the next start`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        AppStateObserver.attach(application)

        assertFalse(AppStateObserver.hasStartedActivity)
        activity.pause().stop()
        assertFalse(AppStateObserver.hasStartedActivity)

        activity.start().resume()
        assertTrue(AppStateObserver.hasStartedActivity)
        activity.pause().stop().destroy()
        assertFalse(AppStateObserver.hasStartedActivity)
    }

    @Test
    fun `reading current state does not replay foreground callbacks`() {
        var foregroundCalls = 0
        val listener = object : AppStateObserver.Listener {
            override fun onAppForegrounded() {
                foregroundCalls++
            }
        }
        AppStateObserver.listeners.add(listener)
        try {
            AppStateObserver.attach(application)
            val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume()

            assertTrue(AppStateObserver.hasStartedActivity)
            assertTrue(AppStateObserver.hasStartedActivity)
            assertEquals(1, foregroundCalls)

            activity.pause().stop().destroy()
        } finally {
            AppStateObserver.listeners.remove(listener)
        }
    }

    class HeadlessService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }
}
