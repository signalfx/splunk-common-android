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

package com.splunk.rum.common.utils.reflector

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflectorTest {

    private val reflector = Reflector(0, 0, 0)

    @Test
    fun `get field value`() {
        val testClass = TestClass("test")

        reflector.reflect {
            assertEquals("test", testClass.get<String>("value"))
            assertEquals("test", testClass.get<String>("value"))
        }

        reflector.reflect {
            assertEquals("test", testClass.get<String>("value"))
        }
    }

    @Test
    fun `set field value`() {
        val testClass = TestClass("test")

        reflector.reflect {
            testClass.set("value", "test2")
            assertEquals("test2", testClass.get("value"))
        }

        reflector.reflect {
            testClass.set("value", "test3")
        }

        reflector.reflect {
            assertEquals("test3", testClass.get("value"))
        }
    }

    @Test
    fun `invoke method`() {
        val testClass = TestClass("test")

        reflector.reflect {
            assertEquals("test", testClass.invoke<String>("getContent"))
            assertEquals("test", testClass.invoke<String>("getContent"))
        }
    }

    @Test
    fun `invoke method with params`() {
        val testClass = TestClass("test")

        reflector.reflect {
            assertEquals("param1", testClass.invoke("getParam", "param1" to String::class.java))
            assertEquals("param2", testClass.invoke("getParam", "param2" to String::class.java))
        }
    }

    @Suppress("unused")
    private class TestClass(val value: String) {

        fun getContent(): String {
            return value
        }

        fun getParam(param: String): String {
            return param
        }
    }
}
