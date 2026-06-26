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

import android.graphics.Color
import androidx.annotation.FloatRange
import com.splunk.rum.common.utils.extensions.a
import com.splunk.rum.common.utils.extensions.b
import com.splunk.rum.common.utils.extensions.g
import com.splunk.rum.common.utils.extensions.plusAssign
import com.splunk.rum.common.utils.extensions.r
import com.splunk.rum.common.utils.extensions.toArgbHexString

class Colors : Iterable<Int> {

    val colors: IntArray

    val width: Int
    val height: Int

    constructor(width: Int, height: Int, vararg colors: Int = IntArray(height * width)) {
        if (colors.size != height * width)
            throw IllegalArgumentException("Wrong colors size")

        this.width = width
        this.height = height
        this.colors = colors
    }

    constructor(color: Int) : this(1, 1, color)

    operator fun get(x: Int, y: Int): Int {
        val index = getIndex(x, y)
        return colors[index]
    }

    operator fun set(x: Int, y: Int, value: Int) {
        val index = getIndex(x, y)
        colors[index] = value
    }

    operator fun get(i: Int): Int {
        return colors[i]
    }

    operator fun set(i: Int, value: Int) {
        colors[i] = value
    }

    fun any(predicate: (Int) -> Boolean): Boolean {
        for (i in colors.indices)
            if (predicate(colors[i]))
                return true

        return false
    }

    fun all(predicate: (Int) -> Boolean): Boolean {
        for (i in colors.indices)
            if (!predicate(colors[i]))
                return false

        return true
    }

    fun isSingleColor(): Boolean {
        return colors.size == 1
    }

    fun isMultiColor(): Boolean {
        return colors.size > 1
    }

    fun isVisible(): Boolean {
        return any { Color.alpha(it) != 0x00 }
    }

    /**
     * Whether any color is clearly visible, means alpha channel is higher than 0x15.
     * Color with alpha below the constant will be probably misjudged and not be visible anyway.
     */
    fun isClearlyVisible(): Boolean {
        return any { Color.alpha(it) > 0x15 }
    }

    fun isOpaque(): Boolean {
        return all { Color.alpha(it) == 0xff }
    }

    operator fun times(color: Int): Colors {
        return copy { composeColor(it, color) { a, b -> a * b } }
    }

    operator fun times(@FloatRange(from = 0.0, to = 1.0) alpha: Float): Colors {
        return copy { Color.argb((it.a * alpha).toInt(), it.r, it.g, it.b) }
    }

    operator fun plus(color: Int): Colors {
        return copy { composeColor(it, color) { a, b -> (a + b).coerceAtMost(1f) } }
    }

    private fun copy(colorProcessor: (Int) -> Int): Colors {
        val result = Colors(width, height, *colors.copyOf())

        for (i in result.colors.indices)
            result.colors[i] = colorProcessor(result.colors[i])

        return result
    }

    private fun composeColor(colorA: Int, colorB: Int, processor: (Float, Float) -> Float): Int {
        val channel: (Int, Int) -> Int = { a, b -> (processor(a / 255f, b / 255f) * 255).toInt() }

        return Color.argb(channel(colorA.a, colorB.a), channel(colorA.r, colorB.r), channel(colorA.g, colorB.g), channel(colorA.b, colorB.b))
    }

    private fun getIndex(x: Int, y: Int): Int {
        return x + width * y
    }

    override fun iterator(): Iterator<Int> {
        return object : Iterator<Int> {
            private var index = 0

            override fun hasNext(): Boolean {
                return index < colors.size
            }

            override fun next(): Int {
                return colors[index++]
            }
        }
    }

    override fun toString(): String {
        val colorsString = StringBuilder()
        colorsString += '['

        for (y in 0 until height) {
            colorsString += '['

            for (x in 0 until width) {
                colorsString += get(x, y).toArgbHexString()

                if (x != width - 1)
                    colorsString += ", "
            }

            colorsString += ']'

            if (y != height - 1)
                colorsString += ", "
        }

        colorsString += ']'

        return "Colors(width: $width, height: $height, colors: $colorsString)"
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Colors && width == other.width && height == other.height && colors.contentEquals(other.colors)
    }

    override fun hashCode(): Int {
        var result = colors.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    companion object {
        val TRANSPARENT = Colors(Color.TRANSPARENT)
    }
}
