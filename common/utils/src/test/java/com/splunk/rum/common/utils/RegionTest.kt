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

import android.graphics.Rect
import com.splunk.rum.common.utils.extensions.contains
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class RegionTest {

    @Test
    fun addArea() {
        val region = Region()

        region.addArea(Rect(2, 3, 2, 5))
        assert(!region.hasArea()) { "Added empty rectangle" }

        region.addArea(Rect(0, 0, 1, 1))
        assert(region.getResult().size == 1) { "Missing added rectangle" }
        assert(region.getResult().first() == Rect(0, 0, 1, 1)) { "Wrong rectangle" }
    }

    @Test
    fun reset() {
        val region = Region()

        region.addArea(Rect(0, 0, 1, 1))
        assert(region.getResult().size == 1) { "Wrong rectangle count" }

        region.reset()
        assert(region.getResult().isEmpty()) { "Region is not empty" }
    }

    @Test
    fun clip() {
        val region = Region()

        region.addArea(Rect(1, 2, 4, 3))
        region.clip(Rect(2, 1, 2, 3))
        assert(region.getResult().size == 1) { "Wrong rectangle count" }
        assert(region.getResult().first() == Rect(2, 2, 2, 3)) { "Wrong rectangle" }
    }

    @Test
    fun clipOut() {
        val region = Region()

        // bigger
        // *————*
        // |    |
        // |    |
        // *————*
        // TODO

        // same
        // *———*
        // |   |
        // *———*
        // TODO

        // *—————*
        // |*———*|
        // ||   ||
        // |*———*|
        // *—————*
        // TODO

        // *———*
        // |   |*
        // *———*|
        //  *———*
        region.addArea(Rect(1, 1, 4, 4))
        region.clipOut(Rect(0, 0, 2, 2))
        assert(region.getResult().size == 2) { "Wrong rectangle count" }
        assert(listOf(Rect(2, 1, 4, 2), Rect(1, 2, 4, 4)) in region.getResult()) { "Wrong rectangle" }

        // *———**
        // |   ||
        // *———*|
        // *————*
        // TODO

        //  *———*
        // *|   |
        // |*———*
        // *———*
        region.reset()
        region.addArea(Rect(1, 1, 4, 4))
        region.clipOut(Rect(2, 0, 6, 2))
        assert(region.getResult().size == 2) { "Wrong rectangle count" }
        assert(listOf(Rect(1, 1, 2, 4), Rect(2, 2, 4, 4)) in region.getResult()) { "Wrong rectangle" }

        // **———*
        // ||   |
        // |*———*
        // *————*
        // TODO

        // *———*
        // |*———*
        // *|   |
        //  *———*
        // TODO

        // *————*
        // |*———*
        // ||   |
        // **———*
        // TODO

        //  *———*
        // *———*|
        // |   |*
        // *———*
        // TODO

        // *————*
        // *———*|
        // |   ||
        // *———**
        // TODO

        //  *———*
        // *|   |*
        // |*———*|
        // *—————*
        // TODO

        // **———**
        // ||   ||
        // |*———*|
        // *—————*
        // TODO

        // *———*
        // |*———*
        // ||   |
        // |*———*
        // *———*
        // TODO

        // *———*
        // |*——*
        // ||  |
        // |*——*
        // *———*
        // TODO

        // *—————*
        // |*———*|
        // *|   |*
        //  *———*
        // TODO

        // *—————*
        // |*———*|
        // ||   ||
        // **———**
        // TODO

        //  *———*
        // *———*|
        // |   ||
        // *———*|
        //  *———*
        // TODO

        // *———*
        // *——*|
        // |  ||
        // *——*|
        // *———*
        // TODO

        // *————*
        // |    |
        // *————*
        //  *——*
        // TODO

        // *————*
        // |    |
        // *————*
        // *————*
        // TODO

        //  *———*
        // *|   |
        // ||   |
        // *|   |
        //  *———*
        // TODO

        // **———*
        // ||   |
        // **———*
        // TODO

        //  *———*
        // *—————*
        // |     |
        // *—————*
        // TODO

        // *———*
        // *———*
        // |   |
        // *———*
        // TODO

        // *———*
        // |   |*
        // |   ||
        // |   |*
        // *———*
        // TODO

        // *———**
        // |   ||
        // *———**
        // TODO

        //  *——*
        // *————*
        // |    |
        // *————*
        //  *——*
        // TODO

        // *——*
        // *——*
        // |  |
        // *——*
        // *——*
        // TODO

        //  *——*
        // *|  |*
        // ||  ||
        // *|  |*
        //  *——*
        // TODO

        // **——**
        // ||  ||
        // **——**
        // TODO
    }
}
