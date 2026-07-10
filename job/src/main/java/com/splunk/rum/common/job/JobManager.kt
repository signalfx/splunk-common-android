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

package com.splunk.rum.common.job

import android.annotation.SuppressLint
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import com.splunk.rum.common.logger.Logger

/**
 * Takes care of scheduling the rests to send them only if they meet certain conditions.
 */
@SuppressLint("NewApi")
class JobManager(private val context: Context) : IJobManager {

    private val jobScheduler: JobScheduler by lazy { context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler }

    override fun scheduleJob(jobType: JobType): JobResult {
        Logger.d(TAG, "scheduleJob()")
        val jobInfo = jobType.createJobInfo(context = context)
        try {
            if (jobType.canSchedule(jobScheduler.allPendingJobs.size)) {
                val result = jobScheduler.schedule(jobInfo)
                if (result == JobScheduler.RESULT_FAILURE) {
                    Logger.d(TAG, "scheduleJob(): job was not scheduled, failure")
                    return JobResult.Failure(reason = JobResult.Failure.FailureReason.SYSTEM_REJECTED)
                }
                return JobResult.Success
            } else {
                Logger.d(TAG, "scheduleJob(): job was not scheduled, limit was reached")
                return JobResult.Failure(reason = JobResult.Failure.FailureReason.LIMIT_REACHED)
            }
        } catch (exception: Exception) {
            Logger.d(TAG, "scheduleJob(): job was not scheduled, limit was reached")
            return JobResult.Failure(reason = JobResult.Failure.FailureReason.UNKNOWN)
        }
    }

    override fun isJobScheduled(id: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            jobScheduler.getPendingJob(id) != null
        } else {
            jobScheduler.allPendingJobs.any { jobInfo -> jobInfo.id == id }
        }
    }

    override fun cancelAll() {
        jobScheduler.cancelAll()
    }

    override fun cancel(id: Int) {
        // System is throwing unexpected exception on some specific devices -> JobSchedulerImpl line 74.
        try {
            jobScheduler.cancel(id)
        } catch (exception: Exception) {
        }
    }

    companion object {
        private const val TAG = "JobManager"

        private var instance: IJobManager? = null

        fun attach(context: Context): IJobManager {
            Logger.v(TAG, "attach(): JobManager attached.")
            return instance ?: JobManager(context).also { instance = it }
        }
    }
}
