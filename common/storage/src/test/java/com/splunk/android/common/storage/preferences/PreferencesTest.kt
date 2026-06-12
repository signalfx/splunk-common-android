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

package com.splunk.android.common.storage.preferences

import org.junit.Assert
import org.junit.Test
import kotlin.text.contains

internal class PreferencesTest {

    private var testCache = TestSimplePermanentCache()
    private var preferences = Preferences(testCache)

    @Test
    fun `check values existence in a map`() {
        println("Test started: check value existence")

        whenWrittenToPreferences()
        thenValuesExistsInPreferences(true)
        whenRemovedFromPreferences()
        thenValuesExistsInPreferences(false)
    }

    @Test
    fun `check values existence in a file`() {
        println("Test started: check value existence in a file")

        whenWrittenToPreferences()
        thenExistsInFile(true)
        whenRemovedFromPreferences()
        thenExistsInFile(false)
    }

    @Test
    fun `check number of values`() {
        println("Test started: check number of values")

        val key = "Int key"

        whenIntWrittenToPreferences(key)
        thenNumberOfValuesIsCorrect(1)
        whenIntDeletedFromPreferences("differentKey")
        thenNumberOfValuesIsCorrect(1)
        whenIntDeletedFromPreferences(key)
        thenNumberOfValuesIsCorrect(0)
    }

    @Test
    fun `read different data type`() {
        println("Test started: read different data type")

        whenWrittenToPreferences()
        thenDifferentDataTypeGivesException()
    }

    @Test
    fun `contains a value`() {
        println("Test started: contains a value")

        whenWrittenToPreferences()
        thenContainsValue()
    }

    @Test
    fun `clear all the values in preferences`() {
        println("Test started: clear all the values in preferences")

        whenWrittenToPreferences()
        whenClearPreferences()
        thenIsEmpty()
    }

    @Test
    fun `JSON saving interruption should not throw error later`() {
        println("Test started: JSON saving interruption should not throw error later")

        testCache.writeBytes("{\"key for String\":{\"type\":\"String\",\"value\":\"String value\"},\"key for Float\":".toByteArray())
        val map = hashMapOf<String, Value>()

        try {
            val json = testCache.readBytes().toString(Charsets.UTF_8)
            deserializeToMap(json, map)
            Assert.assertTrue(false)
        } catch (_: Exception) {
            testCache.writeBytes(ByteArray(0))
        }

        Assert.assertTrue(testCache.readBytes().isEmpty())
    }

    private fun whenWrittenToPreferences() {
        preferences.putString("key for String", "String value")
        preferences.putString("key for String to be deleted", "String value to be deleted")
        preferences.putInt("key for Int", 10)
        preferences.putInt("key for Int to be deleted", 10)
        preferences.putLong("key for Long", 20L)
        preferences.putLong("key for Long to be deleted", 20L)
        preferences.putFloat("key for Float", 30.5f)
        preferences.putFloat("key for Float to be deleted", 30.5f)
        preferences.putBoolean("key for Boolean", true)
        preferences.putBoolean("key for Boolean to be deleted", true)
        preferences.commit()
    }

    private fun whenRemovedFromPreferences() {
        preferences.remove("key for String to be deleted")
        preferences.remove("key for Int to be deleted")
        preferences.remove("key for Long to be deleted")
        preferences.remove("key for Float to be deleted")
        preferences.remove("key for Boolean to be deleted")
        preferences.commit()
    }

    private fun whenIntWrittenToPreferences(key: String) {
        preferences.putInt(key, 10).commit()
    }

    private fun whenIntDeletedFromPreferences(key: String) {
        preferences.remove(key).commit()
    }

    private fun whenClearPreferences() {
        preferences.clear()
        preferences.commit()
    }

    private fun thenValuesExistsInPreferences(exists: Boolean) {
        val stringValue = preferences.getString("key for String to be deleted")
        val intValue = preferences.getInt("key for Int to be deleted")
        val longValue = preferences.getLong("key for Long to be deleted")
        val floatValue = preferences.getFloat("key for Float to be deleted")
        val booleanValue = preferences.getBoolean("key for Boolean to be deleted")

        Assert.assertTrue((stringValue != null) == exists)
        Assert.assertTrue((intValue != null) == exists)
        Assert.assertTrue((longValue != null) == exists)
        Assert.assertTrue((floatValue != null) == exists)
        Assert.assertTrue((booleanValue != null) == exists)
    }

    private fun thenNumberOfValuesIsCorrect(size: Int) {
        Assert.assertTrue(preferences.size() == size)
    }

    private fun thenDifferentDataTypeGivesException() {
        val intValue = runCatching {
            preferences.getInt("key for String to be deleted")
        }.getOrNull()

        Assert.assertTrue(intValue == null)
    }

    private fun thenExistsInFile(exists: Boolean) {
        val json = testCache.readBytes().toString(Charsets.UTF_8)
        Assert.assertTrue("String value to be deleted" in json == exists)
    }

    private fun thenContainsValue() {
        val contains = preferences.contains("key for String to be deleted")
        Assert.assertTrue(contains)
    }

    private fun thenIsEmpty() {
        Assert.assertTrue(testCache.readBytes().toString(Charsets.UTF_8) == "{}")
    }
}
