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

package com.splunk.android.common.logger

import com.splunk.android.common.logger.Logger.clearLogs
import com.splunk.android.common.logger.Logger.consumers
import com.splunk.android.common.logger.Logger.logLevel
import com.splunk.android.common.logger.Logger.logs
import com.splunk.android.common.logger.utils.RollingList
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

/**
 * Basic logging utility. Sent log messages are consumed by [consumers] and saved into [logs] buffer.
 */
object Logger {

    private const val INITIAL_LOGS_CAPACITY = 1000
    private val CLASS_NAME = this::class.java.name

    private val logsInternal = RollingList<Log>(INITIAL_LOGS_CAPACITY)
    private val callerSet = HashSet<String>()

    /**
     * Set of [Consumer].
     */
    val consumers: MutableList<Consumer> = CopyOnWriteArrayList()

    /**
     * Buffer of reported logs. Initial capacity is 1000. Can be disabled by set capacity to zero.
     *
     * @see clearLogs
     */
    val logs: List<Log>
        get() = synchronized(logsInternal) {
            logsInternal.toList()
        }

    /**
     * Whether logs are accepted.
     */
    var isEnabled = true

    /**
     * Capacity of [logs]. Old logs are removed flor [logs] list. Works like FIFO.
     */
    var capacity: Int
        get() = logsInternal.capacity
        set(value) {
            logsInternal.capacity = value
        }

    /**
     * Log level. Only messages with level >= [logLevel] will be sent to [consumers].
     * Default value is [Log.Level.VERBOSE], meaning that all messages will be sent.
     */
    var logLevel: Log.Level = Log.Level.VERBOSE

    /**
     * Send a debug log message.
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.DEBUG))
            log(Log.Level.DEBUG, tag, message, throwable)
    }

    /**
     * Send a debug log message.
     */
    fun d(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.DEBUG))
            log(Log.Level.DEBUG, tag, message(), throwable)
    }

    /**
     * Send a debug message only one time from the caller.
     */
    fun d1(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.DEBUG) && ensureOneCall())
            log(Log.Level.DEBUG, tag, message, throwable)
    }

    /**
     * Send a debug message only one time from the caller.
     */
    fun d1(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.DEBUG) && ensureOneCall())
            log(Log.Level.DEBUG, tag, message(), throwable)
    }

    /**
     * Send an error log message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.ERROR))
            log(Log.Level.ERROR, tag, message, throwable)
    }

    /**
     * Send an error log message.
     */
    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.ERROR))
            log(Log.Level.ERROR, tag, message(), throwable)
    }

    /**
     * Send an error message only one time from the caller.
     */
    fun e1(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.ERROR) && ensureOneCall())
            log(Log.Level.ERROR, tag, message, throwable)
    }

    /**
     * Send an error message only one time from the caller.
     */
    fun e1(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.ERROR) && ensureOneCall())
            log(Log.Level.ERROR, tag, message(), throwable)
    }

    /**
     * Send a info log message.
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.INFO))
            log(Log.Level.INFO, tag, message, throwable)
    }

    /**
     * Send a info log message.
     */
    fun i(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.INFO))
            log(Log.Level.INFO, tag, message(), throwable)
    }

    /**
     * Send a info message only one time from the caller.
     */
    fun i1(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.INFO) && ensureOneCall())
            log(Log.Level.INFO, tag, message, throwable)
    }

    /**
     * Send a info message only one time from the caller.
     */
    fun i1(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.INFO) && ensureOneCall())
            log(Log.Level.INFO, tag, message(), throwable)
    }

    /**
     * Send a verbose log message.
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.VERBOSE))
            log(Log.Level.VERBOSE, tag, message, throwable)
    }

    /**
     * Send a verbose log message.
     */
    fun v(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.VERBOSE))
            log(Log.Level.VERBOSE, tag, message(), throwable)
    }

    /**
     * Send a verbose message only one time from the caller.
     */
    fun v1(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.VERBOSE) && ensureOneCall())
            log(Log.Level.VERBOSE, tag, message, throwable)
    }

    /**
     * Send a verbose message only one time from the caller.
     */
    fun v1(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.VERBOSE) && ensureOneCall())
            log(Log.Level.VERBOSE, tag, message(), throwable)
    }

    /**
     * Send a warn log message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.WARN))
            log(Log.Level.WARN, tag, message, throwable)
    }

    /**
     * Send a warn log message.
     */
    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.WARN))
            log(Log.Level.WARN, tag, message(), throwable)
    }

    /**
     * Send a warn message only one time from the caller.
     */
    fun w1(tag: String, message: String, throwable: Throwable? = null) {
        if (isAllowed(Log.Level.WARN) && ensureOneCall())
            log(Log.Level.WARN, tag, message, throwable)
    }

    /**
     * Send a warn message only one time from the caller.
     */
    fun w1(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isAllowed(Log.Level.WARN) && ensureOneCall())
            log(Log.Level.WARN, tag, message(), throwable)
    }

    /**
     * Clear [logs] list.
     */
    fun clearLogs() {
        synchronized(logsInternal) {
            logsInternal.clear()
        }
    }

    @TestOnly
    @JvmSynthetic
    internal fun clearCallers() {
        synchronized(callerSet) {
            callerSet.clear()
        }
    }

    private fun isAllowed(level: Log.Level): Boolean {
        return isEnabled && logLevel >= level
    }

    /**
     * @return Whether the caller is call the function for the first time.
     */
    private fun ensureOneCall(): Boolean {
        val stackTrace = Thread.currentThread().stackTrace
        var caller: String? = null

        for (i in 3 until min(stackTrace.size, 10)) {
            val entry = stackTrace[i]

            if (entry.className != CLASS_NAME) {
                caller = entry.toString()
                break
            }
        }

        if (caller == null)
            return false

        synchronized(callerSet) {
            if (caller in callerSet)
                return false

            callerSet += caller
            return true
        }
    }

    private fun log(level: Log.Level, tag: String, message: String, throwable: Throwable? = null) {
        val log = Log(level, tag, message, throwable)

        synchronized(logsInternal) {
            logsInternal += log
        }

        for (i in consumers.indices)
            consumers[i].onNewLog(log)
    }

    interface Consumer {

        /**
         * When [Logger.isEnabled] is true and new log message was came into [Logger], this function will be called.
         */
        fun onNewLog(log: Log)
    }
}
