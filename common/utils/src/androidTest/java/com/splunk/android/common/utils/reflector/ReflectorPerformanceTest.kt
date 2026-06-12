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

package com.splunk.android.common.utils.reflector

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class ReflectorPerformanceTest {

    @Test
    fun testAllReflectionEdgeCasesAndBenchmark() {
        val reflector = Reflector()
        val system = AdvancedSystem()

        reflector.reflect {
            system.set("systemId", 999L)
            system.set("coreTemperature", 42.0)

            val systemId = system.get<Long>("systemId")
            val coreTemperature = system.get<Double>("coreTemperature")!!
            val systemName = system.get<String>("systemName")

            assertEquals("Failed to read/write superclass field", 999L, systemId)
            assertEquals("Failed to read/write subclass field", 42.0, coreTemperature, 0.0)
            assertEquals("Failed to read String field", "QuantumCore", systemName)

            val activationResult = system.invoke<Long>("activateSystem", true to Boolean::class.java)
            assertEquals("Failed to invoke superclass method", 999L, activationResult)

            val temp = system.invoke<Double>("getTemperature")
            assertEquals("Failed to invoke no-arg method", 42.0, temp!!, 0.0)

            val calc1 = system.invoke<Int>("compute", 5 to Int::class.java)
            assertEquals("Overload 1 (single int) failed", 10, calc1)

            val calc2 = system.invoke<Int>("compute", 5 to Int::class.java, true to Boolean::class.java)
            assertEquals("Overload 2 (int + boolean) failed", 50, calc2)

            val calc3 = system.invoke<String>("compute", "Turbo" to String::class.java)
            assertEquals("Overload 3 (String) failed", "Config: Turbo", calc3)

            val calcNull = system.invoke<String>("compute", null to String::class.java)
            assertEquals("Passing NULL argument failed", "DefaultConfig", calcNull)
        }

        val warmUpIterations = 10_000
        for (i in 0 until warmUpIterations)
            reflector.reflect {
                system.get<Double>("coreTemperature")
                system.invoke<Int>("compute", 2 to Int::class.java)
            }

        val testIterations = 10_000
        var totalTimeNs = 0L

        for (i in 0 until testIterations) {
            val time = measureNanoTime {
                reflector.reflect {
                    val currentTemp = system.get<Double>("coreTemperature")

                    if (currentTemp != null && currentTemp > 40.0)
                        system.invoke<Int>("compute", 10 to Int::class.java, false to Boolean::class.java)
                    else
                        system.invoke<Int>("compute", 10 to Int::class.java)

                    system.set("coreTemperature", if (i % 2 == 0) 50.0 else 30.0)
                    system.get<String>("systemName")
                }
            }

            totalTimeNs += time
        }

        Log.d(TAG, "Total time ${totalTimeNs / 1_000_000f} ms")
    }

    private companion object {
        private const val TAG = "ReflectorTest"
    }
}

@Suppress("unused")
private open class BaseSystem {
    private var systemId: Long = 101010L
    private var isActive: Boolean = false

    private fun activateSystem(force: Boolean): Long {
        isActive = force
        return if (force) systemId else 0L
    }
}

@Suppress("unused")
private class AdvancedSystem : BaseSystem() {
    private var coreTemperature: Double = 36.5
    private val systemName: String = "QuantumCore"

    private fun compute(factor: Int): Int {
        return factor * 2
    }

    private fun compute(factor: Int, useBoost: Boolean): Int {
        return if (useBoost) factor * 10 else factor * 2
    }

    private fun compute(configName: String?): String {
        return if (configName == null) "DefaultConfig" else "Config: $configName"
    }

    private fun getTemperature(): Double {
        return coreTemperature
    }
}
