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

import com.splunk.rum.common.utils.runOnUiThread
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

fun ExecutorService.safeSubmit(block: () -> Unit): Future<*> {
    val safeBlock = {
        try {
            block()
        } catch (e: Throwable) {
            val delegatedException = Exception("Exception catch in '${Thread.currentThread().name}' thread", e)
            runOnUiThread { throw delegatedException }
        }
    }

    return submit(safeBlock)
}

fun <T> ExecutorService.safeInvokeAll(tasks: Collection<Callable<T>>): List<Future<T>> {
    val safeTasks = tasks.map {
        Callable {
            try {
                it.call()
            } catch (e: Throwable) {
                val delegatedException = Exception("Exception catch in '${Thread.currentThread().name}' thread", e)
                runOnUiThread { throw delegatedException }

                throw e // This will be consumed by ExecutorService
            }
        }
    }

    return invokeAll(safeTasks)
}
