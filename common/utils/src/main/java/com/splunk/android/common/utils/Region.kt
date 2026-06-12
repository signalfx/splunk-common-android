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

package com.splunk.android.common.utils

import android.graphics.Rect

class Region {

    private val newRectangles = ArrayList<Rect>(4)
    private val rectangles = ArrayList<Rect>()

    fun addArea(rect: Rect) {
        if (!rect.isEmpty)
            rectangles += Rect(rect)
    }

    fun hasArea(): Boolean {
        return rectangles.size > 0
    }

    fun reset() {
        rectangles.clear()
    }

    /**
     * Returns list of temporary rectangles with all [clip] and [clipOut] operations. Valid only until the next operation is performed.
     */
    fun getResult(): List<Rect> {
        return rectangles
    }

    fun clip(rect: Rect) {
        if (rectangles.isEmpty())
            return

        var i = 0

        do {
            if (!rectangles[i].intersect(rect)) {
                rectangles.removeAt(i)
                continue
            }

            i++
        } while (i < rectangles.size)
    }

    fun clipOut(rect: Rect) { // Clip out rectangle in "documentation images" is always on top
        if (rectangles.isEmpty())
            return

        var i = 0

        do {
            val base = rectangles[i]

            if (rect.left > base.right || rect.top > base.bottom || rect.right < base.left || rect.bottom < base.top) {
                i++
                continue
            }

            // *———*
            // |   |
            // *———*
            if (rect.left <= base.left && rect.top <= base.top && rect.right >= base.right && rect.bottom >= base.bottom) {
                rectangles.removeAt(i)
                continue
            }

            // *—————*
            // |*———*|
            // ||   ||
            // |*———*|
            // *—————*
            if (rect.left > base.left && rect.top > base.top && rect.right < base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(base.left, rect.top, rect.left, rect.bottom)
                newRectangles += Rect(rect.right, rect.top, base.right, rect.bottom)
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            // *———*
            // |   |*
            // *———*|
            //  *———*
            if (rect.left <= base.left && rect.top <= base.top && rect.right < base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(rect.right, base.top, base.right, rect.bottom)
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *|   |
            // |*———*
            // *———*
            if (rect.left > base.left && rect.top <= base.top && rect.right >= base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, rect.left, base.bottom)
                newRectangles += Rect(rect.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            // *———*
            // |*———*
            // *|   |
            //  *———*
            if (rect.left > base.left && rect.top > base.top && rect.right >= base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(base.left, rect.top, rect.left, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *———*|
            // |   |*
            // *———*
            if (rect.left <= base.left && rect.top > base.top && rect.right < base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(rect.right, rect.top, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *|   |*
            // |*———*|
            // *—————*
            if (rect.left > base.left && rect.top <= base.top && rect.right < base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, rect.left, base.bottom)
                newRectangles += Rect(rect.left, rect.bottom, rect.right, base.bottom)
                newRectangles += Rect(rect.right, base.top, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            // *———*
            // |*———*
            // ||   |
            // |*———*
            // *———*
            if (rect.left >= base.left && rect.top > base.top && rect.right >= base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(base.left, rect.top, rect.left, rect.bottom)
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            // *—————*
            // |*———*|
            // *|   |*
            //  *———*
            if (rect.left > base.left && rect.top > base.top && rect.right < base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, rect.left, base.bottom)
                newRectangles += Rect(rect.left, base.top, rect.right, rect.top)
                newRectangles += Rect(rect.right, base.top, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *———*|
            // |   ||
            // *———*|
            //  *———*
            if (rect.left <= base.left && rect.top > base.top && rect.right < base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(rect.right, rect.top, base.right, rect.bottom)
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            // *————*
            // |    |
            // *————*
            //  *——*
            if (rect.left <= base.left && rect.top <= base.top && rect.right >= base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *|   |
            // ||   |
            // *|   |
            //  *———*
            if (rect.left > base.left && rect.top <= base.top && rect.right >= base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, rect.left, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *———*
            // *—————*
            // |     |
            // *—————*
            if (rect.left <= base.left && rect.top > base.top && rect.right >= base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                rectangles.removeAt(i)
                continue
            }

            // *———*
            // |   |*
            // |   ||
            // |   |*
            // *———*
            if (rect.left <= base.left && rect.top <= base.top && rect.right < base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(rect.right, base.top, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *——*
            // *————*
            // |    |
            // *————*
            //  *——*
            if (rect.left <= base.left && rect.top > base.top && rect.right >= base.right && rect.bottom < base.bottom) {
                newRectangles += Rect(base.left, base.top, base.right, rect.top)
                newRectangles += Rect(base.left, rect.bottom, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            //  *——*
            // *|  |*
            // ||  ||
            // *|  |*
            //  *——*
            if (rect.left > base.left && rect.top <= base.top && rect.right < base.right && rect.bottom >= base.bottom) {
                newRectangles += Rect(base.left, base.top, rect.left, base.bottom)
                newRectangles += Rect(rect.right, base.top, base.right, base.bottom)
                rectangles.removeAt(i)
                continue
            }

            i++
        } while (i < rectangles.size)

        if (newRectangles.isNotEmpty()) {
            rectangles += newRectangles
            newRectangles.clear()
        }
    }

    override fun toString(): String {
        val result = if (hasArea())
            rectangles.joinToString(", ", "[", "]") { it.toString() }
        else
            "empty"

        return "Region(result: $result)"
    }
}
