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

package com.splunk.android.common.utils.legacy

import android.view.View
import com.splunk.android.common.utils.extensions.findField
import com.splunk.android.common.utils.extensions.get
import com.splunk.android.common.utils.extensions.toClass
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import java.lang.reflect.Field

fun View.getViewIdentifier(): String {
    return getResourceName()
        ?: onClickMethodName()
        ?: tabViewIdentifier()
        ?: Constants.UNKNOWN_VIEW_IDENTIFIER
}

fun View.getResourceName(): String? {
    return if (id == View.NO_ID || id == 0 || id and 0xFF000000.toInt() == 0 && id and 0x00FFFFFF != 0) // View.isViewIdGenerated()
        null
    else
        runCatching { context.resources.getResourceEntryName(id) }.getOrNull()
}

private var mListenerInfoField: Field? = null
private var mOnClickListenerField: Field? = null
private var mMethodNameField: Field? = null

private fun View.onClickMethodName(): String? {
    if (!hasOnClickListeners()) {
        return null
    }

    return try {
        val mListenerInfoFieldLocal = mListenerInfoField ?: javaClass.findField("mListenerInfo").also { mListenerInfoField = it }
        val mListenerInfo = get<Any>(mListenerInfoFieldLocal) ?: return null

        val mOnClickListenerField = mOnClickListenerField ?: mListenerInfo.javaClass.findField("mOnClickListener").also { mOnClickListenerField = it }
        val mOnClickListener = mListenerInfo.get<Any>(mOnClickListenerField) ?: return null

        val mMethodNameField = mMethodNameField ?: mOnClickListener.javaClass.findField("mMethodName").also { mMethodNameField = it }
        mOnClickListener.get(mMethodNameField)
    } catch (_: NoSuchFieldException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val tabLayoutTabViewClass = "com.google.android.material.tabs.TabLayout\$TabView".toClass() // MARK Require Proguard rules

private fun View.tabViewIdentifier(): String? {
    if (this::class.java != tabLayoutTabViewClass)
        return null

    val tab = getTabForTabView()
    val tabLayout = getTabLayoutForTabView()

    return Constants.TAB_VIEW_IDENTIFIER_TEMPLATE.format(
        tabLayout?.getResourceName() ?: "TabLayout",
        tab?.position ?: "-",
        tab?.tag ?: "-"
    )
}

private var tabField: Field? = null

private fun View.getTabForTabView(): Tab? {
    return try {
        val tabField = tabField ?: javaClass.findField("tab").also { tabField = it }
        get(tabField)
    } catch (_: NoSuchFieldException) {
        null
    }
}

private fun View.getTabLayoutForTabView(): View? {
    val parent = this.parent as? View

    return if (parent != null && parent is TabLayout)
        parent
    else
        null
}
