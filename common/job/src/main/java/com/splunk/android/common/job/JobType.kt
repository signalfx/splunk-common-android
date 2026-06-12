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

package com.splunk.android.common.job

import android.app.job.JobInfo
import android.content.Context

interface JobType {
    val jobNumberLimit: Long?

    fun createJobInfo(context: Context): JobInfo

    /**
     * @return True is [jobNumberLimit] is null or it is less then number of currently scheduled
     * jobs.
     */
    fun canSchedule(currentSize: Int): Boolean {
        val limit = jobNumberLimit
        return limit == null || currentSize <= limit
    }
}
