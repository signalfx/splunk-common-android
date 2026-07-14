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

package com.splunk.rum.common.utils.extensions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.splunk.rum.common.logger.Logger
import com.splunk.rum.common.utils.R
import com.splunk.rum.common.utils.runOnAndroidAtLeast

private const val TAG = "ViewExt"

val View.activity: Activity?
    get() {
        var context = context

        while (context != null) {
            if (context is Activity)
                return context

            if (context is ContextWrapper && context !is Application)
                context = context.baseContext
            else
                break
        }

        return if (this is ViewGroup) getChildAt(0)?.activity else null
    }

fun View.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val layoutParams = layoutParams as? WindowManager.LayoutParams

    if (layoutParams != null && layoutParams.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN > 0) {
        val location = IntArray(2)
        getLocationInWindow(location)
        canvas.translate(location[0].toFloat(), location[1].toFloat())
    }

    draw(canvas)
    return bitmap
}

var View.ciscoId: String?
    get() = getTag(R.id.sl_tag_id) as? String
    set(value) = setTag(R.id.sl_tag_id, value)

val View.ciscoIdWithPositionInList: String
    get() {
        val id = ciscoId?.let { "userid_$it" } ?: calcCiscoId()
        val position = getPositionInList()

        return if (position != null) "$id#$position" else id
    }

fun View.calcCiscoId(): String {
    if (id == 0 || id == View.NO_ID)
        return "hash_${hashCode()}"

    if (id and 0xFF000000.toInt() != 0) // Based on View.isViewIdGenerated(). Non generated ids have to pass 0xFF000000 mask.
        runCatching { return "name_${context.resources.getResourceEntryName(id)}" }

    return "id_0x${Integer.toHexString(id)}"
}

// MARK Require Proguard rules

private val recyclerViewClass = "androidx.recyclerview.widget.RecyclerView".toClass()
private val recyclerViewSupportClass = "android.support.v7.widget.RecyclerView".toClass()
private val viewPager2ItemClass = "androidx.viewpager2.widget.ViewPager2\$RecyclerViewImpl".toClass()
private val viewPagerClass = "androidx.viewpager.widget.ViewPager".toClass()
private val viewPagerSupport = "android.support.v4.view.ViewPager".toClass()

fun View.getPositionInList(): Int? {
    val parent = parent ?: return null

    when {
        recyclerViewClass?.isAssignableFrom(parent::class.java) == true -> {
            return (parent as RecyclerView).layoutManager?.getPosition(this)
        }
        viewPager2ItemClass?.isAssignableFrom(parent::class.java) == true -> {
            val layoutManager = try {
                parent.parent?.get<LinearLayoutManager>("mLayoutManager")
            } catch (e: NoSuchFieldException) {
                Logger.e1(TAG, "getPositionInList", e)
                null
            }

            if (layoutManager != null)
                return layoutManager.getPosition(this)
        }
        viewPagerClass?.isAssignableFrom(parent::class.java) == true || viewPagerSupport?.isAssignableFrom(parent::class.java) == true -> {
            val items = try {
                parent.get<ArrayList<Any>>("mItems")
            } catch (e: NoSuchFieldException) {
                Logger.e1(TAG, "getPositionInList", e)
                null
            }

            if (items != null)
                for (item in items) {
                    val obj = try {
                        item.get<Any>("object")
                    } catch (e: NoSuchFieldException) {
                        Logger.e1(TAG, "getPositionInList", e)
                        null
                    }

                    if (obj is Fragment && obj.view === this || obj === this)
                        return try {
                            item.get("position")
                        } catch (e: NoSuchFieldException) {
                            Logger.e1(TAG, "getPositionInList", e)
                            null
                        }
                }
        }
        parent is AbsListView -> {
            val firstPosition = runCatching { parent.get<Int>("mFirstPosition") }.getOrNull() ?: return null

            for (i in 0 until parent.childCount)
                if (parent.getChildAt(i) === this)
                    return firstPosition + i
        }
        recyclerViewSupportClass?.isAssignableFrom(parent::class.java) == true -> {
            try {
                val layoutManager = parent.get<Any>("mLayout")
                return layoutManager?.invoke("getPosition", this to View::class.java)
            } catch (e: NoSuchFieldException) {
                Logger.e1(TAG, "getPositionInList", e)
            }
        }
    }

    return null
}

private var fragmentContainerViewTag: Int? = null

fun Context.getFragmentContainerViewTag(): Int? {
    if (fragmentContainerViewTag == null) {
        val name = "$packageName:id/fragment_container_view_tag"

        try {
            val value = TypedValue()
            resources.getValue(name, value, false)
            fragmentContainerViewTag = value.resourceId
        } catch (_: Resources.NotFoundException) {
        }
    }

    return fragmentContainerViewTag
}

inline fun View.doOnLayout(crossinline action: (view: View) -> Unit) {
    val isLaidOut = runOnAndroidAtLeast(Build.VERSION_CODES.KITKAT) { isLaidOut } ?: (width > 0 && height > 0)
    if (isLaidOut && !isLayoutRequested)
        action(this)
    else
        doOnNextLayout { action(it) }
}

inline fun View.doOnNextLayout(crossinline action: (view: View) -> Unit) {
    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            view.removeOnLayoutChangeListener(this)
            action(view)
        }
    })
}
