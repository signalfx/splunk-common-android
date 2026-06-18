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

import android.view.View
import androidx.fragment.app.Fragment
import com.splunk.android.common.logger.Logger
import com.splunk.android.common.utils.FragmentTransactionObserver.Event
import com.splunk.android.common.utils.extensions.getFragmentSpecialEffectsControllerViewTag
import com.splunk.android.common.utils.extensions.hasField
import com.splunk.android.common.utils.extensions.toClass
import com.splunk.android.common.utils.reflector.Reflector

// MARK Required Proguard rules

typealias FragmentTransactionListener = (Event, Fragment) -> Unit

object FragmentTransactionObserver {

    private const val TAG = "FragmentTransactionObserver"

    enum class Event {
        START, END
    }

    private var inPendingInit = true
    private var operationsResolver: OperationsResolver? = null

    fun observe(fragment: Fragment, callback: FragmentTransactionListener) {
        initIfNeeded()

        operationsResolver?.addTransactionsListener(fragment, callback)
    }

    private fun initIfNeeded() {
        if (!inPendingInit)
            return

        inPendingInit = false

        val defaultSpecialEffectsControllerClass = "androidx.fragment.app.SpecialEffectsController".toClass()

        if (defaultSpecialEffectsControllerClass == null) {
            Logger.e(TAG, "initIfNeeded() - missing class")
            return
        }

        when {
            defaultSpecialEffectsControllerClass.hasField("pendingOperations") ->
                operationsResolver = OperationsResolver16()
            defaultSpecialEffectsControllerClass.hasField("mPendingOperations") ->
                operationsResolver = OperationsResolver13()
            else ->
                Logger.e(TAG, "initIfNeeded() - missing field")
        }
    }

    private interface OperationsResolver {
        fun addTransactionsListener(fragment: Fragment, listener: FragmentTransactionListener)
    }

    private open class OperationsResolver13 : OperationsResolver { // 1.3.6, 1.4.1, 1.5.7

        private val reflector = Reflector(2, 0, 1)

        open val pendingOperationsFieldName = "mPendingOperations"
        open val pendingOperationsFragmentFieldName = "mFragment"

        override fun addTransactionsListener(fragment: Fragment, listener: FragmentTransactionListener) {
            val parent = fragment.view?.parent as? View ?: return
            val tagId = parent.context.getFragmentSpecialEffectsControllerViewTag() ?: return
            val controller = parent.getTag(tagId) ?: return

            try {
                reflector.reflect {
                    val pendingOperations = controller.get<ArrayList<Any>>(pendingOperationsFieldName) ?: return@reflect

                    for (operation in pendingOperations) {
                        val currentFragment = operation.get<Fragment>(pendingOperationsFragmentFieldName) ?: continue
                        val completionsListener = Runnable { listener(Event.END, currentFragment) }

                        listener(Event.START, currentFragment)
                        operation.invoke<Unit>("addCompletionListener", completionsListener to Runnable::class.java)
                    }
                }
            } catch (e: Exception) {
                Logger.e1(TAG, "observe", e)
            }
        }

        private companion object {
            const val TAG = "OperationsResolver13"
        }
    }

    private class OperationsResolver16 : OperationsResolver13() { // 1.6.2, 1.7.1, 1.8.9
        override val pendingOperationsFieldName = "pendingOperations"
        override val pendingOperationsFragmentFieldName = "fragment"
    }
}
